package com.xiaohetv.player

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiaohetv.core.utils.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * WebView 嗅探解析（App 内置能力，不依赖任何外部库/模块）。
 *
 * 做法：把网页放进一个隐藏 WebView 真实加载，拦截页面发出的所有请求，
 * 从里面挑出真正的媒体地址（m3u8 / mp4 / flv / ts / mkv …）。
 *
 * 用途：
 *  - 配置里 `parse=1` 的"网页解析型"源：接口给的是网页地址，真实播放地址要嗅探；
 *  - spider 返回的地址不像媒体地址（比如直接返回一个播放页）时兜底。
 */
object Sniffer {

    private val MEDIA_EXT = listOf(
        ".m3u8", ".mp4", ".flv", ".mkv", ".ts", ".m4s", ".mpd", ".avi", ".mov", ".m4v", ".mp3", ".m4a"
    )

    /** App 启动时注入（PlayerVM 里没有 Context，嗅探需要它来做 WebView）。 */
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 供 ViewModel 使用（不需要自己持有 Context）。 */
    suspend fun sniffApp(pageUrl: String, waitMs: Long = 12000): List<String> {
        val ctx = appContext ?: return emptyList()
        return sniff(ctx, pageUrl, waitMs)
    }

    /** 这个地址像不像"能直接播"的媒体地址（不像就需要嗅探）。 */
    fun looksLikeMedia(url: String): Boolean {
        val u = url.trim().lowercase()
        if (u.isEmpty()) return false
        if (u.startsWith("magnet:") || u.startsWith("ed2k:") || u.startsWith("thunder:")) return true
        if (u.startsWith("file:") || u.startsWith("content:")) return true
        return MEDIA_EXT.any { u.substringBefore('?').endsWith(it) || u.contains(it) }
    }

    /**
     * 嗅探一个网页里的媒体地址。
     * @param pageUrl 要加载的网页（解析页 / 播放页）
     * @param waitMs  最长等待时间；一旦抓到第一个媒体地址会再多等 1.5s 收集同页备选线路
     * @return 按出现顺序去重的媒体地址列表（可能为空）
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(context: Context, pageUrl: String, waitMs: Long = 12000): List<String> =
        withContext(Dispatchers.Main) {
            val found = LinkedHashSet<String>()
            val web = WebView(context)
            try {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.mediaPlaybackRequiresUserGesture = false
                web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                web.settings.userAgentString = web.settings.userAgentString
                web.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        request?.url?.toString()?.let { if (it.isNotEmpty() && looksLikeMedia(it)) found.add(it) }
                        return null
                    }

                    @Deprecated("兼容旧接口")
                    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                        val u = url.orEmpty()
                        if (u.isNotEmpty() && looksLikeMedia(u)) found.add(u)
                        return null
                    }
                }
                web.loadUrl(pageUrl)
                val deadline = System.currentTimeMillis() + waitMs
                while (System.currentTimeMillis() < deadline && found.isEmpty()) delay(300)
                if (found.isNotEmpty()) delay(1500)
                Diag.log("Sniffer", "嗅探 ${pageUrl.take(80)} → ${found.size} 个媒体地址")
                found.toList()
            } catch (t: Throwable) {
                Diag.error("Sniffer", "嗅探失败：${pageUrl.take(80)}", t)
                emptyList()
            } finally {
                runCatching {
                    web.stopLoading()
                    web.webViewClient = WebViewClient()
                    web.destroy()
                }
                web.removeAllViews()
            }
        }
}
