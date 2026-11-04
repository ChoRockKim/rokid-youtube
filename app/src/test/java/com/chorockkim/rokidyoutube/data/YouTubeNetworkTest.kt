package com.chorockkim.rokidyoutube.data

import com.chorockkim.rokidyoutube.net.OkHttpDownloader
import com.chorockkim.rokidyoutube.player.StreamSelector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Talks to the real YouTube through NewPipeExtractor. Skipped unless run with
 * `./gradlew :app:testDebugUnitTest -PnetworkTests=true`. No poToken here (that needs a WebView),
 * so it checks search/kiosk/stream extraction, not that the stream URLs actually play.
 */
class YouTubeNetworkTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            assumeTrue(System.getProperty("networkTests") == "true")
            NewPipe.init(OkHttpDownloader.get(), Localization("ko", "KR"), ContentCountry("KR"))
        }
    }

    @Test
    fun searchReturnsVideos() = runBlocking {
        val items = YouTubeRepository.search("rick astley never gonna give you up")
        println("search: ${items.size} items, first = ${items.firstOrNull()}")
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun categoriesLoad() = runBlocking {
        val categories = YouTubeRepository.categories()
        println("categories: $categories")
        val first = categories.first { it.kioskId != "live" }
        val items = YouTubeRepository.kiosk(first.kioskId)
        println("${first.kioskId}: ${items.size} items, first = ${items.firstOrNull()?.title}")
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun streamHasPlayableSelection() = runBlocking {
        val info = YouTubeRepository.stream("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val selection = StreamSelector.select(info)
        println(
            "stream '${info.name}': video=${selection.video?.getResolution()} " +
                "${selection.video?.deliveryMethod} audio=${selection.audio?.format} " +
                "heights=${selection.availableHeights}"
        )
        assertTrue(selection.video != null)
    }
}
