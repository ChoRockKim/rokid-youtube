package com.chorockkim.rokidyoutube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamSelectorTest {
    private fun video(res: String, videoOnly: Boolean, format: MediaFormat = MediaFormat.MPEG_4) =
        VideoStream.Builder()
            .setId("$res-$videoOnly-${format.name}")
            .setContent("https://example.com/$res", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setResolution(res)
            .setIsVideoOnly(videoOnly)
            .build()

    private fun audio(bitrate: Int, format: MediaFormat, type: AudioTrackType? = null) =
        AudioStream.Builder()
            .setId("a$bitrate${format.name}$type")
            .setContent("https://example.com/a$bitrate", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(bitrate)
            .setAudioTrackType(type)
            .build()

    @Test
    fun picksHighestHeightNotAboveTheLimitAndMergesAudio() {
        val muxed = listOf(video("360p", false))
        val videoOnly = listOf(video("1080p", true), video("720p", true), video("480p", true))
        val m4a = audio(128, MediaFormat.M4A)
        val selection = StreamSelector.select(muxed, videoOnly, listOf(audio(160, MediaFormat.WEBMA), m4a), 480)

        assertEquals("480p", selection.video!!.getResolution())
        assertSame(m4a, selection.audio)
        assertEquals(listOf(1080, 720, 480, 360), selection.availableHeights)
    }

    @Test
    fun muxedStreamNeedsNoSeparateAudio() {
        val selection = StreamSelector.select(
            listOf(video("360p", false)), listOf(video("360p", true)), listOf(audio(128, MediaFormat.M4A)), 360
        )
        assertEquals(false, selection.video!!.isVideoOnly())
        assertNull(selection.audio)
    }

    @Test
    fun withoutAudioOnlyMuxedStreamsAreUsable() {
        val selection = StreamSelector.select(
            listOf(video("360p", false)), listOf(video("720p", true)), emptyList(), 720
        )
        assertEquals("360p", selection.video!!.getResolution())
        assertEquals(listOf(360), selection.availableHeights)
    }

    @Test
    fun fallsBackToLowestWhenEverythingIsAboveTheLimit() {
        val selection = StreamSelector.select(
            emptyList(), listOf(video("1080p", true), video("720p", true)), listOf(audio(128, MediaFormat.M4A)), 480
        )
        assertEquals("720p", selection.video!!.getResolution())
    }

    @Test
    fun prefersOriginalAudioTrackOverDubs() {
        val original = audio(96, MediaFormat.M4A, AudioTrackType.ORIGINAL)
        val selection = StreamSelector.select(
            emptyList(), listOf(video("480p", true)),
            listOf(audio(256, MediaFormat.M4A, AudioTrackType.DUBBED), original), 480
        )
        assertSame(original, selection.audio)
    }
}
