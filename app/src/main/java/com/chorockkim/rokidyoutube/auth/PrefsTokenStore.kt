package com.chorockkim.rokidyoutube.auth

import android.content.Context
import androidx.core.content.edit

/** Keeps the Google tokens in app-private preferences on the glasses. */
class PrefsTokenStore(context: Context) : GoogleAuth.TokenStore {
    private val prefs = context.getSharedPreferences("google_auth", Context.MODE_PRIVATE)

    override fun load(): GoogleAuth.Tokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return GoogleAuth.Tokens(access, refresh, prefs.getLong(KEY_EXPIRES, 0))
    }

    override fun save(tokens: GoogleAuth.Tokens?) = prefs.edit {
        if (tokens == null) {
            clear()
        } else {
            putString(KEY_ACCESS, tokens.accessToken)
            putString(KEY_REFRESH, tokens.refreshToken)
            putLong(KEY_EXPIRES, tokens.expiresAtMs)
        }
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
    }
}
