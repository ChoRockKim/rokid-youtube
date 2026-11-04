package com.chorockkim.rokidyoutube.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryTest {
    private var now = 1_000L
    private fun video(id: String, seconds: Long = 600) =
        VideoItem("https://youtu.be/$id", "title $id", "ch", seconds, 10, null, false)

    private fun newHistory(file: File? = null) = WatchHistory(file) { now }

    @Test
    fun recommendationsInterleaveNewestInterestsAndSkipWatched() {
        val history = newHistory()
        history.recordWatch(video("a"), listOf(video("a1"), video("a2"), video("b")))
        now += 10
        history.recordWatch(video("b"), listOf(video("b1"), video("b2"), video("a1")))

        // Newest entry (b) first, round-robin with a; a and b themselves were watched.
        assertEquals(
            listOf("b1", "a1", "b2", "a2"),
            history.recommendations().map { it.url.substringAfterLast('/') }
        )
    }

    @Test
    fun continueWatchingOnlyListsUnfinishedVideos() {
        val history = newHistory()
        history.recordWatch(video("started"), emptyList())
        history.updatePosition(video("started").url, 120_000, 600_000)
        history.recordWatch(video("finished"), emptyList())
        history.updatePosition(video("finished").url, 590_000, 600_000)
        history.recordWatch(video("barely"), emptyList())
        history.updatePosition(video("barely").url, 5_000, 600_000)

        assertEquals(listOf(video("started").url), history.continueWatching().map { it.url })
    }

    @Test
    fun rewatchingMovesToFrontAndKeepsPosition() {
        val history = newHistory()
        history.recordWatch(video("a"), listOf(video("x")))
        history.updatePosition(video("a").url, 90_000, 600_000)
        history.recordWatch(video("b"), emptyList())
        history.recordWatch(video("a"), emptyList())

        assertEquals(video("a").url, history.all.first().video.url)
        assertEquals(90_000, history.position(video("a").url))
        assertEquals(listOf(video("x")), history.all.first().related)
    }

    @Test
    fun survivesRestart() {
        val file = File(Files.createTempDirectory("history").toFile(), "h.json")
        newHistory(file).apply {
            recordWatch(video("a").copy(thumbnailUrl = "https://i.ytimg.com/a.jpg"), listOf(video("r")))
            updatePosition(video("a").url, 45_000, 600_000)
        }

        val reloaded = newHistory(file)
        assertEquals(1, reloaded.all.size)
        assertEquals("https://i.ytimg.com/a.jpg", reloaded.all[0].video.thumbnailUrl)
        assertEquals(45_000, reloaded.position(video("a").url))
        assertEquals(listOf(video("r")), reloaded.recommendations())
        assertTrue(reloaded.all[0].inProgress)
    }

    @Test
    fun keepsAtMostMaxEntries() {
        val history = newHistory()
        repeat(WatchHistory.MAX_ENTRIES + 5) { history.recordWatch(video("v$it"), emptyList()) }
        assertEquals(WatchHistory.MAX_ENTRIES, history.all.size)
    }
}
