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

/** Thin coroutine wrapper over NewPipeExtractor's YouTube service. */
object YouTubeRepository {
    private val service get() = ServiceList.YouTube

    /** Video kiosks mixed into "popular" when there is no watch history yet. */
    private val POPULAR_KIOSKS = listOf("trending_music", "trending_gaming", "trending_movies_and_shows")

    /** Popular videos across YouTube's trending sections, interleaved. */
    suspend fun popular(): List<VideoItem> = withContext(Dispatchers.IO) {
        val available = runCatching { service.kioskList.availableKiosks }.getOrDefault(emptySet())
        val lists = POPULAR_KIOSKS.filter { it in available }.map { id ->
            runCatching {
                val url = service.kioskList.getListLinkHandlerFactoryByType(id).fromId(id).url
                KioskInfo.getInfo(service, url).relatedItems.toVideoItems()
            }.getOrDefault(emptyList())
        }
        interleave(lists).distinctBy { it.url }
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        SearchInfo.getInfo(service, handler).relatedItems.toVideoItems()
    }

    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(service, url)
    }

    fun related(info: StreamInfo): List<VideoItem> = info.relatedItems.toVideoItems()

    fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(out::add) }
        return out
    }

    private fun List<InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().map(::toVideoItem)

    fun toVideoItem(item: StreamInfoItem) = VideoItem(
        url = item.url,
        title = item.name,
        uploader = item.uploaderName.orEmpty(),
        durationSeconds = item.duration,
        viewCount = item.viewCount,
        thumbnailUrl = pickThumbnail(item.thumbnails),
        isLive = item.streamType == StreamType.LIVE_STREAM ||
            item.streamType == StreamType.AUDIO_LIVE_STREAM
    )

    /** Smallest thumbnail that is still at least as wide as a grid card. */
    private fun pickThumbnail(images: List<Image>): String? {
        val sized = images.filter { it.width > 0 }.sortedBy { it.width }
        return (sized.firstOrNull { it.width >= 240 } ?: sized.lastOrNull() ?: images.firstOrNull())?.url
    }
}
