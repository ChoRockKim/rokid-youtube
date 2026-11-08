package com.chorockkim.rokidyoutube.update

import com.grack.nanojson.JsonParser

/**
 * The latest APK on the update server (`GET <UPDATE_URL>/latest`):
 * `{"versionCode": 200, "versionName": "0.2.0", "size": 7481342, "sha256": "…", "uploadedAt": "…"}`.
 */
data class Release(
    val versionName: String,
    val versionCode: Int,
    val size: Long,
    /** Lowercase hex SHA-256 of the APK. */
    val sha256: String
) {
    companion object {
        /** Null if a field is missing or odd. */
        fun parse(json: String): Release? = runCatching {
            val o = JsonParser.`object`().from(json)
            val sha = o.getString("sha256", "").lowercase()
            if (!Regex("[0-9a-f]{64}").matches(sha)) return null
            val code = o.getInt("versionCode", 0)
            val name = o.getString("versionName", "")
            if (code <= 0 || name.isEmpty()) return null
            Release(name, code, o.getLong("size", -1), sha)
        }.getOrNull()
    }
}
