package com.xiaohetv.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xiaohetv.core.net.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 在线更新：地址指向**本仓库自身**。
 *
 * 背景：设置页原来的「版本」行是写死的（点一下永远提示"当前已是最新版本"），既没有仓库地址也没有真实检测。
 *
 * 版本来源两路，先 raw 后 API：
 *   ① 仓库根目录 `update.json`（走 raw.githubusercontent，本机实测可达性最好）
 *   ② GitHub Releases API（`tag_name` + 第一个 `.apk` 资源）
 * 下载后通过 FileProvider 交给系统安装器（对应清单里的 REQUEST_INSTALL_PACKAGES）。
 */
object Updater {

    const val REPO = "hezhanleiok/xhtv"
    private const val RAW_JSON = "https://raw.githubusercontent.com/$REPO/main/update.json"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    val releasesPage = "https://github.com/$REPO/releases/latest"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class Release(val version: String, val url: String, val notes: String)

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 版本比较：去掉前缀 v，按数字逐段比（1.0.10 > 1.0.9）。 */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_', '+')
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 后台检查（回调在主线程）。 */
    fun checkAsync(onResult: (Result<Release>) -> Unit) {
        scope.launch {
            val result = runCatching { check() }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private fun check(): Release {
        // ① 仓库里的 update.json（最稳）
        runCatching {
            val json = JSONObject(OkHttp.string(RAW_JSON))
            val version = json.optString("version")
            val url = json.optString("url")
            if (version.isNotEmpty() && url.isNotEmpty()) {
                return Release(version, url, json.optString("notes"))
            }
        }
        // ② GitHub Releases API（必须带 User-Agent，否则 GitHub 直接 403）
        val text = OkHttp.string(
            API_LATEST,
            mapOf(
                "User-Agent" to "xhtv-updater",
                "Accept" to "application/vnd.github+json"
            )
        )
        val json = JSONObject(text)
        val tag = json.optString("tag_name")
        var url = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = asset.optString("browser_download_url")
                    break
                }
            }
        }
        if (tag.isEmpty() || url.isEmpty()) throw Exception("发布信息里没有可用的 APK")
        return Release(tag, url, json.optString("body"))
    }

    /** 后台下载 APK 到 cacheDir（进度回调在主线程，按百分比去重）。 */
    fun downloadAsync(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
        onDone: (Result<File>) -> Unit
    ) {
        scope.launch {
            val result = runCatching { download(context, url, onProgress) }
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    private suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit): File {
        val request = okhttp3.Request.Builder().url(url).build()
        OkHttp.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("响应为空")
            val total = body.contentLength()
            val out = File(context.cacheDir, "update.apk")
            var lastPercent = -1
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
            }
            if (out.length() <= 0L) throw Exception("下载内容为空")
            return out
        }
    }

    /** 调起系统安装器（FileProvider 授权，API 24+ 必需）。 */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
