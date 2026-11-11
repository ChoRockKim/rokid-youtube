package com.chorockkim.rokidyoutube.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTest {
    private val sha = "ab".repeat(32)

    @Test
    fun parsesTheServerAnswer() {
        val r = Release.parse(
            """{"versionCode":200,"versionName":"0.2.0","size":7481342,"sha256":"${sha.uppercase()}","uploadedAt":"2026-11-08T12:00:00+00:00"}"""
        )!!
        assertEquals(Release("0.2.0", 200, 7_481_342, sha), r)
    }

    @Test
    fun rejectsIncompleteAnswers() {
        assertNull(Release.parse("""{"versionCode":200,"versionName":"0.2.0","sha256":"nope"}"""))
        assertNull(Release.parse("""{"versionName":"0.2.0","sha256":"$sha"}"""))
        assertNull(Release.parse("""{"versionCode":200,"sha256":"$sha"}"""))
        assertNull(Release.parse("not json"))
    }

    @Test
    fun companionsLiveNextToThisAppOnTheServer() {
        assertEquals(
            "https://server-v2-production-8f97.up.railway.app/apps/rokid-lens",
            Updater.companionUrl("https://server-v2-production-8f97.up.railway.app/apps/rokid-youtube", "rokid-lens")
        )
        assertEquals(listOf("com.chorockkim.rokidlens" to "rokid-lens"), Updater.COMPANIONS)
    }
}
