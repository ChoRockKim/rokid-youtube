package com.chorockkim.rokidyoutube.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationsTest {
    private fun video(id: String) = VideoItem("https://youtu.be/$id", "title $id", "ch", 600, 10, null, false)
    private fun ids(list: List<VideoItem>) = list.map { it.url.substringAfterLast('/') }

    private var now = 1_000L
    private var likedCalls = 0

    private fun account(signedIn: Boolean = true) = object : Recommendations.Account {
        override val isSignedIn = signedIn
        override suspend fun likedVideos(): List<VideoItem> {
            likedCalls++
            return listOf(video("L1"), video("L2"))
        }
        override suspend fun subscriptionUploads() = listOf(video("s1"), video("s2"), video("h1"))
    }

    private val related = mapOf(
        "L1" to listOf(video("l1a"), video("l1b")),
        "L2" to listOf(video("l2a"), video("L1")) // a liked video itself is excluded
    )

    private fun recommendations(history: WatchHistory, account: Recommendations.Account?) =
        Recommendations(history, account, { now }) { url -> related[url.substringAfterLast('/')].orEmpty() }

    @Test
    fun mixesHistoryLikedAndSubscriptionsRoundRobin() = runBlocking {
        val history = WatchHistory(null) { now }
        history.recordWatch(video("w"), listOf(video("h1"), video("h2")))

        val feed = recommendations(history, account()).feed()

        // history, liked-related, subscriptions, repeated; duplicates (h1) and liked videos dropped.
        assertEquals(listOf("h1", "l1a", "s1", "h2", "l2a", "s2", "l1b"), ids(feed))
    }

    @Test
    fun signedOutUsesHistoryOnly() = runBlocking {
        val history = WatchHistory(null) { now }
        history.recordWatch(video("w"), listOf(video("h1"), video("h2")))

        assertEquals(listOf("h1", "h2"), ids(recommendations(history, account(signedIn = false)).feed()))
        assertEquals(0, likedCalls)
    }

    @Test
    fun accountSeedsAreCachedUntilExpiryOrInvalidate() = runBlocking {
        val recs = recommendations(WatchHistory(null) { now }, account())
        recs.feed()
        recs.feed()
        assertEquals(1, likedCalls)
        now += Recommendations.CACHE_MS
        recs.feed()
        assertEquals(2, likedCalls)
        recs.invalidate()
        recs.feed()
        assertEquals(3, likedCalls)
    }
}
