package com.chorockkim.rokidyoutube.data

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The signed-in parts of the home screen, through the official YouTube Data API v3
 * (read-only scope). Same idea as Herald's Watchlight: subscriptions and liked videos.
 * The Data API has no "recommended for you" endpoint; that part is built locally from history.
 */
class YouTubeAccountApi(
    private val http: OkHttpClient,
    private val accessToken: suspend () -> String?
) {
    data class Channel(val id: String, val title: String)

    class NotSignedInException : IOException("Not signed in")
    class QuotaException : IOException("YouTube API quota exceeded")

    suspend fun likedVideos(): List<VideoItem> {
        val json = get(
            "videos",
            "part" to "snippet,contentDetails,statistics",
            "myRating" to "like",
            "maxResults" to "50"
        )
        return json.getArray("items").objects().map(::videoFromResource)
    }

    suspend fun subscriptions(limit: Int = 200): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pageToken: String? = null
        do {
            val json = get(
                "subscriptions",
                "part" to "snippet",
                "mine" to "true",
                "order" to "relevance",
                "maxResults" to "50",
                "pageToken" to pageToken
            )
            json.getArray("items").objects().mapTo(channels) { item ->
                val snippet = item.getObject("snippet")
                Channel(snippet.getObject("resourceId").getString("channelId"), snippet.getString("title"))
            }
            pageToken = json.getString("nextPageToken", null)
        } while (pageToken != null && channels.size < limit)
        return channels
    }

    /**
     * Newest uploads of the user's subscriptions. Uploads come from each channel's public feed
     * (via NewPipeExtractor, no API quota), only the subscription list uses the API.
     */
    suspend fun subscriptionFeed(maxChannels: Int = 40, maxItems: Int = 60): List<VideoItem> {
        val channels = subscriptions().take(maxChannels)
        val gate = Semaphore(6)
        val uploads = coroutineScope {
            channels.map { channel ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        runCatching {
                            FeedInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/channel/${channel.id}")
                                .relatedItems
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }
        return newestFirst(uploads).take(maxItems).map(YouTubeRepository::toVideoItem)
    }

    private suspend fun get(path: String, vararg params: Pair<String, String?>): JsonObject {
        val token = accessToken() ?: throw NotSignedInException()
        val url = "$API/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        return withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            ).execute().use { response ->
                val text = response.body.string()
                if (response.code == 401) throw NotSignedInException()
                if (response.code == 403 && text.contains("quotaExceeded")) throw QuotaException()
                if (!response.isSuccessful) throw IOException("YouTube API HTTP ${response.code}")
                JsonParser.`object`().from(text)
            }
        }
    }

    companion object {
        const val API = "https://www.googleapis.com/youtube/v3"

        fun newestFirst(items: List<StreamInfoItem>): List<StreamInfoItem> =
            items.distinctBy { it.url }
                .sortedByDescending { it.uploadDate?.instant?.toEpochMilli() ?: 0L }

        fun videoFromResource(item: JsonObject): VideoItem {
            val snippet = item.getObject("snippet")
            val thumbs = snippet.getObject("thumbnails")
            val thumb = listOf("medium", "high", "standard", "default")
                .firstNotNullOfOrNull { thumbs.getObject(it)?.getString("url", null) }
            val live = snippet.getString("liveBroadcastContent", "none") == "live"
            return VideoItem(
                url = "https://www.youtube.com/watch?v=${item.getString("id")}",
                title = snippet.getString("title", ""),
                uploader = snippet.getString("channelTitle", ""),
                durationSeconds = parseIsoDuration(item.getObject("contentDetails")?.getString("duration", null)),
                viewCount = item.getObject("statistics")?.getString("viewCount", null)?.toLongOrNull() ?: -1,
                thumbnailUrl = thumb,
                isLive = live
            )
        }

        /** "PT1H2M3S" -> 3723; unknown or live ("P0D") -> 0. */
        fun parseIsoDuration(value: String?): Long =
            value?.let { runCatching { Duration.parse(it).seconds }.getOrNull() } ?: 0L

        private fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()
    }
}
