package com.chorockkim.rokidyoutube.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Result of an install session started by [Updater.install]. When the system needs the user to
 * confirm, it hands back its confirmation screen, which we open; everything else is passed to
 * [listener] (the update screen, if it is open).
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                listener?.invoke(Result.WaitingForUser)
            }
            PackageInstaller.STATUS_SUCCESS -> listener?.invoke(Result.Installed)
            else -> listener?.invoke(
                Result.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status")
            )
        }
    }

    sealed class Result {
        object WaitingForUser : Result()
        object Installed : Result()
        data class Failed(val message: String) : Result()
    }

    companion object {
        @Volatile
        var listener: ((Result) -> Unit)? = null
    }
}
