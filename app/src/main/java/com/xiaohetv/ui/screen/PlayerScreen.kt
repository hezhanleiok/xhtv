package com.xiaohetv.ui.screen

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.Util
import com.xiaohetv.core.utils.VodTrace
import com.xiaohetv.player.MpvEngine
import com.xiaohetv.player.MpvPlayerView
import com.xiaohetv.player.TracedDataSourceFactory
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosSecondary
import com.xiaohetv.vm.LiveVM
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.config.ConfigStore
import com.xiaohetv.core.config.LiveConfig
import com.xiaohetv.vm.ConfigVM
import com.xiaohetv.vm.PlayerVM
import com.xiaohetv.vm.SiteVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PanelBg = Color(0xFFF7F3EC)
private val PanelGroupsBg = Color(0xFFEDE6DA)
private val PanelActiveBg = Color(0xFFE4D6BE)
private val PanelActiveText = Color(0xFF8A5A2B)
private val BarLabelColor = Color(0xFFDDDDDD)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val vm: PlayerVM = viewModel()
    val info by vm.info.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    // 直播/点播必须按"这次进入的入口"判定，不能看瞬时状态：
    // 从底部「直播」进入时直播列表还没加载完（Session.live/channel 都还是 null），
    // 旧判断会先当成点播 → 先显示点播菜单，加载完再切成直播菜单（用户反馈"直播有两个菜单"）。
    val isLive = Session.livePending || (Session.live != null && Session.channel != null)
    val vod = Session.vod
    val flags = vod?.flags() ?: emptyList()

    var flagIndex by remember { mutableIntStateOf(Session.flagIndex) }
    val episodes = flags.getOrNull(flagIndex)?.episodes ?: emptyList()

    var episodeIndex by remember { mutableIntStateOf(Session.episodeIndex) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var controls by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    var engineSheet by remember { mutableStateOf(false) }
    var decodeSheet by remember { mutableStateOf(false) }
    var decodeTab by remember { mutableIntStateOf(0) }
    // 直播底栏两级选择：配置=所有订阅；综合直播=当前订阅下的直播源
    var configSheet by remember { mutableStateOf(false) }
    var liveSourceSheet by remember { mutableStateOf(false) }
    val configVM: ConfigVM = viewModel()
    val siteVM: SiteVM = viewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var panel by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    /** 已加载字节（AnalyticsListener 累加），用来算 KB/s。 */
    var bytesLoaded by remember { mutableLongStateOf(0L) }
    var speedText by remember { mutableStateOf("") }
    /** 真正的缓冲状态（播放中卡顿才为 true），加载提示只按它显示。 */
    var buffering by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf("") }
    var liveError by remember { mutableStateOf("") }

    var scale by remember { mutableIntStateOf(Store.settings.scale) }
    var decoder by remember { mutableIntStateOf(Store.settings.decode) }
    var speed by remember { mutableFloatStateOf(Store.settings.speed) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    // 循环播放（点播底栏「循环」）：单集循环
    var loopOne by remember { mutableStateOf(false) }
    // 片头/片尾跳过点（0 = 未设置）；片头一集只跳一次
    var introMs by remember { mutableLongStateOf(0L) }
    var outroMs by remember { mutableLongStateOf(0L) }
    var introDone by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }

    // 直播换台面板
    val liveVM: LiveVM = viewModel()
    val liveGroups by liveVM.groups.collectAsState()
    val liveEpg by liveVM.epg.collectAsState()
    var panelGroupIndex by remember { mutableIntStateOf(0) }
    val panelGroups = if (liveGroups.isNotEmpty()) liveGroups else Session.live?.groups ?: emptyList()

    // 播放控制按「播放引擎」分派：MPV 模式下走 MpvEngine，否则走 ExoPlayer。
    // （上一轮只接了渲染与状态回传，seek/暂停在 MPV 下点了没反应，这里补齐）
    val doSeek: (Long) -> Unit = { ms ->
        if (Store.settings.engine == 1) MpvEngine.seekTo(ms) else player?.seekTo(ms)
    }
    val doTogglePlay: () -> Unit = {
        if (Store.settings.engine == 1) {
            MpvEngine.pause(MpvEngine.playing)
        } else {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    // 点播：解析失败（例如站点返回“没有选集/没有播放源”）→ 自动换到其它站点的同名影片
    // （参考项目 / TVBox 的换源逻辑；只在一棵树上吊死必然大量“播不了”）
    // 只看错误本身：之前判断里有 `info != null`，上一次播放成功留下的旧 info 会让这里永远 return，
    // 自动换源根本没机会执行（实测日志里一次 跨站点换源 记录都没有）。
    // 重复尝试由 PlayerVM 的 failedVodSites 兜住，不会死循环。
    LaunchedEffect(vm.error) {
        if (isLive || vm.error.value.isEmpty()) return@LaunchedEffect
        Toast.makeText(context, "当前站点放不了，正在尝试其它站点…", Toast.LENGTH_SHORT).show()
        vm.switchSameVodSite()
    }

    fun switchEpisode(index: Int) {
        if (index < 0 || index >= episodes.size) return
        episodeIndex = index
        Session.episodeIndex = index
        introDone = false
        outroMs = 0L
        vm.start()
    }

    fun switchChannel(channel: Channel) {
        panel = false
        liveError = ""
        channel.index = 0
        Session.channel = channel
        vm.start()
    }

    /** 组内频道上下切换（供滑动 / 上下键换台使用）。 */
    fun moveChannel(delta: Int) {
        val cur = Session.channel ?: return
        val group = panelGroups.firstOrNull { g -> g.channel.any { it.name == cur.name } } ?: return
        val list = group.channel
        val idx = list.indexOfFirst { it.name == cur.name }
        list.getOrNull(idx + delta)?.let { switchChannel(it) }
    }

    fun nextFlag() {
        if (flags.size <= 1) return
        flagIndex = if (flagIndex >= flags.size - 1) 0 else flagIndex + 1
        Session.flagIndex = flagIndex
        Session.episodeIndex = 0
        episodeIndex = 0
        vm.start()
    }

    fun toggleDecoder() {
        decoder = if (decoder == 0) 1 else 0
        Store.settings.decode = decoder
        // 同时驱动新的「解码设置」：之前只写老字段 decode，decodeVideo/decodeAudio 从没被改过
        val mode = if (decoder == 1) 1 else 0
        Store.settings.decodeVideo = mode
        Store.settings.decodeAudio = mode
        Store.saveSettings()
        if (Store.settings.engine == 1) MpvEngine.setDecode(mode) else decoder++
    }

    fun cycleScale() {
        scale = when (scale) {
            0 -> 3
            3 -> 4
            else -> 0
        }
        Store.settings.scale = scale
        Store.saveSettings()
        // MPV 没有 PlayerView 的 resizeMode，比例要显式下发给 mpv，否则该项在 MPV 下等于没实现
        if (Store.settings.engine == 1) MpvEngine.setScale(scale)
    }

    fun cycleSpeed() {
        val options = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val i = options.indexOfFirst { it == speed }.let { if (it < 0) 1 else it }
        speed = options[(i + 1) % options.size]
        Store.settings.speed = speed
        Store.saveSettings()
        if (Store.settings.engine == 1) MpvEngine.setSpeed(speed) else player?.let { it.playbackParameters = it.playbackParameters.withSpeed(speed) }
    }

    fun cycleTrack(type: Int) {
        val instance = player ?: return
        val groups = instance.currentTracks.groups.filter { it.type == type }
        if (groups.size <= 1) return
        val cur = groups.indexOfFirst { it.isSelected }
        val next = if (cur < 0) 0 else (cur + 1) % groups.size
        val override = TrackSelectionOverride(groups[next].mediaTrackGroup, 0)
        instance.trackSelectionParameters = instance.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .setTrackTypeDisabled(type, false)
            .build()
        tracksVersion++
    }

    // 「点直播」直接进播放器时的加载态：在横屏播放器里加载直播列表，再播第一个频道
    var livePending by remember { mutableStateOf(Session.livePending) }

    LaunchedEffect(Unit) {
        if (livePending) {
            liveVM.refresh()
            liveVM.open()
        } else {
            vm.start()
            if (isLive) {
                val live = Session.live
                if (live != null && live.groups.isEmpty()) liveVM.open(live)
                else liveVM.refresh()
            }
        }
    }

    LaunchedEffect(liveGroups) {
        if (livePending && liveGroups.isNotEmpty()) {
            val live = Session.live
            val first = liveGroups.firstOrNull()?.channel?.firstOrNull()
            if (live != null && first != null) {
                first.index = 0
                Session.channel = first
                livePending = false
                Session.livePending = false
                vm.start()
            }
        }
    }

    LaunchedEffect(info) { liveError = "" }

    DisposableEffect(info, decoder) {
        // 选了 MPV 引擎就不建 ExoPlayer（否则两套核心会同时出声）
        if (Store.settings.engine == 1) return@DisposableEffect onDispose { }
        val target = info ?: return@DisposableEffect onDispose { }
        val instance = buildPlayer(context, target.headers)
        var watchdog: kotlinx.coroutines.Job? = null
        try {
            instance.setMediaItem(mediaItemFor(target))
            instance.playbackParameters = instance.playbackParameters.withSpeed(speed)
            instance.prepare()
            instance.playWhenReady = true
            VodTrace.stage(
                "prepare", "ok",
                extra = "kind=${VodTrace.kindOf(target.url)} url=${VodTrace.mask(target.url)} speed=$speed"
            )
            // 起播看门狗：5 秒一档；出画面立即退出；5/10 秒各重试一次；15 秒仍无画面 → 自动换源
            // （换源走 PlayerVM.nextSource()：本频道多线路 → 同名跨源 + 并行预探测取最快源）
            watchdog = scope.launch {
                repeat(2) {
                    kotlinx.coroutines.delay(5000)
                    if (instance.currentPosition > 0 || instance.playbackState == Player.STATE_READY) return@launch
                    Diag.warn("Play", "起播看门狗：自动重试 ${VodTrace.mask(target.url)}")
                    runCatching {
                        instance.prepare()
                        instance.playWhenReady = true
                    }
                }
                kotlinx.coroutines.delay(5000)
                if (instance.currentPosition <= 0) {
                    Diag.warn("Play", "起播看门狗：仍无画面，自动换源")
                    vm.nextSource()
                }
            }
        } catch (t: Throwable) {
            VodTrace.fail("prepare", t, extra = "url=${VodTrace.mask(target.url)}")
            throw t
        }
        // 详情页切全屏带过来的断点优先于历史记录（否则表现就是"切全屏从头开始播"）
        val carry = if (isLive) 0L else Session.startPositionMs
        if (carry > 0) {
            instance.seekTo(carry)
            Session.startPositionMs = 0
        } else if (!isLive && Store.settings.resume && !Store.settings.incognito) {
            val history = Store.findHistory(Session.site?.key.orEmpty(), vod?.vodId.orEmpty())
            val flag = vod?.flags()?.getOrNull(Session.flagIndex)
            if (history != null && flag != null && history.flag == flag.flag && history.position > 0 && history.duration > 0) {
                if (history.position < history.duration - 5000) instance.seekTo(history.position)
            }
        }
        val listener = object : Player.Listener {
            private var retriedHls = false

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !isLive && episodeIndex + 1 < episodes.size) switchEpisode(episodeIndex + 1)
            }

            override fun onPlayerError(e: PlaybackException) {
                com.xiaohetv.core.utils.Diag.error("Play", "播放器错误：${e.errorCodeName}｜url=${com.xiaohetv.core.utils.Diag.safeUrl(target.url)}", e)
                // 对齐参考项目 ExoUtil.getMimeType：容器解析失败时按 HLS 再试一次（很多源的地址看不出是 m3u8）
                val fallback = hlsFallbackMime(e.errorCode)
                if (!retriedHls && fallback != null && target.format.isEmpty()) {
                    retriedHls = true
                    Diag.log("Play", "按 $fallback 重试一次")
                    instance.setMediaItem(MediaItem.Builder().setUri(target.url).setMimeType(fallback).build())
                    instance.prepare()
                    instance.playWhenReady = true
                    return
                }
                if (!isLive) {
                    // 点播：播放器层失败（403/格式/断流）→ 自动换到其它站点的同名影片
                    scope.launch { vm.switchSameVodSite() }
                    return
                }
                val ok = vm.nextSource()
                if (!ok) liveError = "当前频道所有源都无法播放，换一个频道试试"
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                tracksVersion++
            }
        }
        // 加载速度统计：点击播放后显示实时 KB/s
        instance.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
            ) {
                bytesLoaded += loadEventInfo.bytesLoaded
            }
        })
        instance.addListener(listener)
        player = instance

        onDispose {
            if (!isLive) saveProgress(instance)
            instance.removeListener(listener)
            watchdog?.cancel()
            instance.release()
            player = null
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (Store.settings.engine == 1) {
                position = MpvEngine.position
                duration = MpvEngine.duration
                playing = MpvEngine.playing
                buffering = false
                if (!isLive) {
                    if (introMs > 0 && !introDone) { if (position in 0 until introMs) MpvEngine.seekTo(introMs); introDone = true }
                    if (outroMs > 0 && position >= outroMs) { outroMs = 0L; if (episodeIndex + 1 < episodes.size) switchEpisode(episodeIndex + 1) }
                }
                delay(500)
                continue
            }
            val current = player ?: break
            position = current.currentPosition
            duration = if (current.duration > 0) current.duration else 0L
            playing = current.isPlaying
            buffering = current.playbackState == Player.STATE_BUFFERING
            // 片头/片尾：设了跳过点就自动跳（片头一集只跳一次；片尾到点直接下一集）
            if (!isLive) {
                if (introMs > 0 && !introDone) {
                    if (position in 0 until introMs) current.seekTo(introMs)
                    introDone = true
                }
                if (outroMs > 0 && position >= outroMs) {
                    outroMs = 0L
                    if (episodeIndex + 1 < episodes.size) switchEpisode(episodeIndex + 1)
                }
            }
            delay(500)
        }
    }

    // 每秒算一次加载速度（KB/s）
    LaunchedEffect(player) {
        var lastBytes = 0L
        while (true) {
            if (Store.settings.engine == 1) {
                position = MpvEngine.position
                duration = MpvEngine.duration
                playing = MpvEngine.playing
                buffering = false
                if (MpvEngine.speedText.isNotEmpty()) speedText = MpvEngine.speedText
                delay(500)
                continue
            }
            val now = bytesLoaded
            val delta = now - lastBytes
            lastBytes = now
            if (delta > 512 * 1024) speedText = String.format("%.2f MB/s", delta / 1048576.0)
                else if (delta > 1024) speedText = "${delta / 1024} KB/s"
            delay(1000)
        }
    }

    // 控制层自动隐藏（直播/点播都一样），避免一直挡画面
    LaunchedEffect(controls, sheet, panel, locked) {
        if (controls && !sheet && !panel && !locked) {
            delay(4500)
            controls = false
        }
    }

    // 直播：上下滑动换台
    val swipeModifier = if (isLive && !panel) {
        Modifier.pointerInput(panelGroups) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { _, dy -> total += dy },
                onDragEnd = {
                    if (total <= -90f) moveChannel(1)
                    else if (total >= 90f) moveChannel(-1)
                }
            )
        }
    } else Modifier

    // 左右滑动快进/快退（照参考项目：右滑快进、左滑快退；整屏宽度对应 120 秒，松手生效）
    val seekModifier = if (!isLive) {
        Modifier.pointerInput(Unit) {
            var acc = 0f
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f },
                onDragEnd = {
                    val deltaMs = (acc / size.width * 120_000f).toLong()
                    if (deltaMs != 0L && duration > 0) {
                        doSeek((position + deltaMs).coerceIn(0L, duration))
                    }
                    seekHint = ""
                    acc = 0f
                },
                onDragCancel = { acc = 0f; seekHint = "" }
            ) { _, dx ->
                acc += dx
                val deltaMs = (acc / size.width * 120_000f).toLong()
                val target = (position + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
                seekHint = (if (deltaMs >= 0) "快进 " else "快退 ") + formatTime(target)
            }
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(swipeModifier)
            .then(seekModifier)
            .combinedClickable(
                // 单击/双击：唤出或隐藏控制层（双击强制唤出）；长按：打开节目单
                onClick = { if (!locked) controls = !controls },
                onDoubleClick = { if (!locked) controls = true },
                onLongClick = {
                    if (!locked && isLive) {
                        openPanel(panelGroups) { panelGroupIndex = it }
                        panel = true
                    }
                }
            )
    ) {
        if (Store.settings.engine == 1) {
            info?.let { target -> MpvPlayerView(url = target.url, headers = target.headers, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = if (flip) -1f else 1f)) }
        } else player?.let { instance ->
            AndroidView(
                factory = { ctx -> createPlayerView(ctx, instance, scale) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = if (flip) -1f else 1f),
                update = {
                    it.player = instance
                    it.resizeMode = resizeMode(scale)
                }
            )
        }

        // 只保留白圈 + 速度：不再显示"解析播放地址…/正在进入直播…"这类文字。
        // MPV 模式用 mpv 的时长/进度判断是否已出画面（没有 ExoPlayer 的字节统计）。
        val mpvBooting = Store.settings.engine == 1 && MpvEngine.duration <= 0L && MpvEngine.position <= 0L
        if (loading || livePending || mpvBooting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = speedText.ifEmpty { "缓冲中…" }, color = Color.White, fontSize = 13.sp)
                }
            }
        }

        val shownError = error.ifEmpty { liveError }
        if (shownError.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = shownError, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
            }
        }

        if (controls) {
            PlayerTopBar(
                isLive = isLive,
                title = info?.title.orEmpty().ifEmpty { if (isLive) Session.channel?.name.orEmpty() else vod?.vodName.orEmpty() },
                subtitle = if (isLive) liveSubtitle(panelGroups) else info?.episode.orEmpty(),
                onBack = {
                    player?.let { if (!isLive) saveProgress(it) }
                    onFinish()
                },
                onSheet = { sheet = true },
                onCast = { Toast.makeText(context, "投屏功能暂未开放", Toast.LENGTH_SHORT).show() },
                onInfo = {
                    val url = player?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                    Toast.makeText(context, url.ifEmpty { "没有播放地址" }, Toast.LENGTH_LONG).show()
                },
                showSheet = !isLive && episodes.size > 1
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (isLive) moveChannel(-1) else switchEpisode(episodeIndex - 1) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一个", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = {
                    doTogglePlay()
                }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                IconButton(onClick = { if (isLive) moveChannel(1) else switchEpisode(episodeIndex + 1) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一个", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (isLive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(text = "直播", color = IosBlue, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        val channel = Session.channel
                        Text(
                            text = channel?.let { "线路 ${it.index + 1}/${it.urls.size.coerceAtLeast(1)}" }.orEmpty(),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(text = formatTime(position), color = Color.White, fontSize = 12.sp)
                        // 参考项目：细轨（2dp）+ 小圆点，不是 Material3 那种粗轨道。
                        // 左右拖动 = 快进/快退（对应电视版遥控器左右键），松手才 seek。
                        var dragging by remember { mutableStateOf(false) }
                        var dragPos by remember { mutableLongStateOf(0L) }
                        val total = duration.coerceAtLeast(1L)
                        val shown = if (dragging) dragPos else position
                        val frac = (shown.toFloat() / total).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .padding(horizontal = 8.dp)
                                .pointerInput(total) {
                                    detectTapGestures { at ->
                                        doSeek((at.x / size.width * total).toLong())
                                    }
                                }
                                .pointerInput(total) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { dragging = true; dragPos = position },
                                        onDragEnd = { doSeek(dragPos); dragging = false },
                                        onDragCancel = { dragging = false }
                                    ) { change, _ ->
                                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                        dragPos = (x / size.width * total).toLong()
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(frac.coerceAtLeast(0.001f))
                                        .fillMaxHeight()
                                        .background(IosBlue),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    // 小圆点贴在已播/未播交界处
                                    Box(modifier = Modifier.size(9.dp).background(IosBlue, CircleShape))
                                }
                                Box(
                                    modifier = Modifier
                                        .weight((1f - frac).coerceAtLeast(0.0001f))
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                        Text(text = formatTime(duration), color = Color.White, fontSize = 12.sp)
                    }
                }
                val audioLabel = remember(tracksVersion, player) { trackLabelFor(player, C.TRACK_TYPE_AUDIO) }
                val textLabel = remember(tracksVersion, player) { trackLabelFor(player, C.TRACK_TYPE_TEXT) }
                val videoLabel = remember(tracksVersion, player) { trackLabelFor(player, C.TRACK_TYPE_VIDEO) }
                val sourceName = if (isLive) liveSubtitle(panelGroups) else flags.getOrNull(flagIndex)?.flag.orEmpty()
                val lineNo = if (isLive) "${(Session.channel?.index ?: 0) + 1}" else "${flagIndex + 1}"
                val leftButtons = mutableListOf<Triple<String, String, () -> Unit>>()
                val rightButtons = mutableListOf<Triple<String, String, () -> Unit>>()
                // 直播左组：配置(源名) / 解码 / 比例（参考项目直播底栏就这三项：无线路、无倍速、无选集）
                if (isLive) {
                    // 照参考项目直播底栏：配置 · 综合直播 · EXO · 解码 · 原始
                    // 「配置」=所有已添加的订阅；「综合直播」=当前订阅下的直播源列表（两级不是一回事）
                    leftButtons.add(Triple("配置", "", { configSheet = true }))
                    leftButtons.add(
                        Triple(
                            LiveConfig.home?.name ?: Session.live?.name ?: "综合直播",
                            "",
                            { liveSourceSheet = true }
                        )
                    )
                    leftButtons.add(Triple(Store.engineText(), "", { engineSheet = true }))
                    leftButtons.add(Triple("解码", Store.decodeModeText(Store.settings.decodeVideo), { decodeSheet = true }))
                    leftButtons.add(Triple("比例", when (scale) { 3 -> "拉伸"; 4 -> "裁剪"; else -> "原始" }, { cycleScale() }))
                } else {
                    // 点播左组：选集 / 解码 / 刷新 / 重播 / 循环 / 倍速 / 比例（照参考项目手机版 activity_video）
                    if (episodes.size > 1) leftButtons.add(Triple("选集", "", { sheet = true }))
                    // 照参考项目：播放引擎（EXO/MPV/其它）与 解码（解码设置）是两个独立按钮
                    leftButtons.add(Triple(Store.engineText(), "", { engineSheet = true }))
                    leftButtons.add(Triple("解码", Store.decodeModeText(Store.settings.decodeVideo), { decodeSheet = true }))
                    leftButtons.add(Triple("刷新", "", {
                        // 刷新：把当前媒体源重新加载一遍
                        if (Store.settings.engine == 1) MpvEngine.reload() else player?.let { it.stop(); it.prepare(); it.playWhenReady = true }
                    }))
                    leftButtons.add(Triple("重播", "", { doSeek(0L); if (Store.settings.engine == 1) MpvEngine.pause(false) else player?.play() }))
                    leftButtons.add(Triple("循环", if (loopOne) "单集" else "", {
                        loopOne = !loopOne
                        if (Store.settings.engine == 1) MpvEngine.setLoop(loopOne) else player?.repeatMode = if (loopOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    }))
                    leftButtons.add(Triple("倍速", "${speed}x", { cycleSpeed() }))
                    leftButtons.add(Triple("比例", when (scale) { 3 -> "拉伸"; 4 -> "裁剪"; else -> "原始" }, { cycleScale() }))
                }
                // 两套菜单严格分开（照参考项目）：
                //   直播右组：音轨 / 视轨 / 指令 / 反转 / 跨类 / 换源
                //   点播右组：字幕 / 音轨 / 视轨 / 片头 / 片尾 / 指令
                val showUrl: () -> Unit = {
                    val url = player?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                    Toast.makeText(context, url.ifEmpty { "没有播放地址" }, Toast.LENGTH_LONG).show()
                }
                if (isLive) {
                    rightButtons.add(Triple("音轨", "", { if (Store.settings.engine == 1) MpvEngine.cycleTrack("aid") else if (audioLabel.isNotEmpty()) cycleTrack(C.TRACK_TYPE_AUDIO) }))
                    rightButtons.add(Triple("视轨", "", { if (Store.settings.engine == 1) MpvEngine.cycleTrack("vid") else if (videoLabel.isNotEmpty()) cycleTrack(C.TRACK_TYPE_VIDEO) }))
                    rightButtons.add(Triple("指令", "", showUrl))
                    rightButtons.add(Triple("反转", if (flip) "开" else "", { flip = !flip }))
                    rightButtons.add(Triple("跨类", "", {
                        // 真功能（照参考项目「跨类」）：拿频道名/片名去聚合搜索，直接进搜索结果页选源
                        val xhWord = Session.channel?.name?.trim().orEmpty().ifEmpty { vod?.vodName.orEmpty() }
                        if (xhWord.isEmpty()) {
                            Toast.makeText(context, "没有可用于跨类搜索的名字", Toast.LENGTH_SHORT).show()
                        } else {
                            Session.searchKey = xhWord
                            Session.searchAuto = true
                            Session.searchRequest++
                            val xhIntent = android.content.Intent(context, com.xiaohetv.MainActivity::class.java)
                            xhIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            context.startActivity(xhIntent)
                            Toast.makeText(context, "跨类搜索：" + xhWord, Toast.LENGTH_SHORT).show()
                        }
                    }))
                    rightButtons.add(Triple("换源", "", { vm.nextSource() }))
                } else {
                    // 字幕：有字幕轨就给「字幕」按钮切换（无字幕轨时该按钮不出现，与参考项目一致）
                    // 照参考项目：字幕/音轨/视轨 只显示名字，不显示轨道数字与 "-"
                    rightButtons.add(Triple("字幕", "", { if (Store.settings.engine == 1) MpvEngine.cycleTrack("sid") else if (textLabel.isNotEmpty()) cycleTrack(C.TRACK_TYPE_TEXT) }))
                    rightButtons.add(Triple("音轨", "", { if (Store.settings.engine == 1) MpvEngine.cycleTrack("aid") else if (audioLabel.isNotEmpty()) cycleTrack(C.TRACK_TYPE_AUDIO) }))
                    rightButtons.add(Triple("视轨", "", { if (Store.settings.engine == 1) MpvEngine.cycleTrack("vid") else if (videoLabel.isNotEmpty()) cycleTrack(C.TRACK_TYPE_VIDEO) }))
                    // 片头/片尾：点一次按当前位置设定跳过点，再点一次清除
                    rightButtons.add(Triple("片头", if (introMs > 0) formatTime(introMs) else "", {
                        introMs = if (introMs > 0) 0L else position
                        introDone = false
                    }))
                    rightButtons.add(Triple("片尾", if (outroMs > 0) formatTime(outroMs) else "", {
                        outroMs = if (outroMs > 0) 0L else position
                    }))
                    rightButtons.add(Triple("指令", "", showUrl))
                }
                // 照参考项目：左组靠左、右组靠右，中间留空（不是全部左对齐）
                Row(
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        leftButtons.forEach { item -> BarButton(item.first, item.second, item.third) }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rightButtons.forEach { item -> BarButton(item.first, item.second, item.third) }
                    }
                }
            }

            // ---------- 配置（订阅）面板：所有已添加的直播订阅 ----------
            if (configSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { configSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .heightIn(max = 360.dp)
                            .background(Color(0xFFF8F2ED), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text = "配置", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(modifier = Modifier.height(8.dp))
                        val configs = ConfigStore.all(Config.LIVE)
                        if (configs.isEmpty()) {
                            Text(text = "还没有添加任何订阅（设置 → 订阅设置）", fontSize = 12.sp, color = Color(0xFF8A8A8E))
                        }
                        configs.forEach { cfg ->
                            PanelOption(
                                title = cfg.name.ifEmpty { "未命名订阅" },
                                desc = cfg.url,
                                selected = LiveConfig.config?.url == cfg.url
                            ) {
                                configSheet = false
                                configVM.select(cfg, siteVM) { ok, _ ->
                                    if (ok) scope.launch { liveVM.refresh(); liveVM.open() }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 综合直播面板：当前订阅下的直播源（lives） ----------
            if (liveSourceSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { liveSourceSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .heightIn(max = 360.dp)
                            .background(Color(0xFFF8F2ED), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(text = "综合直播", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveConfig.lives.forEach { item ->
                            PanelOption(
                                title = item.name.ifEmpty { "未命名" },
                                desc = "${item.groups.size} 个分组",
                                selected = LiveConfig.home?.name == item.name
                            ) {
                                liveSourceSheet = false
                                LiveConfig.home = item
                                Session.live = item
                                Session.channel = item.groups.firstOrNull()?.channel?.firstOrNull()
                                vm.start()
                            }
                        }
                    }
                }
            }

            // ---------- 播放引擎面板：EXO / MPV / 其它 ----------
            if (engineSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { engineSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .background(Color(0xFFF8F2ED), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(16.dp)
                    ) {
                        Text(text = "播放引擎", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row {
                            PanelChip("EXO", Store.settings.engine == 0) {
                                Store.settings.engine = 0
                                Store.saveSettings()
                                decoder++          // 触发重建播放器，切回 EXO
                                engineSheet = false
                                Toast.makeText(context, "已切换：EXO（Media3）", Toast.LENGTH_SHORT).show()
                            }
                            PanelChip("MPV", Store.settings.engine == 1) {
                                Store.settings.engine = 1
                                Store.saveSettings()
                                decoder++          // 触发重建：改用内置 MPV（libmpv.so）渲染
                                engineSheet = false
                                Toast.makeText(context, "已切换：MPV（内置 libmpv）", Toast.LENGTH_SHORT).show()
                            }
                            PanelChip("其它", false) {
                                engineSheet = false
                                Toast.makeText(context, "其它引擎（如 VLC）尚未接入", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // ---------- 解码设置面板：视频 / 音频 × 自动/硬件/软解/软件 ----------
            if (decodeSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { decodeSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .background(Color(0xFFF8F2ED), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(16.dp)
                    ) {
                        Text(text = "解码设置", fontSize = 16.sp, color = Color(0xFF1C1C1E))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            PanelTab("视频", decodeTab == 0, Modifier.weight(1f)) { decodeTab = 0 }
                            PanelTab("音频", decodeTab == 1, Modifier.weight(1f)) { decodeTab = 1 }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val current = if (decodeTab == 0) Store.settings.decodeVideo else Store.settings.decodeAudio
                        DECODE_MODES.forEachIndexed { index, mode ->
                            PanelOption(mode.first, mode.second, current == index) {
                                if (decodeTab == 0) {
                                    Store.settings.decodeVideo = index
                                    Store.settings.decode = if (index == 1) 1 else 0
                                } else {
                                    Store.settings.decodeAudio = index
                                }
                                Store.saveSettings()
                                decoder++
                                decodeSheet = false
                            }
                        }
                    }
                }
            }

            // 右侧竖排：旋转 / 锁定屏幕
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { rotateScreen(context) }) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "旋转", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { locked = true; controls = false }) {
                    Icon(Icons.Default.Lock, contentDescription = "锁定屏幕", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }

        // 加载/缓冲时显示实时速度 + 滑动时的快进快退提示
        if (seekHint.isNotEmpty()) {
            Text(
                text = seekHint,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else if (buffering || !playing) {
            // 直播 / 点播统一加载样式（照参考项目）：屏幕中央白色转圈 + 下面显示加载速度
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = speedText,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        if (locked) {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "解锁", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
            }
        }

        if (sheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { sheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color(0xFFF2F2F7), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        // 参考项目交互：选集面板展开后向下滑动即可关闭（原来只能靠点空白，视频区还会吃掉触摸）
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 24f) sheet = false
                            }
                        }
                        .padding(16.dp)
                ) {
                    Text(text = "选集（${episodes.size}）", fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(episodes.size) { index ->
                            val episode = episodes[index]
                            Text(
                                text = episode.name,
                                fontSize = 14.sp,
                                color = if (index == episodeIndex) IosBlue else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (index == episodeIndex) IosBlue.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        sheet = false
                                        switchEpisode(index)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (panel) {
            LeftChannelPanel(
                groups = panelGroups,
                groupIndex = panelGroupIndex,
                onGroupIndex = { panelGroupIndex = it },
                current = Session.channel,
                epg = liveEpg,
                onPick = { switchChannel(it) },
                onClose = { panel = false }
            )
        }
    }
}

/** 计算当前频道所在分组，用于打开左侧节目单时定位。 */
private fun openPanel(groups: List<com.xiaohetv.core.bean.Group>, apply: (Int) -> Unit) {
    val cur = Session.channel ?: return
    val idx = groups.indexOfFirst { g -> g.channel.any { it.name == cur.name } }
    apply(if (idx < 0) 0 else idx)
}

private fun liveSubtitle(groups: List<com.xiaohetv.core.bean.Group>): String {
    val cur = Session.channel ?: return "直播"
    val group = groups.firstOrNull { g -> g.channel.any { it.name == cur.name } }
    return group?.name ?: "直播"
}

@Composable
private fun PlayerTopBar(
    isLive: Boolean,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSheet: () -> Unit,
    onCast: () -> Unit,
    onInfo: () -> Unit,
    showSheet: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            if (title.isNotEmpty()) {
                Text(text = title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
            }
        }
        if (showSheet) {
            Text(
                text = "选集",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onSheet() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        IconButton(onClick = onCast) {
            Icon(Icons.Default.Cast, contentDescription = "投屏", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Default.Info, contentDescription = "信息", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** 解码设置的四个模式（照参考项目文案）。 */
internal val DECODE_MODES = listOf(
    "自动" to "由播放器自动选择解码器",
    "硬件" to "仅使用硬件 MediaCodec 解码",
    "软解" to "仅使用软件 MediaCodec 解码",
    "软件" to "仅使用软件解码器（不回退硬件）"
)

/** 面板里的胶囊选项（选中态高亮）。 */
@Composable
internal fun PanelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        color = if (selected) Color(0xFFC05A4A) else Color(0xFF1C1C1E),
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFF6DED8) else Color(0xFFEDE7E2))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

/** 面板里的分页标签。 */
@Composable
internal fun PanelTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 14.sp,
        color = Color(0xFF1C1C1E),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFFF6DED8) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    )
}

/** 面板里的整行选项（标题 + 说明，选中打勾）。 */
@Composable
internal fun PanelOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFF6DED8) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "✓" else "　",
            fontSize = 14.sp,
            color = Color(0xFFC05A4A),
            modifier = Modifier.padding(end = 6.dp)
        )
        Column {
            Text(text = title, fontSize = 14.sp, color = Color(0xFF1C1C1E))
            Text(text = desc, fontSize = 11.sp, color = Color(0xFF8A8A8E))
        }
    }
}

@Composable
private fun BarButton(label: String, value: String, onClick: () -> Unit, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 参考项目：底部菜单整体是白色系，不要蓝色（原值用了主题蓝，用户明确要求去掉）
        // 直播底栏的「跨类 / 换源」用高亮色（参考项目里是黄色）
        Text(text = label, color = if (highlight) Color(0xFFF5C542) else Color.White, fontSize = 13.sp)
        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = value, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** 左侧节目单：左侧分类栏 + 右侧频道列表（深色，对齐影视TV直播节目单）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeftChannelPanel(
    groups: List<com.xiaohetv.core.bean.Group>,
    groupIndex: Int,
    onGroupIndex: (Int) -> Unit,
    current: Channel?,
    epg: Map<String, List<com.xiaohetv.core.parser.EpgParser.Programme>>,
    onPick: (Channel) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(onClick = onClose, onLongClick = onClose)
    ) {
        val gIndex = groupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        val channels = groups.getOrNull(gIndex)?.channel ?: emptyList()
        // 宽度完全照参考项目 LiveActivity.setWidth：
        // 分组列 = 最长分组名 + 48dp，上限屏宽 1/4；频道列 = (台标56dp + 编号+名称) + 60dp，上限屏宽 1/2
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val groupWidth = remember(groups, density) {
            val widest = groups.maxOfOrNull {
                measurer.measure(AnnotatedString(it.name), TextStyle(fontSize = 14.sp)).size.width
            } ?: 0
            with(density) { (widest.toDp() + 48.dp).coerceAtMost(screenWidth / 4) }
        }
        val channelWidth = remember(channels, density) {
            val logoWidth = 56.dp
            val widest = channels.maxOfOrNull { ch ->
                val text = measurer.measure(
                    AnnotatedString(ch.number + ch.name),
                    TextStyle(fontSize = 14.sp)
                ).size.width
                with(density) { (if (ch.logo.isEmpty()) 0.dp else logoWidth) + text.toDp() }
            } ?: 0.dp
            (widest + 60.dp).coerceAtMost(screenWidth / 2)
        }
        val now = current?.let { epg[it.tvgId.ifEmpty { it.tvgName }] }?.firstOrNull()?.title
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(groupWidth + channelWidth)
                .align(Alignment.CenterStart)
                .background(Color(0xEB141414))
        ) {
            // 分组列：对齐参考项目 adapter_group.xml（单行居中、选中米色圆角底 + 深色字），
            // 宽度按最长分组名自适应，否则"综合直播"这类会被截成"综合…"
            LazyColumn(
                modifier = Modifier.width(groupWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(groups.size) { gi ->
                    val active = gi == gIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onGroupIndex(gi) }
                            .background(if (active) Color(0xFFE9DFCB) else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = groups[gi].name,
                            fontSize = 14.sp,
                            color = if (active) Color(0xFF4A3B26) else Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current?.name ?: "",
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!now.isNullOrEmpty()) {
                            Text(text = now, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        text = "关闭",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { onClose() }.padding(4.dp)
                    )
                }
                // 明示当前分组（分组名 + 该组频道数），避免看起来"没有分组"
                Text(
                    text = "${groups.getOrNull(gIndex)?.name.orEmpty()}（${channels.size}）",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channels) { ch ->
                        val active = ch.name == current?.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { onPick(ch) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 编号：3 位等宽数字（对齐参考项目 adapter_channel.xml 的 tnum + 001 格式）
                            Text(
                                text = ch.number,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.width(40.dp)
                            )
                            if (ch.logo.isNotEmpty()) {
                                // 台标 44×33（4:3，对齐 adapter_channel.xml）
                                AsyncImage(
                                    model = ch.logo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.width(44.dp).height(33.dp).padding(end = 10.dp)
                                )
                            }
                            Text(
                                text = ch.name,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (ch.urls.size > 1) {
                                Text(text = "源${ch.urls.size}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun saveProgress(instance: ExoPlayer) {
    if (Store.settings.incognito) return
    val site = Session.site ?: return
    val vod = Session.vod ?: return
    val flag = vod.flags().getOrNull(Session.flagIndex) ?: return
    val episode = flag.episodes.getOrNull(Session.episodeIndex) ?: return
    Store.addHistory(
        Store.History(
            siteKey = site.key,
            vodId = vod.vodId,
            name = vod.vodName,
            pic = vod.vodPic,
            remark = vod.vodRemarks,
            flag = flag.flag,
            episode = episode.name,
            url = episode.url,
            position = instance.currentPosition,
            duration = instance.duration,
            update = System.currentTimeMillis()
        )
    )
}

/** 播放页旋转：横屏 ↔ 竖屏。 */
private fun rotateScreen(context: Context) {
    val activity = context as? Activity ?: return
    activity.requestedOrientation = if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

private fun trackLabelFor(player: ExoPlayer?, type: Int): String {
    val groups = player?.currentTracks?.groups?.filter { it.type == type } ?: return ""
    if (groups.size <= 1) return ""
    val cur = groups.indexOfFirst { it.isSelected }
    return "${if (cur < 0) 1 else cur + 1}/${groups.size}"
}

/**
 * 播放格式判定，对齐参考项目 `player/media/MediaItemFactory`（`setMimeType(spec.getFormat())`）：
 * 优先用 spider 返回的 format，其次按地址推断。
 * 关键原因：很多点播源地址形如 `.../nby/m3u8/getM3u8?…`、`/proxy?do=m3u8&…`，**路径不以 .m3u8 结尾**，
 * 不显式给 MIME 时 ExoPlayer 会按 ProgressiveMediaSource 处理 → ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED（实测）。
 */
internal fun formatOf(info: PlayerVM.PlayInfo?): String {
    val declared = info?.format.orEmpty().trim()
    if (declared.isNotEmpty()) return declared
    val url = info?.url.orEmpty().lowercase()
    return when {
        url.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
        url.contains(".mpd") || url.contains("mpd?") || url.contains("/dash") -> MimeTypes.APPLICATION_MPD
        url.contains(".mp4") -> MimeTypes.VIDEO_MP4
        else -> ""
    }
}

/** 带格式的 MediaItem（对齐参考项目 MediaItemFactory.build()）。 */
internal fun mediaItemFor(info: PlayerVM.PlayInfo?): MediaItem {
    val builder = MediaItem.Builder().setUri(info?.url.orEmpty())
    val format = formatOf(info)
    if (format.isNotEmpty()) builder.setMimeType(format)
    return builder.build()
}

/** 参考项目 `player/exo/ExoUtil.getMimeType`：这些错误码说明"其实是 HLS"，可换 MIME 重试。 */
internal fun hlsFallbackMime(errorCode: Int): String? = when (errorCode) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> MimeTypes.APPLICATION_M3U8
    else -> null
}

/** nextlib FFmpeg 扩展是否可用（一次探测，失败即回落 MediaCodec，不影响播放）。 */
internal val FFMPEG_AVAILABLE: Boolean by lazy {
    runCatching {
        io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary.isAvailable()
    }.getOrDefault(false)
}

internal fun resizeMode(scale: Int): Int = when (scale) {
    3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    4 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

internal fun buildPlayer(context: Context, headers: Map<String, String> = emptyMap()): ExoPlayer {
    // 播放地址带的请求头优先（很多源需要 Referer/UA 才能出流）
    // 净化请求头：名字非法/值为空会让 OkHttp 直接抛异常，导致"怎么都播不出来"
    val props = (if (headers.isNotEmpty()) headers else Session.directHeaders)
        .filterKeys { it.isNotBlank() && it.none { ch -> ch <= ' ' } }
        .mapValues { it.value.trim() }
        .filterValues { it.isNotEmpty() }
    // UA 优先级：播放地址自带的 User-Agent（jar 指定）> 用户设置 > 默认 Chrome。
    // 必须这样做：media3 的 DefaultHttpDataSource.setUserAgent 是在应用请求头之后覆盖写入 UA 的，
    // 之前固定传 Chrome，会把 jar 给的 UA 冲掉 —— 实测瓜子「不卡」的 CDN 用 UA 做防盗链
    // （curl 验证：UA=Lavf/57.83.100 → 200 + 真 TS；UA=Chrome → 302 落地页），
    // 所以带着 Chrome UA 去取分片必然被 302，表现就是"瓜子永远播不了"。
    // 另外实测带 Referer 反而会被该 CDN 拒绝（302），所以不擅自补 Referer。
    val ua = props.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        ?: Store.settings.ua.ifEmpty { Util.CHROME }
    val dataSource = DefaultHttpDataSource.Factory()
        .setUserAgent(ua)
        .setAllowCrossProtocolRedirects(true)
    if (props.isNotEmpty()) dataSource.setDefaultRequestProperties(props)

    val buffer = Store.settings.buffer.coerceIn(1, 10)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            5000 + 5000 * buffer,
            20000 + 20000 * buffer,
            500 * buffer,
            1000 * buffer
        )
        .build()

    // 解码设置（照参考项目「解码设置」面板）：视频/音频可分别选 自动/硬件/软解/软件
    val decodeSelector = object : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean
        ): List<MediaCodecInfo> {
            val mode = if (mimeType.startsWith("audio/")) Store.settings.decodeAudio else Store.settings.decodeVideo
            val all = MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            return when (mode) {
                1 -> all.filter { !it.softwareOnly }.ifEmpty { all }   // 仅硬件
                2, 3 -> all.filter { it.softwareOnly }.ifEmpty { all } // 仅软件
                else -> all                                            // 自动
            }
        }
    }
    // 解码分派（与参考项目「解码设置」一致：自动 / 硬件 / 软解=FFmpeg / 软件=MediaCodec 软解）：
    //   软解 → nextlib 的 FFmpegOnlyRenderersFactory（真 FFmpeg 解码，H.265/AV1 等兼容性远好于 MediaCodec 软解）；
    //   自动 → nextlib 的 NextRenderersFactory（优选 FFmpeg，失败自动回落 MediaCodec）；
    //   硬件 / 软件 / 扩展不可用 → media3 默认工厂 + 我们自己的 MediaCodec 选择器。
    // FFmpeg 扩展不可用（libmedia3ext 加载失败或缺解码器）时一律回落，绝不至于"整个播不了"。
    val renderers: androidx.media3.exoplayer.RenderersFactory = when {
        Store.settings.decodeVideo == 2 && FFMPEG_AVAILABLE ->
            io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FFmpegOnlyRenderersFactory(context)
        Store.settings.decodeVideo == 0 && FFMPEG_AVAILABLE ->
            io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
        else -> DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(decodeSelector)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }

    // 播放器构建失败也要有完整异常链（不能只有"播不出来"）
    val player = try {
        ExoPlayer.Builder(context)
            // 数据源套一层取证包装：能看到播放器真实发出的每个媒体/分片请求
            .setMediaSourceFactory(DefaultMediaSourceFactory(TracedDataSourceFactory(dataSource)))
            .setRenderersFactory(renderers)
            .setLoadControl(loadControl)
            .build()
    } catch (t: Throwable) {
        VodTrace.fail(
            "player_init", t,
            extra = "engine=exoplayer decoder=${if (Store.settings.decode == 0) "软解" else "硬解"}" +
                " ua=${if (ua == Util.CHROME) "默认Chrome" else "自定义"}"
        )
        throw t
    }
    // 播放器状态变化 → 结构化点播日志（Player.Listener 为稳定 API）
    player.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_IDLE -> "idle"
                Player.STATE_BUFFERING -> "buffering"
                Player.STATE_READY -> "ready"
                Player.STATE_ENDED -> "ended"
                else -> "unknown($playbackState)"
            }
            VodTrace.stage("state", state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) VodTrace.stage("state", "playing")
        }

        override fun onPlayerError(error: PlaybackException) {
            VodTrace.fail("player", error, extra = "errorCode=${error.errorCodeName}")
        }
    })
    // 首帧渲染 = 真的出画面（只看到 HTTP 200 不等于播放成功）
    player.addAnalyticsListener(VodAnalytics())
    VodTrace.stage(
        "player_init", "ok",
        extra = "engine=exoplayer headers=${VodTrace.headerNames(props)}" +
            " ua=${if (ua == Util.CHROME) "默认Chrome" else "自定义"}" +
            " buffer=${Store.settings.buffer} decoder=${if (Store.settings.decode == 0) "软解" else "硬解"}"
    )
    return player
}

/**
 * 创建播放器视图（详情页内嵌与全屏播放页共用）。
 *
 * 必须用 `view_player.xml` 里 `surface_type="texture_view"` 的 PlayerView：
 * SurfaceView 在 Compose AndroidView 中会出现"有声音、黑屏、退出后才出图像"
 * （实测 first_frame 被压到 21 秒，退出触发布局重排才渲染）。
 */
/**
 * PlayerView 自己会消费触摸事件（内置控制器的显隐、双击等逻辑都挂在它身上），
 * 叠在它上层的 Compose 控件（返回 / 全屏按钮 / 双击全屏）就收不到点击 —— 这正是
 * "全屏按钮点击没反应"的另一半原因。我们完全不用它的内置控制器，所以直接不接管触摸。
 */
class NoTouchPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : PlayerView(context, attrs) {
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
}

internal fun createPlayerView(context: Context, instance: ExoPlayer, scale: Int): PlayerView {
    val view = android.view.LayoutInflater.from(context)
        .inflate(com.xiaohetv.R.layout.view_player, null) as PlayerView
    view.useController = false
    view.resizeMode = resizeMode(scale)
    view.player = instance
    return view
}

/** 播放器侧事件（首帧渲染 / 加载完成与取消 / 播放错误），写进点播结构化日志。 */
private class VodAnalytics : androidx.media3.exoplayer.analytics.AnalyticsListener {

    /** 真正出画面的证据（只看到"HTTP 200"不等于播放成功）。 */
    override fun onRenderedFirstFrame(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        VodTrace.stage("first_frame", "rendered", extra = "renderTimeMs=$renderTimeMs")
    }

    /** 一次媒体加载完成（播放列表/分片），bytesLoaded 是实际读到的字节。 */
    override fun onLoadCompleted(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
    ) {
        VodTrace.stage(
            "load", "completed",
            extra = "url=${VodTrace.mask(loadEventInfo.uri.toString())} bytesLoaded=${loadEventInfo.bytesLoaded}"
        )
    }

    override fun onPlayerError(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        error: PlaybackException
    ) {
        VodTrace.fail("player", error, extra = "errorCode=${error.errorCodeName}")
    }
}


/**
 * 软解码：优先选择系统软件解码器（c2.android.* / OMX.google.*），
 * 设备上没有软件解码器时回退默认解码器。
 */
private object SoftCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val soft = all.filter { it.softwareOnly }
        return if (soft.isNotEmpty()) soft else all
    }
}


/**
 * 按播放引擎分派 跳转 / 播放暂停（详情页内嵌播放器与播放页共用）。
 * MPV 模式下走 MpvEngine，否则走 ExoPlayer。
 */
internal fun engineSeek(player: ExoPlayer?, ms: Long) {
    if (Store.settings.engine == 1) MpvEngine.seekTo(ms) else player?.seekTo(ms)
}

internal fun engineTogglePlay(player: ExoPlayer?) {
    if (Store.settings.engine == 1) MpvEngine.pause(MpvEngine.playing)
    else player?.let { if (it.isPlaying) it.pause() else it.play() }
}