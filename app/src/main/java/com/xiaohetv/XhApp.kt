package com.xiaohetv

import android.app.Application
import com.xiaohetv.core.Core
import com.xiaohetv.data.Store

class XhApp : Application(), coil.ImageLoaderFactory {

    /** Coil 图片加载：失败时写入运行日志，避免"海报看不到"却无从判断。 */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        // 还原 TVBox 约定：图片 URL 后缀里的 @Referer= / @User-Agent= 要变成真正的请求头，
        // 否则防盗链站点（如豆瓣）会返回 418，首页就"只有字没有海报"。
        .okHttpClient {
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(com.xiaohetv.player.ImageHeaderInterceptor())
                // 死主机/被劫持域名（日志里 bdcache1-f1.v3mh.com → 127.0.0.1）默认要挂满 10s 才失败，
                // 20 张图同时排队会把图片线程池长时间占死；这里把超时收紧。
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
        .eventListener(object : coil.EventListener {
            override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                com.xiaohetv.core.utils.Diag.error(
                    "Image",
                    "加载失败 ${com.xiaohetv.core.utils.Diag.safeUrl(request.data.toString())}：${result.throwable.message}",
                    result.throwable
                )
            }
        })
        .build()


    override fun onCreate() {
        super.onCreate()
        Core.init(this)
        // WebView 嗅探解析（parse=1 网页解析型源）需要 Context，这里注入一次
        com.xiaohetv.player.Sniffer.init(this)

        // 主线程卡顿看门狗：主线程超过 2s 回不来就记一条日志。
        // 卡死/ANR 报告里没有堆栈是最难查的，有了这条下次直接能定位到时间点。
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var lastTick = android.os.SystemClock.uptimeMillis()
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val now = android.os.SystemClock.uptimeMillis()
                val delta = now - lastTick
                if (delta > 2000) {
                    com.xiaohetv.core.utils.Diag.warn("Perf", "主线程阻塞 ${delta}ms（疑似卡顿/ANR）")
                }
                lastTick = now
                mainHandler.postDelayed(this, 1000)
            }
        }, 1000)
        // 让 jar 兼容层（com.github.catvod.utils.* / Path / Prefers）也能拿到 Context
        com.github.catvod.Init.set(this)
        // 本地代理服务：spider 会把播放地址指向 http://127.0.0.1:<port>/proxy，
        // 不起这个服务端口就是 -1，点播会报连接失败（对齐参考项目 server/Server.start）
        com.xiaohetv.server.LocalServer.start()
        // 安装包指纹：用于确认"装的到底是不是最新那一版"，以及本地代理端口是否起来
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val stamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(info.lastUpdateTime))
            com.xiaohetv.core.utils.Diag.log(
                "App",
                "安装包：versionName=${info.versionName}｜安装/更新时间=$stamp｜本地代理端口=${com.xiaohetv.server.LocalServer.port}"
            )
        }
        Store.init(this)
        // 依据日志证据：真实 spider 会直接读宿主 SQLite 库 databases/tv 的 Config 表
        // （与参考项目 AppDatabase(NAME="tv") 的 Config 实体同构）。
        // 按同款结构建库建表，避免它的初始化被 SQLite 异常中断。
        com.xiaohetv.core.utils.LegacyDb.ensure()
        // 运行诊断：记录启动与未捕获崩溃（排查 Guard/jar 加载问题用，可随时移除）
        com.xiaohetv.core.utils.Diag.installCrashHandler()
        com.xiaohetv.core.utils.Diag.log(
            "App",
            "启动：Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})，" +
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
    }
}
