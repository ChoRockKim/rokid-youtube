package com.chorockkim.rokidyoutube.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTest {
    private val sha = "ab".repeat(32)

    private fun json(tag: String = "v0.2.1", digest: String? = "sha256:${sha.uppercase()}", prerelease: Boolean = false) = """
        {"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"- 첫 줄\n- 둘째 줄",
         "assets":[
           {"name":"rokid-youtube-0.2.1.apk.sha256","browser_download_url":"https://x/a.apk.sha256","size":90},
           {"name":"rokid-youtube-0.2.1.apk","browser_download_url":"https://x/a.apk","size":9800000
            ${if (digest != null) ""","digest":"$digest"""" else ""}}
         ]}
    """.trimIndent()

    @Test
    fun versionCodeMatchesTheGradleFormula() {
        assertEquals(201, Release.versionCodeOf("v0.2.1"))
        assertEquals(10203, Release.versionCodeOf("1.2.3"))
        assertNull(Release.versionCodeOf("v1.2"))
        assertNull(Release.versionCodeOf("v1.2.x"))
        assertNull(Release.versionCodeOf("v0.100.0"))
    }

    @Test
    fun parsesApkDigestAndShaAsset() {
        val r = Release.parse(json())!!
        assertEquals("0.2.1", r.versionName)
        assertEquals(201, r.versionCode)
        assertEquals("https://x/a.apk", r.apkUrl)
        assertEquals(9_800_000L, r.apkSize)
        assertEquals(sha, r.sha256)
        assertEquals("https://x/a.apk.sha256", r.sha256Url)
    }

    @Test
    fun withoutDigestFallsBackToTheShaFile() {
        val r = Release.parse(json(digest = null))!!
        assertNull(r.sha256)
        assertEquals(sha, Release.parseSha256File("${sha.uppercase()}  rokid-youtube-0.2.1.apk\n"))
    }

    @Test
    fun ignoresPrereleasesAndOddTags() {
        assertNull(Release.parse(json(prerelease = true)))
        assertNull(Release.parse(json(tag = "nightly")))
    }
}
