package com.chorockkim.rokidyoutube.update

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser

/** One GitHub release that carries an APK. */
data class Release(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lowercase hex SHA-256 of the APK, from the asset digest or the ".sha256" asset; may be null. */
    val sha256: String?,
    /** URL of the "<apk>.sha256" asset, used when [sha256] is null. */
    val sha256Url: String?
) {
    companion object {
        /** "v1.2.3" / "1.2.3" -> 10203; null if the tag is not X.Y.Z. Same formula as build.gradle.kts. */
        fun versionCodeOf(tag: String): Int? {
            val parts = tag.removePrefix("v").split(".")
            if (parts.size != 3) return null
            val (x, y, z) = parts.map { it.toIntOrNull() ?: return null }
            if (y > 99 || z > 99) return null
            return x * 10000 + y * 100 + z
        }

        /** Parses `GET /repos/{repo}/releases/latest`; null if it has no APK or no X.Y.Z tag. */
        fun parse(json: String): Release? {
            val o: JsonObject = JsonParser.`object`().from(json)
            if (o.getBoolean("draft", false) || o.getBoolean("prerelease", false)) return null
            val tag = o.getString("tag_name") ?: return null
            val code = versionCodeOf(tag) ?: return null
            val assets = o.getArray("assets").filterIsInstance<JsonObject>()
            val apk = assets.firstOrNull { it.getString("name", "").endsWith(".apk") } ?: return null
            val apkName = apk.getString("name")
            val digest = apk.getString("digest", null)
                ?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
            val shaAsset = assets.firstOrNull { it.getString("name", "") == "$apkName.sha256" }
            return Release(
                tag = tag,
                versionName = tag.removePrefix("v"),
                versionCode = code,
                notes = o.getString("body", "").trim(),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.getLong("size", -1),
                sha256 = digest,
                sha256Url = shaAsset?.getString("browser_download_url")
            )
        }

        /** First 64-hex word of a ".sha256" file ("<hash>  <name>"). */
        fun parseSha256File(text: String): String? =
            Regex("\\b[0-9a-fA-F]{64}\\b").find(text)?.value?.lowercase()
    }
}
