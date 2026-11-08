package com.chorockkim.rokidyoutube.player

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Picks which YouTube streams to play. Same rules as RokidPipe/NewPipe's ListHelper, reduced
 * to what a 480x282 HUD needs: one video stream (muxed or video-only) and, for video-only, the
 * best original-language audio track.
 */
object StreamSelector {
    /** Itags ExoPlayer can play; copied from NewPipe's ListHelper.SUPPORTED_ITAG_IDS. */
    private val SUPPORTED_ITAG_IDS = setOf(
        17, 36, // video v3GPP
        18, 34, 35, 59, 78, 22, 37, 38, // video MPEG4
        43, 44, 45, 46, // video webm
        171, 172, 139, 140, 141, 249, 250, 251, // audio
        160, 133, 134, 135, 212, 136, 298, 137, 299, 266, // video only
        278, 242, 243, 244, 245, 246, 247, 248, 271, 272, 302, 303, 308, 313, 315
    )

    /** The HUD band is 282dp tall, so 480p is plenty and saves bandwidth on a hotspot. */
    const val DEFAULT_MAX_HEIGHT = 480

    data class Selection(
        val video: VideoStream?,
        val audio: AudioStream?,
        /** Distinct heights the user can switch between, highest first. */
        val availableHeights: List<Int>
    )

    fun <S : Stream> playable(streams: List<S>?, isYoutube: Boolean = true): List<S> =
        streams.orEmpty().filter { stream ->
            stream.deliveryMethod != DeliveryMethod.TORRENT &&
                (stream.deliveryMethod != DeliveryMethod.HLS || stream.format != MediaFormat.OPUS) &&
                (!isYoutube || stream.itagItem == null || stream.itagItem!!.id in SUPPORTED_ITAG_IDS)
        }

    /**
     * The [화질] button: one step up from the height playing now ([maxHeight] picks it), and from
     * the highest back to the lowest. [heights] is highest first, as in [Selection].
     */
    fun nextHeightUp(heights: List<Int>, maxHeight: Int): Int? {
        if (heights.size < 2) return null
        val current = heights.firstOrNull { it <= maxHeight } ?: heights.last()
        val index = heights.indexOf(current)
        return if (index == 0) heights.last() else heights[index - 1]
    }

    fun heightOf(stream: VideoStream): Int =
        stream.height.takeIf { it > 0 }
            ?: Regex("(\\d+)p").find(stream.getResolution())?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

    fun select(info: StreamInfo, maxHeight: Int = DEFAULT_MAX_HEIGHT): Selection {
        val isYoutube = info.serviceId == org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId
        return select(
            playable(info.videoStreams, isYoutube),
            playable(info.videoOnlyStreams, isYoutube),
            playable(info.audioStreams, isYoutube),
            maxHeight
        )
    }

    fun select(
        muxed: List<VideoStream>,
        videoOnly: List<VideoStream>,
        audio: List<AudioStream>,
        maxHeight: Int
    ): Selection {
        val bestAudio = pickAudio(audio)
        // Video-only streams need an audio stream to be merged with.
        val candidates = muxed + if (bestAudio != null) videoOnly else emptyList()
        val heights = candidates.map(::heightOf).filter { it > 0 }.distinct().sortedDescending()

        val targetHeight = heights.firstOrNull { it <= maxHeight } ?: heights.lastOrNull()
        val video = candidates
            .filter { targetHeight == null || heightOf(it) == targetHeight }
            .sortedWith(
                compareByDescending<VideoStream> { it.format == MediaFormat.MPEG_4 }
                    // Prefer muxed at equal height: one connection, no A/V merge.
                    .thenBy { it.isVideoOnly() }
                    .thenByDescending { it.fps }
            )
            .firstOrNull()

        return Selection(
            video = video,
            audio = if (video == null || video.isVideoOnly()) bestAudio else null,
            availableHeights = heights
        )
    }

    private fun pickAudio(audio: List<AudioStream>): AudioStream? {
        if (audio.isEmpty()) return null
        val original = audio.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { audio }
        return original.sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate }
        ).first()
    }
}
