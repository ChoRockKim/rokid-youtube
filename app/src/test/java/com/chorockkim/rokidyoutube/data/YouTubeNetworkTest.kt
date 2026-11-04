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
    fun popularLoads() = runBlocking {
        val items = YouTubeRepository.popular()
        println("popular: ${items.size} items, first = ${items.firstOrNull()?.title}")
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun relatedVideosFeedRecommendations() = runBlocking {
        val info = YouTubeRepository.stream("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val related = YouTubeRepository.related(info)
        println("related: ${related.size}, first = ${related.firstOrNull()?.title}")
        assertTrue(related.isNotEmpty())
    }

    @Test
    fun channelFeedHasNewestUploads() {
        // Same path as the subscription feed, without needing a signed-in account.
        val items = org.schabi.newpipe.extractor.feed.FeedInfo.getInfo(
            org.schabi.newpipe.extractor.ServiceList.YouTube,
            "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
        ).relatedItems
        val newest = YouTubeAccountApi.newestFirst(items)
        println("channel feed: ${items.size}, newest = ${newest.firstOrNull()?.name} ${newest.firstOrNull()?.uploadDate}")
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
