package com.dlovel.plankton.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.dlovel.plankton.util.ReleaseLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val downloadUrl: String?,
    val downloadFileName: String?
)

@Serializable
private data class GithubReleaseResponse(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubAssetResponse> = emptyList()
)

@Serializable
private data class GithubAssetResponse(
    val name: String,
    val browser_download_url: String,
    val content_type: String? = null
)

object GithubReleaseService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = (URL(ReleaseLinks.LATEST_RELEASE_API_URL)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Ripple-Chronicles-Android")
        }

        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IllegalStateException("GitHub API returned HTTP $statusCode")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<GithubReleaseResponse>(response)
            if (release.draft || release.prerelease) {
                throw IllegalStateException("没有可用的稳定版本")
            }
            val apk = release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    (asset.name == "app-release.apk" ||
                        asset.content_type == "application/vnd.android.package-archive")
            }
            ReleaseInfo(
                tagName = release.tag_name,
                title = release.name?.takeIf { it.isNotBlank() } ?: release.tag_name,
                notes = release.body.orEmpty().trim(),
                downloadUrl = apk?.browser_download_url,
                downloadFileName = apk?.name
            )
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueApkDownload(context: Context, release: ReleaseInfo): Long {
        val url = release.downloadUrl
            ?: throw IllegalStateException("该版本没有可下载的 APK")
        val fileName = release.downloadFileName
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "ripple-chronicles-" + release.tagName.removePrefix("v") + ".apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("溯澜录 " + release.tagName)
            .setDescription("正在下载更新，完成后可从通知安装")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
}
