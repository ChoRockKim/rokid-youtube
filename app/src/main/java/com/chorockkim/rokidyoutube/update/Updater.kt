package com.chorockkim.rokidyoutube.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.chorockkim.rokidyoutube.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Over-the-air updates from this app's folder on the rokid-conversate server
 * ([BuildConfig.UPDATE_URL]: `/latest` = version JSON, `/download` = the APK, uploaded by
 * scripts/release.sh): check, download, verify, hand it to the system installer.
 *
 * Installed once with `adb install -i <package>` (the app is its own installer), the update
 * needs no confirmation on the glasses (USER_ACTION_NOT_REQUIRED, same as rokid-conversate) and
 * [UpdateReceiver] starts the app again afterwards.
 *
 * Verification before installing: the file's SHA-256 must match the release, and the APK must
 * be this app (same package), newer, and signed with the same key as the installed app. The
 * system would refuse a different key anyway; checking first gives a clear message instead.
 */
class Updater(private val context: Context, private val http: () -> OkHttpClient) {
    sealed class Check {
        data class Available(val release: Release) : Check()
        object UpToDate : Check()
    }

    class UpdateException(message: String) : IOException(message)

    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val dir get() = File(context.cacheDir, "updates")

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Latest release found by the last check, if newer than this app. */
    var available: Release? = null
        private set

    /** Checks at most every [CHECK_EVERY_MS] unless [force] (the app forces it at every start). */
    suspend fun check(force: Boolean = false): Check {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_EVERY_MS) {
            return available?.let { Check.Available(it) } ?: Check.UpToDate
        }
        val json = get("${BuildConfig.UPDATE_URL}/latest") { code ->
            // 404 = nothing uploaded yet.
            if (code == 404) return Check.UpToDate.also { available = null }
        }
        prefs.edit().apply {
            putLong(KEY_LAST_CHECK, now)
            apply()
        }
        val release = Release.parse(json)
        available = release?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        return available?.let { Check.Available(it) } ?: Check.UpToDate
    }

    /** Downloads and verifies the APK; [onProgress] gets 0..100 (or -1 if the size is unknown). */
    suspend fun download(release: Release, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "rokid-youtube-${release.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        http().newCall(Request.Builder().url("${BuildConfig.UPDATE_URL}/download").build()).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("받기 실패 (HTTP ${response.code})")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            var done = 0L
            var lastPercent = -2
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
            }
        }
        val expected = release.sha256
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            throw UpdateException("파일이 손상됐어요 (SHA-256 불일치)")
        }
        verifyApk(file, release)
        file
    }

    private fun verifyApk(file: File, release: Release) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, file.path) ?: throw UpdateException("APK를 읽지 못했어요")
        if (archive.packageName != context.packageName) throw UpdateException("다른 앱의 APK예요")
        @Suppress("DEPRECATION")
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archiveCode <= BuildConfig.VERSION_CODE) throw UpdateException("지금 버전보다 새 버전이 아니에요")
        if (archiveCode != release.versionCode.toLong()) throw UpdateException("릴리스와 APK 버전이 달라요")
        val installed = signatures(pm.getPackageInfo(context.packageName, signingFlags()))
        val incoming = signatures(archive)
        if (installed.isEmpty() || installed != incoming) {
            throw UpdateException("서명 키가 달라서 덮어 설치할 수 없어요")
        }
    }

    /** Whether the system lets this app install APKs (the "unknown sources" toggle for this app). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows installs from this app. */
    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Starts a system install session. The system shows its own confirmation; the result arrives
     * in [InstallResultReceiver]. On success Android replaces and restarts nothing: the app closes.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    private inline fun get(url: String, onError: (Int) -> Unit): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "rokid-youtube-updater")
            .build()
        http().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                onError(response.code)
                throw UpdateException("업데이트 확인 실패 (HTTP ${response.code})")
            }
            return response.body.string()
        }
    }

    companion object {
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "lastCheck"

        @Suppress("DEPRECATION")
        private fun signingFlags() =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

        private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
            } else {
                @Suppress("DEPRECATION") pm.getPackageArchiveInfo(path, signingFlags())
            }

        @Suppress("DEPRECATION")
        private fun signatures(info: PackageInfo): Set<String> {
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
            } else {
                info.signatures
            }
            return sigs.orEmpty().map { sig ->
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
    }
}
