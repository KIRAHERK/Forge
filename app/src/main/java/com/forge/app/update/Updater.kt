package com.forge.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.forge.app.BuildConfig
import com.forge.app.data.AutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long, val page: String)

/**
 * Checks the GitHub repo's latest release, downloads the APK and hands it to the system installer.
 * Android only installs it over the current app if it is signed with the same key, which is also
 * what keeps all data in place.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val at: Long) : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class Ready(val info: UpdateInfo, val file: File) : State
        data class Failed(val message: String, val info: UpdateInfo? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    val repo: String get() = BuildConfig.UPDATE_REPO
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val releasesPage: String get() = "https://github.com/$repo/releases"

    private val json = Json { ignoreUnknownKeys = true }

    /** Numeric compare of "1.2.10" style versions. */
    fun compare(a: String, b: String): Int {
        val pa = a.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    /** Returns the newer release, or null when up to date. Throws on network errors. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL("${BuildConfig.UPDATE_API}/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Forge-Android-Updater")
        }
        try {
            when (conn.responseCode) {
                404 -> return@withContext null // no releases published yet
                in 200..299 -> {}
                else -> error("GitHub answered ${conn.responseCode}")
            }
            val rel = json.decodeFromString(GhRelease.serializer(), conn.inputStream.bufferedReader().use { it.readText() })
            if (rel.draft || rel.prerelease) return@withContext null
            val version = rel.tag.removePrefix("v")
            if (compare(version, currentVersion) <= 0) return@withContext null
            val apk = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
            UpdateInfo(version, rel.body?.trim().orEmpty(), apk.url, apk.size, rel.htmlUrl)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun check(): State {
        _state.value = State.Checking
        val next = runCatching { fetchLatest() }.fold(
            onSuccess = { info -> if (info == null) State.UpToDate(System.currentTimeMillis()) else State.Available(info) },
            onFailure = { State.Failed("Couldn't reach GitHub. Check your connection.") },
        )
        _state.value = next
        return next
    }

    /** Called by the background worker so the UI already knows when the app opens. */
    fun publish(info: UpdateInfo) {
        if (_state.value !is State.Downloading && _state.value !is State.Ready) _state.value = State.Available(info)
    }

    private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(ctx: Context, info: UpdateInfo) {
        _state.value = State.Downloading(info, 0f)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                updatesDir(ctx).listFiles()?.forEach { it.delete() }
                val out = File(updatesDir(ctx), "forge-${info.version}.apk")
                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Forge-Android-Updater")
                }
                try {
                    if (conn.responseCode !in 200..299) error("Download failed (${conn.responseCode})")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0 && read - lastEmit > 128 * 1024) {
                                    lastEmit = read
                                    _state.value = State.Downloading(info, (read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                verify(ctx, out)
                out
            }
        }
        _state.value = result.fold(
            onSuccess = { State.Ready(info, it) },
            onFailure = { State.Failed(it.message ?: "Download failed", info) },
        )
    }

    @Suppress("DEPRECATION")
    private fun signatures(pi: PackageInfo?): Set<String> {
        if (pi == null) return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pi.signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else pi.signatures
        return sigs?.map { it.toCharsString() }?.toSet() ?: emptySet()
    }

    /** Refuse anything that isn't this app, isn't newer, or isn't signed with the same key. */
    @Suppress("DEPRECATION")
    private fun verify(ctx: Context, apk: File) {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("The downloaded file isn't a valid app.")
        if (archive.packageName != ctx.packageName) error("The download is a different app. Not installing.")
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val oldCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (newCode <= oldCode) error("The download isn't newer than what you have.")
        val a = signatures(archive)
        val b = signatures(installed)
        if (a.isEmpty() || b.isEmpty() || a.intersect(b).isEmpty()) {
            error("Signed with a different key. Installing it would require wiping your data, so it was blocked.")
        }
    }

    fun canInstall(ctx: Context): Boolean = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Backs up first, then opens the system installer. */
    suspend fun install(ctx: Context, file: File) {
        runCatching { AutoBackup.run(ctx, reason = "before update") }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { ctx.startActivity(intent) }
    }
}
