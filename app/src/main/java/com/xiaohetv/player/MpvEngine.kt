package com.xiaohetv.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaohetv.core.utils.Diag
import `is`.xyz.mpv.MPVLib

/**
 * MPV 播放核心（内置 `libmpv.so` + `libplayer.so`，见 app/src/main/jniLibs/arm64-v8a）。
 *
 * 与 EXO 并列，由「播放引擎」面板切换（Store.settings.engine：0=EXO，1=MPV）。
 * 这里只做三件事：核心生命周期、播放状态回传给 Compose、以及基础控制（暂停/跳转/倍速）。
 */
object MpvEngine {

    var ready by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var error by mutableStateOf("")

    /** 缓冲速度文本（来自 mpv 的 cache-speed；取不到就保持空/上次值）。 */
    var speedText by mutableStateOf("")

    fun pause(p: Boolean) {
        runCatching { MPVLib.setPropertyString("pause", if (p) "yes" else "no") }
    }

    fun seekTo(ms: Long) {
        runCatching { MPVLib.command(arrayOf("seek", (ms / 1000.0).toString(), "absolute")) }
    }

    fun setSpeed(speed: Float) {
        runCatching { MPVLib.setPropertyString("speed", speed.toString()) }
    }

    /** 当前播放地址（供「刷新」重新 loadfile 用）。 */
    private var currentUrl: String = ""

    fun setUrl(url: String) {
        currentUrl = url
    }

    /** 刷新（点播底栏「刷新」）：用同一地址重新加载一遍。 */
    fun reload() {
        if (currentUrl.isEmpty()) return
        runCatching { MPVLib.command(arrayOf("loadfile", currentUrl, "replace")) }
    }

    /** 解码方式（照参考项目「解码设置」）：MPV 用 hwdec 表达。
     * 0=自动 auto-safe、1=硬件 mediacodec、2/3=软解 no（MPV 本身就是 FFmpeg 解码）。 */
    fun setDecode(mode: Int) {
        val value = when (mode) {
            1 -> "mediacodec"
            2, 3 -> "no"
            else -> "auto-safe"
        }
        runCatching { MPVLib.setPropertyString("hwdec", value) }
    }

    /** 比例：0=原始自适应、3=拉伸填满、4=裁剪填满。MPV 用 keepaspect/panscan 表达。 */
    fun setScale(scale: Int) {
        runCatching {
            when (scale) {
                3 -> {
                    MPVLib.setPropertyString("keepaspect", "no")
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                }
                4 -> {
                    MPVLib.setPropertyString("keepaspect", "yes")
                    MPVLib.setPropertyString("panscan", "1.0")
                }
                else -> {
                    MPVLib.setPropertyString("keepaspect", "yes")
                    MPVLib.setPropertyString("panscan", "0")
                    MPVLib.setPropertyString("video-aspect-override", "no")
                }
            }
        }
    }

    /** 循环（点播底栏「循环」）：单集循环开关。 */
    fun setLoop(one: Boolean) {
        runCatching { MPVLib.setPropertyString("loop-file", if (one) "inf" else "no") }
    }

    /**
     * 音轨 / 视轨 / 字幕切换（点播底栏 音轨/视轨/字幕）。
     * kind = aid / vid / sid；直接交给 mpv 的 `cycle` 命令（无需自己枚举轨道）。
     */
    fun cycleTrack(kind: String) {
        runCatching { MPVLib.command(arrayOf("cycle", kind)) }
    }

    fun reset() {
        speedText = ""
        ready = false
        playing = false
        position = 0L
        duration = 0L
        error = ""
    }
}

/**
 * MPV 播放视图：SurfaceView + MPVLib 生命周期。
 * URL 变化时复用同一个 MPV 实例（只重新 loadfile），避免频道/集数切换时反复 create/destroy。
 *
 * ## 修过的三个真 bug（对照日志 + mpv-android 官方用法）
 * 1. **缺 `gpu-context=android`**：我们的 `libmpv.so` 是 mpv-android 那份（选项表里含
 *    `gpu-context`/`android`/`mediacodec`），但 Android 上必须显式指定 GPU 上下文，
 *    否则 vo 初始化失败 → 表现为 mpv 刚拉取一次 m3u8 就断流（日志里 `Broken pipe`），画面永远不出来。
 * 2. **起播早于 Surface 挂载**：`loadfile` 必须等 `attachSurface` 之后，否则同样是 vo 起不来。
 *    这里改成 Surface 就绪后由 `LaunchedEffect(url, surfaceReady)` 统一起播。
 * 3. **旋转/全屏切换没 `replaceSurface`**：Surface 变了却不告诉 mpv → 画面停在旧 Surface（黑屏/花屏）。
 * 4. 另外加了 `idle=yes`：mpv 默认播完就退出，换集时 `loadfile replace` 会失效。
 * 5. 补了 mpv 自家日志与 `END_FILE` 错误上报（`Mpv[...]`），以后再出问题日志里直接能看到原因。
 */
@Composable
internal fun MpvPlayerView(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var surfaceReady by remember { mutableStateOf(false) }
    // Surface 每重建一次 +1：用它触发"重新挂载"，而不是靠 url 变化（否则会重复 loadfile 打断播放）
    var surfaceGen by remember { mutableStateOf(0) }
    // 已经交给 mpv 的地址：同一地址不重复 loadfile
    var loadedUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {}

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> MpvEngine.position = value * 1000
                    "duration" -> MpvEngine.duration = value * 1000
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                if (property == "pause") MpvEngine.playing = !value
            }

            override fun eventProperty(property: String, value: String) {}

            override fun eventProperty(property: String, value: Double) {
                when (property) {
                    "time-pos" -> MpvEngine.position = (value * 1000).toLong()
                    "duration" -> MpvEngine.duration = (value * 1000).toLong()
                    "cache-speed" -> if (value > 1024) MpvEngine.speedText = "${(value / 1024).toInt()} KB/s"
                }
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED ->
                        Diag.log("Mpv", "mpv 已加载文件（开始解码）")
                    MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG ->
                        Diag.log("Mpv", "mpv 视频输出已配置")
                }
            }

            // mpv 自己的失败原因（例如 "Failed to open …"、"vo/gpu: …" ）——以前完全没上报
            override fun eventEndFile(reason: Int, error: Int, errorString: String?) {
                if (reason == MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR || error != 0) {
                    MpvEngine.error = "MPV 播放失败：${errorString ?: "error=$error"}"
                    Diag.error("Mpv", "mpv 播放失败 reason=$reason error=$error ${errorString.orEmpty()}")
                }
            }
        }
        // mpv 内部日志（只收 warn 及更严重），排障时最关键的一手信息
        val logObserver = object : MPVLib.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) {
                if (level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_WARN) Diag.log("Mpv", "mpv/$prefix ${text.trim()}")
            }
        }
        try {
            MPVLib.create(context)
            MPVLib.init()
            // 关键：Android 必须用 android 这个 GPU 上下文 + GLES，否则 vo 初始化失败（见类注释 1）
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            // 播完/换集后实例保留，loadfile replace 才有效
            MPVLib.setOptionString("idle", "yes")
            MPVLib.setOptionString("keep-open", "yes")
            MPVLib.setOptionString("hwdec", "auto-safe")
            MPVLib.setOptionString("audio-client-name", "XHTV")
            // Android 上 curl 没有可用的 CA 文件（系统证书是目录，curl 要单文件），
            // 实测日志：`mpv/curl TLS certificate verification failed` → `stream Failed to open https://…`
            // 于是所有 HTTPS 点播源在 MPV 下必然打不开。mpv-android 系播放器同样关闭校验。
            MPVLib.setOptionString("tls-verify", "no")
            // UA：mpv 默认 UA 是 "mpv …"，不少源站按 UA 防盗链会直接拒绝（与 EXO 侧保持同一策略）
            val ua = com.xiaohetv.data.Store.settings.ua.trim()
            if (ua.isNotEmpty()) MPVLib.setOptionString("user-agent", ua)
            if (headers.isNotEmpty()) {
                MPVLib.setOptionString(
                    "http-header-fields",
                    headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                )
            }
            MPVLib.addObserver(observer)
            MPVLib.addLogObserver(logObserver)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            // 缓冲速度：属性不存在时只是收不到事件，不影响播放
            runCatching { MPVLib.observeProperty("cache-speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE) }
            MpvEngine.ready = true
            // 把「解码」「比例」真正作用到 MPV —— 否则这两项在 MPV 下就是占位符
            MpvEngine.setDecode(com.xiaohetv.data.Store.settings.decodeVideo)
            MpvEngine.setScale(com.xiaohetv.data.Store.settings.scale)
            Diag.log("Mpv", "MPV 核心初始化成功（libmpv + libplayer 已加载，gpu-context=android）")
        } catch (t: Throwable) {
            MpvEngine.error = "MPV 核心初始化失败：${t.message}"
            Diag.error("Mpv", "MPV 核心初始化失败", t)
        }
        onDispose {
            runCatching {
                MPVLib.removeObserver(observer)
                MPVLib.removeLogObserver(logObserver)
                MPVLib.detachSurface()
                MPVLib.destroy()
            }
            MpvEngine.reset()
        }
    }

    // 起播时机：URL 变化、或 Surface 重新挂好。
    //
    // 上一版是"surfaceReady 就 loadfile"，实测日志暴露两个问题：
    //   · 同一地址被 load 两次（同秒两条"起播"）→ 打断播放并重建视频输出；
    //   · Surface 刚销毁/重建时又去 loadfile → mpv 报
    //     `vo/gpu/android Missing surface pointer` + `Failed initializing any suitable GPU context!`
    //     （这正是"直播能播、点播播不了"的直接原因：点播在详情页 InlinePlayer 里，Surface 会被销毁重建）
    // 现在：同一地址只 load 一次；Surface 重建且上次确实失败了，才清错误重试一次；其余情况只挂 Surface。
    LaunchedEffect(url, surfaceReady, surfaceGen) {
        MpvEngine.setUrl(url)
        if (!surfaceReady || !MpvEngine.ready || url.isEmpty()) return@LaunchedEffect
        when {
            url != loadedUrl -> {
                loadedUrl = url
                Diag.log("Mpv", "mpv 起播（Surface 已就绪）")
            }
            MpvEngine.error.isNotEmpty() -> {
                MpvEngine.error = ""
                Diag.log("Mpv", "Surface 重建后重试起播")
            }
            else -> return@LaunchedEffect
        }
        runCatching { MPVLib.command(arrayOf("loadfile", url, "replace")) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        runCatching { MPVLib.attachSurface(holder.surface) }
                            .onFailure { Diag.warn("Mpv", "attachSurface 失败：${it.message}") }
                        surfaceReady = true
                        surfaceGen++        // 触发上面的 effect：只在必要时重试起播
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        // 旋转/全屏切换会换 Surface：不 replace 就会停在旧 Surface 上（黑屏）
                        if (MpvEngine.ready) {
                            runCatching { MPVLib.replaceSurface(holder.surface) }
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        surfaceReady = false
                        runCatching { MPVLib.detachSurface() }
                    }
                })
            }
        }
    )
}
