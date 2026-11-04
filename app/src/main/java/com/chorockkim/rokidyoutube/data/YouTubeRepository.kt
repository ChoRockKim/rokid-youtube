package com.chorockkim.rokidyoutube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val thumbnailUrl: String?,
    val isLive: Boolean
)

data class Category(val kioskId: String, val label: String)

/** Thin coroutine wrapper over NewPipeExtractor's YouTube service. */
object YouTubeRepository {
    private val service get() = ServiceList.YouTube

    /** Kiosks shown as top-bar chips, in RokidPipe's order; "Trending" itself is broken upstream. */
    private val CATEGORY_LABELS = linkedMapOf(
        "trending_gaming" to "게임",
        "trending_music" to "음악",
        "trending_movies_and_shows" to "영화",
        "trending_podcasts_episodes" to "팟캐스트",
        "live" to "라이브"
    )

    fun categories(): List<Category> {
        val available = runCatching { service.kioskList.availableKiosks }.getOrDefault(emptySet())
        return CATEGORY_LABELS.filterKeys { it in available }.map { (id, label) -> Category(id, label) }
    }

    suspend fun kiosk(kioskId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val url = service.kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).url
        KioskInfo.getInfo(service, url).relatedItems.toVideoItems()
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        SearchInfo.getInfo(service, handler).relatedItems.toVideoItems()
    }

    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(service, url)
    }

    private fun List<InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().map { item ->
            VideoItem(
                url = item.url,
                title = item.name,
                uploader = item.uploaderName.orEmpty(),
                durationSeconds = item.duration,
                viewCount = item.viewCount,
                thumbnailUrl = pickThumbnail(item.thumbnails),
                isLive = item.streamType == StreamType.LIVE_STREAM ||
                    item.streamType == StreamType.AUDIO_LIVE_STREAM
            )
        }

    /** Smallest thumbnail that is still at least as wide as the list cell (124dp). */
    private fun pickThumbnail(images: List<Image>): String? {
        val sized = images.filter { it.width > 0 }.sortedBy { it.width }
        return (sized.firstOrNull { it.width >= 240 } ?: sized.lastOrNull() ?: images.firstOrNull())?.url
    }
}
