package com.monzoflow.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.monzoflow.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewerThanCurrent: Boolean,
    val htmlUrl: String
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long, val release: ReleaseInfo) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val release: ReleaseInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("monzoflow_update_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    var cachedRelease: ReleaseInfo? = null
        private set

    val currentVersionName: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Throwable) {
            "1.0.2"
        }

    val currentVersionCode: Int
        get() = try {
            BuildConfig.VERSION_CODE
        } catch (e: Throwable) {
            2
        }

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkForUpdates(isManual: Boolean = false) {
        if (!isNetworkConnected()) {
            _updateState.value = UpdateState.Idle
            return
        }

        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking

        scope.launch {
            try {
                val releaseInfo = withContext(Dispatchers.IO) {
                    fetchLatestReleaseFromGitHub()
                }

                if (releaseInfo == null) {
                    _updateState.value = UpdateState.Idle
                    return@launch
                }

                cachedRelease = releaseInfo

                if (releaseInfo.isNewerThanCurrent) {
                    Timber.i("Update Available: Remote ${releaseInfo.tagName} > Local $currentVersionName")
                    _updateState.value = UpdateState.UpdateAvailable(
                        release = releaseInfo,
                        currentVersion = currentVersionName
                    )
                } else {
                    _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for updates")
                _updateState.value = if (isManual) UpdateState.Error("Update check failed: ${e.message}") else UpdateState.Idle
            }
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        cachedRelease = release
        _updateState.value = UpdateState.Downloading(0f, 0L, release.apkSizeBytes, release)

        scope.launch {
            try {
                val targetDir = File(context.cacheDir, "updates")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, release.apkFileName)

                withContext(Dispatchers.IO) {
                    val url = URL(release.apkDownloadUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                    }
                    conn.connect()

                    val contentLength = conn.contentLength.toLong().let { if (it > 0) it else release.apkSizeBytes }
                    var bytesDownloaded = 0L

                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesDownloaded += read
                                val progress = if (contentLength > 0) (bytesDownloaded.toFloat() / contentLength).coerceIn(0f, 1f) else 0f
                                _updateState.value = UpdateState.Downloading(progress, bytesDownloaded, contentLength, release)
                            }
                            output.flush()
                        }
                    }
                }

                _updateState.value = UpdateState.ReadyToInstall(targetFile, release)
                installApk(targetFile, release)
            } catch (e: Exception) {
                Timber.e(e, "Error downloading APK update")
                _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun installApk(apkFile: File, release: ReleaseInfo? = null) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = UpdateState.Error("Failed to launch installer: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun fetchLatestReleaseFromGitHub(): ReleaseInfo? {
        val endpoint = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }

        if (conn.responseCode != 200) {
            Timber.w("GitHub API returned HTTP ${conn.responseCode}")
            return null
        }

        val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)

        val tagName = root.optString("tag_name", "")
        val name = root.optString("name", tagName)
        val body = root.optString("body", "Bug fixes and stability updates.")
        val htmlUrl = root.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

        val assets = root.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var apkName = "monzoflow-update.apk"
        var apkSize = 0L

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name", "")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url", "")
                apkName = assetName
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        if (apkUrl.isBlank()) return null

        val isNewer = compareSemVer(tagName, currentVersionName) > 0

        return ReleaseInfo(
            tagName = tagName,
            releaseTitle = name,
            releaseNotes = body,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isNewerThanCurrent = isNewer,
            htmlUrl = htmlUrl
        )
    }

    private fun compareSemVer(remote: String, local: String): Int {
        val rClean = remote.removePrefix("v").substringBefore("-")
        val lClean = local.removePrefix("v").substringBefore("-")

        val rParts = rClean.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = lClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    companion object {
        const val GITHUB_REPO = "CharlesLennon/monzoflow"
    }
}
