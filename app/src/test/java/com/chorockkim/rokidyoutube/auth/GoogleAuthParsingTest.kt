package com.chorockkim.rokidyoutube.auth

import com.chorockkim.rokidyoutube.data.YouTubeAccountApi
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAuthParsingTest {
    @Test
    fun deviceCodeResponse() {
        // Shape from https://developers.google.com/identity/protocols/oauth2/limited-input-device
        val json = JsonParser.`object`().from(
            """{"device_code":"4/4-GMMhmHCXhWEzkobqIHGG_EnNYYsAkukHspeYUk9E8",
               "user_code":"GQVQ-JKEC","verification_url":"https://www.google.com/device",
               "expires_in":1800,"interval":5}"""
        )
        val code = GoogleAuth.parseDeviceCode(json, nowMs = 1_000)
        assertEquals("GQVQ-JKEC", code.userCode)
        assertEquals("https://www.google.com/device", code.verificationUrl)
        assertEquals(1_000 + 1_800_000L, code.expiresAtMs)
        assertEquals(5, code.intervalSeconds)
    }

    @Test
    fun refreshResponseKeepsExistingRefreshToken() {
        val json = JsonParser.`object`().from("""{"access_token":"new","expires_in":3599,"token_type":"Bearer"}""")
        val tokens = GoogleAuth.parseTokens(json, nowMs = 0, previousRefresh = "refresh-1")
        assertEquals("new", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(3_599_000L, tokens.expiresAtMs)
    }

    @Test
    fun likedVideoResource() {
        val json = JsonParser.`object`().from(
            """{"id":"dQw4w9WgXcQ",
               "snippet":{"title":"Never Gonna Give You Up","channelTitle":"Rick Astley",
                 "liveBroadcastContent":"none",
                 "thumbnails":{"default":{"url":"d.jpg"},"medium":{"url":"m.jpg"}}},
               "contentDetails":{"duration":"PT3M33S"},
               "statistics":{"viewCount":"1700000000"}}"""
        )
        val video = YouTubeAccountApi.videoFromResource(json)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", video.url)
        assertEquals("Rick Astley", video.uploader)
        assertEquals(213, video.durationSeconds)
        assertEquals(1_700_000_000, video.viewCount)
        assertEquals("m.jpg", video.thumbnailUrl)
    }

    @Test
    fun isoDurations() {
        assertEquals(3723, YouTubeAccountApi.parseIsoDuration("PT1H2M3S"))
        assertEquals(0, YouTubeAccountApi.parseIsoDuration("P0D"))
        assertEquals(0, YouTubeAccountApi.parseIsoDuration(null))
    }

    @Test
    fun missingStatisticsIsUnknownViews() {
        val json = JsonParser.`object`().from("""{"id":"x","snippet":{"title":"t","thumbnails":{}}}""")
        val video = YouTubeAccountApi.videoFromResource(json)
        assertEquals(-1, video.viewCount)
        assertNull(video.thumbnailUrl)
    }
}
