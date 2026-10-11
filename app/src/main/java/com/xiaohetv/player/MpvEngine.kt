package com.xiaohetv.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
 */
@Composable
internal fun MpvPlayerView(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                }
            }

            override fun event(eventId: Int) {}
        }
        try {
            MPVLib.create(context)
            MPVLib.init()
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("hwdec", "auto-safe")
            MPVLib.setOptionString("keep-open", "yes")
            if (headers.isNotEmpty()) {
                MPVLib.setOptionString(
                    "http-header-fields",
                    headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                )
            }
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MpvEngine.ready = true
            Diag.log("Mpv", "MPV 核心初始化成功（libmpv + libplayer 已加载）")
        } catch (t: Throwable) {
            MpvEngine.error = "MPV 核心初始化失败：${t.message}"
            Diag.error("Mpv", "MPV 核心初始化失败", t)
        }
        onDispose {
            runCatching {
                MPVLib.removeObserver(observer)
                MPVLib.detachSurface()
                MPVLib.destroy()
            }
            MpvEngine.reset()
        }
    }

    // URL 变化（换台/换集）时复用实例重新加载
    LaunchedEffect(url) {
        MpvEngine.setUrl(url)
        if (MpvEngine.ready && url.isNotEmpty()) {
            runCatching { MPVLib.command(arrayOf("loadfile", url, "replace")) }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        runCatching {
                            MPVLib.attachSurface(holder.surface)
                            if (url.isNotEmpty()) MPVLib.command(arrayOf("loadfile", url, "replace"))
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        runCatching { MPVLib.detachSurface() }
                    }
                })
            }
        }
    )
}
