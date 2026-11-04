package com.chorockkim.rokidyoutube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * The "추천" feed. YouTube's own "recommended for you" is not available through the official
 * API, so it is built from what we can read:
 *
 * - related videos of what was watched on these glasses (local history),
 * - when signed in: related videos of the user's liked videos, and new uploads of their
 *   subscriptions.
 *
 * The three sources are interleaved so the feed starts with a bit of each, minus anything
 * already watched or liked. Account seeds are slow to fetch (one page fetch per liked video, one
 * feed per channel), so they are cached for [CACHE_MS]; the local part is always fresh.
 */
class Recommendations(
    private val history: WatchHistory,
    private val account: Account?,
    private val now: () -> Long = System::currentTimeMillis,
    private val relatedOf: suspend (String) -> List<VideoItem> = { url ->
        YouTubeRepository.related(YouTubeRepository.stream(url))
    }
) {
    /** What the feed needs from the signed-in account; null / not signed in = history only. */
    interface Account {
        val isSignedIn: Boolean
        suspend fun likedVideos(): List<VideoItem>
        suspend fun subscriptionUploads(): List<VideoItem>
    }

    private class Seeds(val liked: List<VideoItem>, val likedRelated: List<List<VideoItem>>, val uploads: List<VideoItem>)

    private val lock = Mutex()
    private var cached: Seeds? = null
    private var cachedAt = 0L

    /** Drop the cached account seeds, e.g. after signing in or out. */
    fun invalidate() {
        cached = null
    }

    suspend fun feed(limit: Int = 60): List<VideoItem> {
        val seeds = accountSeeds()
        return mix(
            historyRelated = history.all.take(WatchHistory.SEED_ENTRIES).map { it.related },
            likedRelated = seeds?.likedRelated.orEmpty(),
            subscriptionUploads = seeds?.uploads.orEmpty(),
            exclude = history.all.map { it.video.url }.toSet() + seeds?.liked.orEmpty().map { it.url },
            limit = limit
        )
    }

    private suspend fun accountSeeds(): Seeds? {
        val acc = account?.takeIf { it.isSignedIn } ?: return null
        return lock.withLock {
            cached?.takeIf { now() - cachedAt < CACHE_MS }?.let { return@withLock it }
            coroutineScope {
                val liked = async { runCatching { acc.likedVideos() }.getOrDefault(emptyList()) }
                val uploads = async { runCatching { acc.subscriptionUploads() }.getOrDefault(emptyList()) }
                val gate = Semaphore(4)
                val likedList = liked.await()
                val likedRelated = likedList.take(LIKED_SEEDS).map { video ->
                    async(Dispatchers.IO) {
                        gate.withPermit { runCatching { relatedOf(video.url).take(WatchHistory.MAX_RELATED) }.getOrDefault(emptyList()) }
                    }
                }.awaitAll()
                Seeds(likedList, likedRelated, uploads.await())
            }.also {
                // Don't keep an all-empty result (e.g. offline): try again next time.
                if (it.liked.isNotEmpty() || it.uploads.isNotEmpty()) {
                    cached = it
                    cachedAt = now()
                }
            }
        }
    }

    companion object {
        const val CACHE_MS = 30 * 60 * 1000L
        /** How many of the most recent liked videos seed the feed. */
        const val LIKED_SEEDS = 8

        /**
         * Round-robin over the three sources: (history, liked, subscription) repeated, each source
         * itself round-robin over its seed videos so no single video dominates.
         */
        fun mix(
            historyRelated: List<List<VideoItem>>,
            likedRelated: List<List<VideoItem>>,
            subscriptionUploads: List<VideoItem>,
            exclude: Set<String>,
            limit: Int
        ): List<VideoItem> {
            fun clean(list: List<VideoItem>) = list.filter { it.url !in exclude }.distinctBy { it.url }
            val sources = listOf(
                clean(YouTubeRepository.interleave(historyRelated)),
                clean(YouTubeRepository.interleave(likedRelated)),
                clean(subscriptionUploads)
            )
            return YouTubeRepository.interleave(sources).distinctBy { it.url }.take(limit)
        }
    }
}
