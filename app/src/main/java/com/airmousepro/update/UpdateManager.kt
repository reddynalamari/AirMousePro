package com.airmousepro.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.airmousepro.AirMouseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

const val UPDATE_OWNER = "reddynalamari"
const val UPDATE_REPO = "AirMousePro"
const val RELEASE_WORKFLOW_URL = "https://github.com/$UPDATE_OWNER/$UPDATE_REPO/actions/workflows/release.yml"

/** One published GitHub release that carries an APK. */
data class ReleaseInfo(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case SHA-256 of the APK when GitHub or SHA256SUMS.txt provides one. */
    val sha256: String?,
    val sumsUrl: String?,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: ReleaseInfo) : UpdateStatus
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateStatus
    data class Installing(val release: ReleaseInfo) : UpdateStatus
    data class NeedsPermission(val release: ReleaseInfo) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Checks GitHub Releases for a newer signed APK, downloads it, verifies its checksum and hands it to Android's
 * installer. Android itself refuses any APK that is not signed with the same key as the installed app.
 */
class UpdateManager(private val app: AirMouseApp) {
    private val ctx: Context = app.applicationContext
    private val settings get() = app.settings

    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set

    /** Newest first. Filled by any successful check. */
    var releases: List<ReleaseInfo> by mutableStateOf(emptyList())
        private set

    /** When set, the launch prompt is showing for this release. */
    var prompt: ReleaseInfo? by mutableStateOf(null)

    val installedName: String
        get() = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    val installedCode: Long
        get() = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode }.getOrNull() ?: 0L

    fun isNewer(tag: String) = compareVersions(tag, installedName) > 0
    fun isSame(tag: String) = compareVersions(tag, installedName) == 0

    /** Called when the app opens: at most one automatic check every six hours. */
    fun checkOnLaunch() {
        if (!settings.updateAuto) return
        val last = settings.updateLastCheck.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < SIX_HOURS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (status is UpdateStatus.Checking || status is UpdateStatus.Downloading || status is UpdateStatus.Installing) return
        status = UpdateStatus.Checking
        app.appScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { fetchReleases() }
                releases = list
                settings.updateLastCheck = System.currentTimeMillis().toString()
                val latest = list.firstOrNull()
                if (latest != null && isNewer(latest.tag)) {
                    status = UpdateStatus.Available(latest)
                    // An automatic check stays quiet about a version the user already skipped.
                    if (manual || settings.updateSkip != latest.tag) prompt = latest
                } else {
                    status = UpdateStatus.UpToDate
                }
            } catch (e: Exception) {
                // Automatic checks fail quietly; the Settings page shows the reason.
                status = UpdateStatus.Error(describe(e))
            }
        }
    }

    fun skip(release: ReleaseInfo) {
        settings.updateSkip = release.tag
        prompt = null
    }

    /** Downloads, verifies and installs [release]. Needs the "install unknown apps" permission once. */
    fun install(release: ReleaseInfo) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            status = UpdateStatus.NeedsPermission(release)
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        status = UpdateStatus.Downloading(release, 0f)
        app.appScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { download(release) }
                status = UpdateStatus.Installing(release)
                withContext(Dispatchers.IO) { commit(file) }
            } catch (e: Exception) {
                status = UpdateStatus.Error(describe(e))
            }
        }
    }

    /** Saves a release APK to the Downloads folder, for a manual reinstall. */
    fun downloadToDownloads(release: ReleaseInfo) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle(release.apkName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, release.apkName)
        dm.enqueue(request)
    }

    fun uninstallSelf() {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---- Results from the system installer ----

    fun onInstallFailed(code: Int, message: String?) {
        val text = when {
            message?.contains("signature", ignoreCase = true) == true || message?.contains("INCOMPATIBLE") == true ->
                "This update is signed with a different key than the installed app. Back up your settings, uninstall once, then install it."
            message?.contains("DOWNGRADE") == true ->
                "Android does not allow installing an older version over a newer one."
            code == PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was cancelled."
            else -> "Installation failed${message?.let { ": $it" } ?: ""}."
        }
        status = UpdateStatus.Error(text)
    }

    // ---- Network ----

    private fun open(url: String, accept: String? = null): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "AirMousePro/$installedName")
        if (accept != null) c.setRequestProperty("Accept", accept)
        return c
    }

    private fun fetchReleases(): List<ReleaseInfo> {
        val c = open("https://api.github.com/repos/$UPDATE_OWNER/$UPDATE_REPO/releases?per_page=30", "application/vnd.github+json")
        try {
            when (c.responseCode) {
                200 -> {}
                403, 429 -> throw IOException("GitHub is limiting requests right now. Try again in a while.")
                else -> throw IOException("GitHub answered ${c.responseCode}.")
            }
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            val out = mutableListOf<ReleaseInfo>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
                val assets = r.getJSONArray("assets")
                var apk: org.json.JSONObject? = null
                var sums: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val name = a.getString("name")
                    if (name.endsWith(".apk")) apk = a
                    if (name.equals("SHA256SUMS.txt", ignoreCase = true)) sums = a.getString("browser_download_url")
                }
                if (apk == null) continue
                val digest = apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
                out += ReleaseInfo(
                    tag = r.getString("tag_name"),
                    title = r.optString("name").ifBlank { r.getString("tag_name") },
                    notes = r.optString("body"),
                    publishedAt = r.optString("published_at").take(10),
                    apkName = apk.getString("name"),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size"),
                    sha256 = digest?.lowercase(),
                    sumsUrl = sums,
                )
            }
            return out.sortedWith { a, b -> compareVersions(b.tag, a.tag) }
        } finally {
            c.disconnect()
        }
    }

    /** The checksum from GitHub's digest field, or failing that from the release's SHA256SUMS.txt. */
    private fun expectedHash(release: ReleaseInfo): String? {
        release.sha256?.let { return it }
        val url = release.sumsUrl ?: return null
        val c = open(url)
        try {
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().useLines { lines ->
                lines.map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 2 && it.last().removePrefix("*") == release.apkName }
                    ?.first()?.lowercase()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun download(release: ReleaseInfo): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, release.apkName)
        // Fail closed: without a published checksum there is nothing to verify the download against.
        val expected = expectedHash(release)
            ?: throw IOException("This release has no checksum, so it was not installed.")
        val c = open(release.apkUrl)
        try {
            if (c.responseCode != 200) throw IOException("The download failed (${c.responseCode}).")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) status = UpdateStatus.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                out.delete()
                throw IOException("The downloaded file did not match its checksum, so it was discarded.")
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    private fun commit(file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
            // Mutable on purpose: the system fills in the install status before delivering it.
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "GitHub took too long to answer."
        else -> e.message ?: "Something went wrong."
    }

    private companion object {
        const val SIX_HOURS = 6L * 60 * 60 * 1000
    }
}

/** Compares dotted version strings such as "v0.0.12" and "0.1.0" numerically. */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
        if (d != 0) return d
    }
    return 0
}
