package com.xiaohetv.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xiaohetv.data.Store
import com.xiaohetv.player.MpvEngine
import com.xiaohetv.player.MpvPlayerView
import com.xiaohetv.vm.PlayerVM
import kotlinx.coroutines.delay

/**
 * 详情页顶部的内嵌播放器（参考项目 fongmi 手机版 VideoActivity 的做法：
 * 详情页顶部就是播放器，进入即自动播放）。
 */
@Composable
internal fun InlinePlayer(
    info: PlayerVM.PlayInfo?,
    scale: Int,
    /** 自增即重建播放器（换解码方式用），播放进度由调用方按需续播。 */
    version: Int = 0,
    onPlayer: (ExoPlayer?) -> Unit = {},
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val ended by rememberUpdatedState(onEnded)
    val notify by rememberUpdatedState(onPlayer)

    // ---- 播放期间保持屏幕常亮（参考项目 FongMi 用 View.setKeepScreenOn 的做法）----
    // 原因：详情页播放器跑在 MainActivity 里，而常亮只写在 PlayerActivity 的
    // FLAG_KEEP_SCREEN_ON 上；手机默认几十秒就息屏 → 画面先变暗再黑屏，碰一下又亮，
    // 反复下来就是"手机自动降低亮度 / 自动刷屏"（TV 盒子通常不息屏，所以只在手机出现）。
    // 这里用 View 级 keepScreenOn（与宿主 Activity 无关），按"是否处于播放意图"开关：
    // 暂停/播完立即释放，不会一直占着屏幕。
    val host = LocalView.current
    LaunchedEffect(info) {
        if (info == null) {
            host.keepScreenOn = false
            return@LaunchedEffect
        }
        try {
            while (true) {
                host.keepScreenOn = if (Store.settings.engine == 1) {
                    MpvEngine.playing
                } else {
                    val instance = player
                    instance?.playWhenReady == true && instance.playbackState != Player.STATE_ENDED
                }
                delay(1000)
            }
        } finally {
            host.keepScreenOn = false
        }
    }
    DisposableEffect(Unit) { onDispose { host.keepScreenOn = false } }

    DisposableEffect(info, version) {
        val target = info ?: return@DisposableEffect onDispose { }
        // 选了 MPV 引擎就不建 ExoPlayer（两套核心同时渲染会互相干扰），交给 MpvPlayerView
        if (Store.settings.engine == 1) {
            notify(null)
            return@DisposableEffect onDispose { notify(null) }
        }
        val instance = buildPlayer(context, target.headers)
        try {
            instance.setMediaItem(mediaItemFor(target))
            instance.prepare()
            instance.playWhenReady = true
            com.xiaohetv.core.utils.VodTrace.stage(
                "prepare", "ok",
                extra = "scene=detail kind=${com.xiaohetv.core.utils.VodTrace.kindOf(target.url)}" +
                    " url=${com.xiaohetv.core.utils.VodTrace.mask(target.url)}"
            )
        } catch (t: Throwable) {
            com.xiaohetv.core.utils.VodTrace.fail("prepare", t, extra = "scene=detail url=${com.xiaohetv.core.utils.VodTrace.mask(target.url)}")
            throw t
        }
        val listener = object : Player.Listener {
            private var retriedHls = false

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 写进运行日志，便于定位"点了没反应/黑屏"
                com.xiaohetv.core.utils.Diag.error("Play", "详情页播放器错误：${error.errorCodeName}", error)
                // 对齐参考项目 ExoUtil.getMimeType：容器解析失败时按 HLS 再试一次
                val fallback = hlsFallbackMime(error.errorCode)
                if (!retriedHls && fallback != null && target.format.isEmpty()) {
                    retriedHls = true
                    com.xiaohetv.core.utils.Diag.log("Play", "详情页按 $fallback 重试一次")
                    instance.setMediaItem(
                        androidx.media3.common.MediaItem.Builder()
                            .setUri(target.url)
                            .setMimeType(fallback)
                            .build()
                    )
                    instance.prepare()
                    instance.playWhenReady = true
                }
            }
        }
        instance.addListener(listener)
        player = instance
        notify(instance)
        onDispose {
            instance.removeListener(listener)
            instance.release()
            player = null
            notify(null)
        }
    }

    if (Store.settings.engine == 1) {
        info?.let { target -> MpvPlayerView(url = target.url, headers = target.headers, modifier = modifier) }
    } else player?.let { instance ->
        AndroidView(
            factory = { ctx -> createPlayerView(ctx, instance, scale) },
            modifier = modifier,
            update = {
                it.player = instance
                it.resizeMode = resizeMode(scale)
            }
        )
    }
}
