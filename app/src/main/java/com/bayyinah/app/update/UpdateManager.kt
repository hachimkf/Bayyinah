package com.bayyinah.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bayyinah.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** Hex SHA-256 from GitHub's asset digest, when available. */
    val sha256: String?,
)

/**
 * In-app updates from GitHub Releases (the app is sideloaded, so there is no Play Store).
 *
 * - Checks `releases/latest` of [BuildConfig.UPDATE_REPO] at most every [CHECK_INTERVAL_MS]
 *   (or on demand). Any network failure is silent: the app stays fully usable offline.
 * - Picks the APK matching this phone's CPU (`…-arm64-v8a-release.apk` / `…-armeabi-v7a-…`).
 * - Verifies the download's SHA-256 against GitHub's digest, then opens the system installer.
 *   Android itself refuses the update unless it is signed with the same key as the installed app.
 */
class UpdateManager(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir = File(app.cacheDir, "updates")

    suspend fun check(force: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return@withContext null
        val json = try { httpGetJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") }
        catch (e: Exception) { android.util.Log.i(TAG, "Update check skipped: ${e.message}"); return@withContext null }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        val version = json.optString("tag_name").removePrefix("v").removePrefix("V")
        if (version.isEmpty() || !isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk") }
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }
        val asset = apks.firstOrNull { abi != null && it.optString("name").contains(abi) }
            ?: apks.firstOrNull { it.optString("name").contains("universal") }
            ?: return@withContext null
        UpdateInfo(
            version = version,
            notes = json.optString("body"),
            apkUrl = asset.getString("browser_download_url"),
            sizeBytes = asset.optLong("size"),
            sha256 = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        )
    }

    /** Downloads the APK into app cache, reporting progress 0..1; returns the verified file. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // keep only one update around
        val out = File(dir, "bayyinah-${info.version}.apk")
        val conn = open(info.apkUrl)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); digest.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { conn.disconnect() }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && !hex.equals(info.sha256, ignoreCase = true)) {
            out.delete(); error("Checksum mismatch")
        }
        out
    }

    /** True when Android lets this app open the installer (Android 8+ asks the user once). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            )
        }
    }

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${app.packageName}.updates", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Bayyinah-Android/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    private fun httpGetJson(url: String): JSONObject {
        val conn = open(url)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    companion object {
        private const val TAG = "BayyinahUpdate"
        private const val KEY_LAST_CHECK = "last_check"
        const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

        /** Semantic-version compare: "1.10.0" > "1.9.2". Non-numeric suffixes are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.split('.', '-').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
