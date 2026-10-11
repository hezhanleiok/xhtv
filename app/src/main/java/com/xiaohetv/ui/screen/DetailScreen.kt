package com.xiaohetv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import com.xiaohetv.player.MpvEngine
import com.xiaohetv.player.Playback
import com.xiaohetv.ui.components.LoadingBox
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.vm.DetailVM
import com.xiaohetv.vm.PlayerVM
import com.xiaohetv.vm.SiteVM

/** 当前轨道名（底栏「字幕/音轨/视轨」用；没有该类型轨道时返回空 → 按钮不出现）。 */
private fun trackLabel(player: ExoPlayer?, type: Int, version: Int): String {
    val groups = player?.currentTracks?.groups?.filter { it.type == type } ?: return ""
    if (groups.isEmpty()) return ""
    val index = groups.indexOfFirst { it.isSelected }
    return if (index >= 0) "${index + 1}/${groups.size}" else "-"
}

/** 循环切到下一个轨道（与播放页交互一致）。 */
private fun cycleTrack(player: ExoPlayer?, type: Int) {
    val p = player ?: return
    val groups = p.currentTracks.groups.filter { it.type == type }
    if (groups.size <= 1) return
    val current = groups.indexOfFirst { it.isSelected }
    val next = if (current < 0) 0 else (current + 1) % groups.size
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setOverrideForType(
            androidx.media3.common.TrackSelectionOverride(groups[next].mediaTrackGroup, 0)
        )
        .setTrackTypeDisabled(type, false)
        .build()
}

/** 全屏底栏的菜单按钮（标签 + 当前值，与播放页底栏同一套视觉）。 */
@Composable
private fun FsButton(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp)
        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = value, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** 面板里的选项胶囊（选中=蓝底白字，与播放页引擎/解码面板一致）。 */
@Composable
private fun FsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) IosBlue else Color(0xFFE5E5EA))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = if (selected) Color.White else Color(0xFF1C1C1E), fontSize = 13.sp)
    }
}

/** 毫秒 → mm:ss / h:mm:ss */
private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val Bg = Color(0xFFF7F7F9)
private val TitleColor = Color(0xFF1C1C1E)
private val TextColor = Color(0xFF3A3A3C)
private val SubColor = Color(0xFF8A8A8E)
private val CellBg = Color(0xFFF2F2F7)
private val CellActiveBg = Color(0xFF007AFF)

/**
 * 详情页，照参考项目 fongmi 手机版 `activity_video.xml` 复刻：
 * 顶部 220dp 播放器（进入即自动播放）+ 下方详情（标题/备注/站源/年份地区类型/导演/演员/线路/选集/简介）。
 * 参考项目里详情页与播放页合一，点「选集」直接在本页切集。配色跟随 App 浅色主题。
 */
@Composable
fun DetailScreen(nav: NavHostController, sites: SiteVM) {
    val vm: DetailVM = viewModel()
    val playVM: PlayerVM = viewModel()
    val context = LocalContext.current
    val site = Session.site
    val initial = Session.vod

    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val detail by vm.vod.collectAsState()
    val info by playVM.info.collectAsState()
    val playLoading by playVM.loading.collectAsState()
    val playError by playVM.error.collectAsState()

    val vod = detail ?: initial
    var flagIndex by remember { mutableIntStateOf(Session.flagIndex) }
    var episodeIndex by remember { mutableIntStateOf(Session.episodeIndex) }
    var reversed by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    // 全屏＝本页布局切换（同一个 ExoPlayer 实例，不重建播放器）→ 切换不缓冲、画面不中断
    var fullscreen by remember { mutableStateOf(false) }
    var fsPosition by remember { mutableLongStateOf(0L) }
    var fsDuration by remember { mutableLongStateOf(0L) }
    var fsPlaying by remember { mutableStateOf(false) }
    var fsDrag by remember { mutableStateOf(false) }
    var fsDragPos by remember { mutableLongStateOf(0L) }
    var fsIntro by remember { mutableLongStateOf(0L) }
    var fsOutro by remember { mutableLongStateOf(0L) }
    var trackVersion by remember { mutableIntStateOf(0) }
    // 全屏底栏：EXO（播放引擎）与 解码（解码设置）两个独立按钮的面板
    var fsEngineSheet by remember { mutableStateOf(false) }
    var fsDecodeSheet by remember { mutableStateOf(false) }
    var fsDecodeTab by remember { mutableIntStateOf(0) }
    var fsSeekHint by remember { mutableStateOf("") }
    var fsBytesLoaded by remember { mutableLongStateOf(0L) }
    var fsSpeedText by remember { mutableStateOf("") }
    var fsLastBytes by remember { mutableLongStateOf(0L) }
    // 全屏控制层：默认不显示，点一下才出现，4s 后自动隐藏（照参考项目，不是常显）
    var fsControls by remember { mutableStateOf(false) }
    // 切换解码方式需要重建播放器（保持当前进度），这里用自增触发
    var playerRebuild by remember { mutableIntStateOf(0) }
    // 全屏底栏菜单状态（照点播底栏语义，全部作用在同一个 ExoPlayer 上）
    var fsScale by remember { mutableIntStateOf(0) }
    var fsSpeed by remember { mutableStateOf(1f) }
    var fsLoop by remember { mutableStateOf(false) }
    var fsMenu by remember { mutableStateOf(false) }
    var collected by remember(vod?.vodId) {
        mutableStateOf(if (vod != null) Store.isCollect(site?.key.orEmpty(), vod.vodId) else false)
    }
    val scale = Store.settings.scale

    val flags = vod?.flags() ?: emptyList()
    val flag = flags.getOrNull(flagIndex)
    val episodes = flag?.episodes ?: emptyList()
    val ordered = remember(episodes, reversed) {
        if (reversed) episodes.indices.reversed().toList() else episodes.indices.toList()
    }

    fun start(index: Int) {
        if (index < 0 || index >= episodes.size) return
        episodeIndex = index
        Session.flagIndex = flagIndex
        Session.episodeIndex = index
        playVM.start()
    }

    LaunchedEffect(initial?.vodId) {
        val target = site ?: return@LaunchedEffect
        val item = initial ?: return@LaunchedEffect
        vm.load(target, item.vodId)
        // 续播：命中历史记录时定位到上次的线路与集数
        if (Store.settings.resume && !Store.settings.incognito) {
            val list = item.flags()
            Store.findHistory(target.key, item.vodId)?.let { history ->
                val f = list.indexOfFirst { it.flag == history.flag }
                if (f >= 0) {
                    flagIndex = f
                    Session.flagIndex = f
                    val e = list[f].episodes.indexOfFirst { it.name == history.episode }
                    if (e >= 0) {
                        episodeIndex = e
                        Session.episodeIndex = e
                    }
                }
            }
        }
        // 不在这里 start：此刻 Session.vod 可能还是残缺的（没有播放源），
        // 等详情写回 Session 后由下面的 flags 监听统一起播。
    }

    DisposableEffect(Unit) {
        onDispose { player?.let { saveProgress(it) } }
    }

    // 全屏态采样播放进度（内嵌播放器没有 Compose 状态，只能定时读）
    LaunchedEffect(player) {
        // 挂上真实加载字节统计（用于"开始播放前的加载速度"）。
        // 注意：不能只在全屏分支挂 —— 竖屏小窗也必须能看到转圈 + KB/s。
        player?.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
            ) {
                fsBytesLoaded += loadEventInfo.bytesLoaded
            }
        })
        while (player != null || Store.settings.engine == 1) {
            fsPosition = if (Store.settings.engine == 1) MpvEngine.position else (player?.currentPosition ?: 0L)
            fsDuration = if (Store.settings.engine == 1) MpvEngine.duration else (player?.duration ?: 0L).coerceAtLeast(0L)
            fsPlaying = if (Store.settings.engine == 1) MpvEngine.playing else player?.isPlaying == true
            // 每秒算一次加载速度（KB/s），用于"开始播放前"的加载提示
            val deltaBytes = fsBytesLoaded - fsLastBytes
            fsLastBytes = fsBytesLoaded
            fsSpeedText = if (deltaBytes > 1024) "${deltaBytes / 1024} KB/s" else ""
            // 片头（一集只跳一次）/ 片尾（到点自动下一集）
            if (fsIntro > 0 && fsPosition in 0 until fsIntro) engineSeek(player, fsIntro)
            if (fsOutro > 0 && fsPosition >= fsOutro && episodeIndex + 1 < episodes.size) {
                fsOutro = 0L
                start(episodeIndex + 1)
            }
            kotlinx.coroutines.delay(500)
        }
    }

    // 进全屏转横屏；退出全屏回竖屏（返回后是"竖屏小窗播放"），离开本页再复位
    val hostActivity = context as? android.app.Activity
    DisposableEffect(fullscreen) {
        hostActivity?.requestedOrientation = if (fullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { }
    }

    // 全屏控制层自动隐藏（4s）；打开选集面板时保持显示
    LaunchedEffect(fsControls, fsMenu) {
        if (fsControls && !fsMenu) {
            kotlinx.coroutines.delay(4000)
            fsControls = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            hostActivity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 全屏态按系统返回：只退出全屏，退回"横屏播放"这一层（详情页，播放不中断）；
    // 再按一次返回才离开详情页回搜索结果。上一版直接 popBackStack 跳过了这一层，是错的。
    androidx.activity.compose.BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    // 从首页 / 历史进入时 Session.vod 还没带播放源，等详情加载完补一次解析
    var retried by remember(vod?.vodId) { mutableStateOf(false) }
    LaunchedEffect(flags.size, episodeIndex) {
        // 注意：不能用 playLoading 做条件 —— PlayerVM.loading 初值就是 true，
        // 之前因此永远不满足条件，导致详情页永不自动起播（"点播无法播放"）。
        if (!retried && flags.isNotEmpty() && info == null) {
            retried = true
            Session.flagIndex = flagIndex
            Session.episodeIndex = episodeIndex
            playVM.start()
        }
    }

    // 详情页播放失败（站点没有这部片 / 详情为空 → 没有选集）→ 自动换到其它站点的同名影片。
    // 用户实际就是在详情页踩到的：内嵌播放器报"没有选集"，而播放页那套自动换源根本不会执行。
    LaunchedEffect(playError) {
        if (playError.isEmpty()) return@LaunchedEffect
        android.widget.Toast.makeText(context, "当前站点放不了，正在尝试其它站点…", android.widget.Toast.LENGTH_SHORT).show()
        if (playVM.switchSameVodSite()) {
            // 换站成功：详情也换成新站点的，保证标题/线路/选集一致
            flagIndex = 0
            episodeIndex = 0
            Session.site?.let { s -> Session.vod?.let { v -> vm.load(s, v.vodId) } }
        }
    }

    // 进全屏播放页 / 退到后台时暂停内嵌播放器，避免两个播放器同时出声
    val owner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(owner, player) {
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> player?.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> player?.play()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (vod == null) {
        Box(modifier = Modifier.fillMaxSize().background(Bg)) { LoadingBox() }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---------- 顶部播放器（照 activity_video.xml 的 220dp video 区）----------
            // 全屏：只切换本页布局 + 转屏，同一个播放器继续播
            // （参考项目就是这样：不重建播放器 → 不缓冲、画面不中断、只换方向）
            val toFullscreen: () -> Unit = {
                Session.flagIndex = flagIndex
                Session.episodeIndex = episodeIndex
                Session.site = site
                Session.vod = vod
                fullscreen = true
            }
            // 照 activity_video.xml：播放器撑满宽度、按 16:9 高度；全屏时铺满整屏（同一个播放器）
            // 横屏（退出全屏后的"横屏播放"这一层）必须给播放器限高，否则 16:9 会占满整个横屏，
            // 看起来和全屏一模一样 → 用户以为"返回没生效"。限高后下方详情露出来，两层可区分。
            val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
            Box(
                modifier = (
                    if (fullscreen) Modifier.fillMaxSize()
                    else if (landscape) Modifier.fillMaxWidth().fillMaxHeight(0.62f)
                    else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                    .background(Color.Black)
                    // 参考项目行为：单击唤出/收起控制层；双击直接进/出全屏
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (fullscreen) fsControls = !fsControls },
                            onDoubleTap = { fullscreen = !fullscreen }
                        )
                    }
                    // 全屏：左右滑动快进/快退（右滑快进、左滑快退；整屏宽 = 120 秒，松手才 seek）
                    .pointerInput(fullscreen) {
                        if (!fullscreen) return@pointerInput
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = {
                                if (acc != 0f && fsDuration > 0) {
                                    val target = fsPosition + (acc / size.width * 120_000f).toLong()
                                    engineSeek(player, target.coerceIn(0L, fsDuration))
                                }
                                fsSeekHint = ""
                                acc = 0f
                            },
                            onDragCancel = { acc = 0f; fsSeekHint = "" }
                        ) { _, dx ->
                            acc += dx
                            val target = (fsPosition + (acc / size.width * 120_000f).toLong())
                                .coerceIn(0L, fsDuration.coerceAtLeast(0L))
                            fsSeekHint = (if (acc >= 0) "快进 " else "快退 ") + fmtTime(target)
                        }
                    }
            ) {
                InlinePlayer(
                    info = info,
                    scale = if (fullscreen) fsScale else scale,
                    version = playerRebuild,
                    onPlayer = { player = it },
                    onEnded = { if (episodeIndex + 1 < episodes.size) start(episodeIndex + 1) },
                    modifier = Modifier.fillMaxSize()
                )
                if (playLoading || info == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "解析播放地址…", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                if (playError.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = playError, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
                    }
                }
                // 地址已解析但画面还没出来：白色转圈 + 实时加载速度（照参考项目）
                if (!playLoading && info != null && playError.isEmpty() && !fsPlaying && fsPosition == 0L) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                            if (fsSpeedText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = fsSpeedText, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { nav.popBackStack() },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                if (!fullscreen) {
                    // 全屏按钮放在播放器右下角（播放器控件的位置）
                    IconButton(
                        onClick = toFullscreen,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "全屏播放", tint = Color.White)
                    }
                } else {
                    // 全屏态：右上角退出 + 底部控制条（细轨进度条，可拖动/点击快进快退）
                    if (fsControls) IconButton(
                        onClick = { fullscreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
                    }
                    // 全屏底栏菜单：选集 / 线路 / 倍速 / 比例 / 循环 / 重播 / 刷新 / 退出全屏
                    // （之前全屏态只做了进度条+播放+下一集，所以"底部没有任何菜单"）
                    // 顺序与内容严格照参考项目点播底栏：
                    //   选集 · 解码(EXO) · 刷新 · 重播 · 循环 · 倍速 · 比例   ‖   字幕 · 音轨 · 视轨 · 片头 · 片尾 · 指令
                    // 不再自作主张加「线路 / 退出全屏」（退出全屏用右上角按钮或系统返回）
                    val audioTrack = trackLabel(player, androidx.media3.common.C.TRACK_TYPE_AUDIO, trackVersion)
                    val textTrack = trackLabel(player, androidx.media3.common.C.TRACK_TYPE_TEXT, trackVersion)
                    val videoTrack = trackLabel(player, androidx.media3.common.C.TRACK_TYPE_VIDEO, trackVersion)
                    if (fsControls) Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        // 两组各自按内容宽度排布、无法被挤压换行 → 高度天然一致（不再用会挤压的 weight 分栏）
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f, fill = false)) {
                        FsButton("选集", "", { fsMenu = true })
                        // 照参考项目：EXO（播放引擎）与 解码（解码设置）是两个独立按钮，且不显示集数等自加数字
                        FsButton(Store.engineText(), "", { fsEngineSheet = true })
                        FsButton("解码", Store.decodeModeText(Store.settings.decodeVideo), { fsDecodeSheet = true })
                        FsButton("刷新", "", { if (Store.settings.engine == 1) MpvEngine.reload() else player?.let { it.stop(); it.prepare(); it.playWhenReady = true } })
                        FsButton("重播", "", { engineSeek(player, 0L); if (Store.settings.engine == 1) MpvEngine.pause(false) else player?.play() })
                        FsButton("循环", if (fsLoop) "单集" else "", {
                            fsLoop = !fsLoop
                            if (Store.settings.engine == 1) MpvEngine.setLoop(fsLoop) else player?.repeatMode =
                                if (fsLoop) androidx.media3.common.Player.REPEAT_MODE_ONE
                                else androidx.media3.common.Player.REPEAT_MODE_OFF
                        })
                        FsButton("倍速", "${fsSpeed}x", {
                            fsSpeed = when (fsSpeed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
                            if (Store.settings.engine == 1) MpvEngine.setSpeed(fsSpeed) else player?.setPlaybackSpeed(fsSpeed)
                        })
                        FsButton(
                            "比例",
                            when (fsScale) { 3 -> "拉伸"; 4 -> "裁剪"; else -> "原始" },
                            { fsScale = when (fsScale) { 0 -> 3; 3 -> 4; else -> 0 } }
                        )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        // 照参考项目：字幕/音轨/视轨 只显示名字，不带数字、不带 "-"
                        FsButton("字幕", "", {
                            if (Store.settings.engine == 1 || textTrack.isNotEmpty()) {
                                if (Store.settings.engine == 1) MpvEngine.cycleTrack("sid") else cycleTrack(player, androidx.media3.common.C.TRACK_TYPE_TEXT)
                                trackVersion++
                            }
                        })
                        FsButton("音轨", "", {
                            if (Store.settings.engine == 1 || audioTrack.isNotEmpty()) {
                                if (Store.settings.engine == 1) MpvEngine.cycleTrack("aid") else cycleTrack(player, androidx.media3.common.C.TRACK_TYPE_AUDIO)
                                trackVersion++
                            }
                        })
                        FsButton("视轨", "", {
                            if (Store.settings.engine == 1 || videoTrack.isNotEmpty()) {
                                if (Store.settings.engine == 1) MpvEngine.cycleTrack("vid") else cycleTrack(player, androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                trackVersion++
                            }
                        })
                        FsButton("片头", if (fsIntro > 0) fmtTime(fsIntro) else "", {
                            fsIntro = if (fsIntro > 0) 0L else fsPosition
                        })
                        FsButton("片尾", if (fsOutro > 0) fmtTime(fsOutro) else "", {
                            fsOutro = if (fsOutro > 0) 0L else fsPosition
                        })
                        FsButton("指令", "", {
                            val url = player?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
                            android.widget.Toast.makeText(
                                context,
                                url.ifEmpty { "没有播放地址" },
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        })
                        }
                    }
                    // 播放引擎面板：EXO / MPV。
                    // 注意：原实现只把 fsEngineSheet 置 true、却没有渲染面板 → 用户看到"点了没反应"。
                    if (fsEngineSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { fsEngineSheet = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .background(Color(0xFFF2F2F7), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .padding(16.dp)
                            ) {
                                Text(text = "播放引擎", fontSize = 14.sp, color = TitleColor)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FsChip("EXO", Store.settings.engine == 0) {
                                        Store.settings.engine = 0
                                        Store.saveSettings()
                                        fsEngineSheet = false
                                        playerRebuild++   // 触发重建：同一页直接 MPV ⇄ EXO，不用回直播页切
                                        android.widget.Toast.makeText(context, "已切换：EXO（Media3）", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    FsChip("MPV", Store.settings.engine == 1) {
                                        Store.settings.engine = 1
                                        Store.saveSettings()
                                        fsEngineSheet = false
                                        playerRebuild++
                                        android.widget.Toast.makeText(context, "已切换：MPV（内置 libmpv）", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }

                    // 解码设置面板：视频 / 音频 × 自动/硬件/软解/软件（原实现同样只有标志没有 UI）
                    if (fsDecodeSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { fsDecodeSheet = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .background(Color(0xFFF2F2F7), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FsChip("视频", fsDecodeTab == 0) { fsDecodeTab = 0 }
                                    FsChip("音频", fsDecodeTab == 1) { fsDecodeTab = 1 }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("自动", "硬件", "软解", "软件").forEachIndexed { index, label ->
                                        val current = if (fsDecodeTab == 0) Store.settings.decodeVideo else Store.settings.decodeAudio
                                        FsChip(label, current == index) {
                                            if (fsDecodeTab == 0) Store.settings.decodeVideo = index
                                            else Store.settings.decodeAudio = index
                                            Store.saveSettings()
                                            fsDecodeSheet = false
                                            playerRebuild++   // 解码方式变了要重建播放器
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 选集 / 线路 面板
                    if (fsMenu) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { fsMenu = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .background(Color(0xFFF2F2F7), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .padding(16.dp)
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "线路", fontSize = 14.sp, color = TitleColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    flags.forEachIndexed { index, item ->
                                        Text(
                                            text = item.flag,
                                            fontSize = 13.sp,
                                            color = if (index == flagIndex) IosBlue else TextColor,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (index == flagIndex) IosBlue.copy(alpha = 0.12f) else Color.Transparent)
                                                .clickable {
                                                    fsMenu = false
                                                    flagIndex = index
                                                    Session.flagIndex = index
                                                    start(0)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = "选集（${episodes.size}）", fontSize = 14.sp, color = TitleColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                episodes.forEachIndexed { index, episode ->
                                    Text(
                                        text = episode.name,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (index == episodeIndex) IosBlue else TitleColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (index == episodeIndex) IosBlue.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable { fsMenu = false; start(index) }
                                            .padding(horizontal = 8.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }
                    val total = fsDuration.coerceAtLeast(1L)
                    val now = if (fsDrag) fsDragPos else fsPosition
                    val frac = (now.toFloat() / total).coerceIn(0f, 1f)
                    if (fsControls) Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = fmtTime(now), color = Color.White, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .padding(horizontal = 8.dp)
                                .pointerInput(total) {
                                    detectTapGestures { at ->
                                        if (fsDuration > 0) engineSeek(player, (at.x / size.width * total).toLong())
                                    }
                                }
                                .pointerInput(total) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { fsDrag = true; fsDragPos = fsPosition },
                                        onDragEnd = { engineSeek(player, fsDragPos); fsDrag = false },
                                        onDragCancel = { fsDrag = false }
                                    ) { change, _ ->
                                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                        fsDragPos = (x / size.width * total).toLong()
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
                        Text(text = fmtTime(fsDuration), color = Color.White, fontSize = 12.sp)
                        IconButton(onClick = { if (Store.settings.engine == 1) MpvEngine.pause(MpvEngine.playing) else if (fsPlaying) player?.pause() else player?.play() }) {
                            Icon(
                                if (fsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放/暂停",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { if (episodeIndex + 1 < episodes.size) start(episodeIndex + 1) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "下一集", tint = Color.White)
                        }
                    }
                }
            }

            // ---------- 详情（顺序与字段照 activity_video.xml）----------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vod.vodName,
                        color = TitleColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 收藏从播放器顶栏挪到这里：详情标题行右侧，不再和全屏一起堆在播放器上
                    IconButton(onClick = {
                        val item = Store.Collect(
                            site?.key.orEmpty(),
                            vod.vodId,
                            vod.vodName,
                            vod.vodPic,
                            vod.vodRemarks,
                            System.currentTimeMillis()
                        )
                        collected = Store.toggleCollect(item)
                    }) {
                        Icon(
                            if (collected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (collected) Color(0xFFFF3B30) else SubColor
                        )
                    }
                }
                if (vod.vodRemarks.isNotBlank()) {
                    DetailLine(text = vod.vodRemarks, top = 8.dp, color = SubColor)
                }
                DetailLine(text = "站源：${site?.name.orEmpty()}", top = 8.dp)

                val other = buildList {
                    if (vod.vodYear.isNotBlank()) add("年份：${vod.vodYear}")
                    if (vod.vodArea.isNotBlank()) add("地区：${vod.vodArea}")
                    if (vod.typeName.isNotBlank()) add("类型：${vod.typeName}")
                }.joinToString(" ")
                if (other.isNotEmpty()) DetailLine(text = other, top = 8.dp)
                if (vod.vodDirector.isNotBlank()) DetailLine(text = "导演：${vod.vodDirector}", top = 8.dp)
                if (vod.vodActor.isNotBlank()) {
                    DetailLine(text = "演员：${vod.vodActor}", top = 8.dp, maxLines = 1)
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                if (error.isNotEmpty()) {
                    Text(text = error, color = Color(0xFFFF3B30), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                }

                // 线路
                if (flags.isNotEmpty()) {
                    SectionTitle("线路")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(flags.size) { index ->
                            Cell(
                                text = flags[index].flag,
                                selected = index == flagIndex,
                                corner = 8.dp,
                                onClick = {
                                    flagIndex = index
                                    Session.flagIndex = index
                                    start(0)
                                }
                            )
                        }
                    }
                }

                // 选集
                if (episodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "选集", color = TitleColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (episodes.size >= 2) {
                            IconButton(onClick = { reversed = !reversed }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "倒序", tint = TextColor)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // 展开图标 → 弹出分页选集面板（照参考项目 dialog_episode_grid）
                        IconButton(onClick = { showGrid = true }) {
                            Icon(Icons.Default.UnfoldMore, contentDescription = "展开选集", tint = TextColor)
                        }
                    }
                    // 默认只有一行，左右滑动选择
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(ordered.size) { position ->
                            val real = ordered[position]
                            Cell(
                                text = episodes[real].name,
                                selected = real == episodeIndex,
                                corner = 8.dp,
                                onClick = { start(real) }
                            )
                        }
                    }
                }

                // 简介
                if (vod.vodContent.isNotBlank()) {
                    SectionTitle("简介")
                    Text(
                        text = vod.vodContent.replace(Regex("<[^>]*>"), "").trim(),
                        color = TextColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp).clickable { expanded = !expanded }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showGrid) {
        EpisodeGridDialog(
            names = episodes.map { it.name },
            selected = episodeIndex,
            onPick = { start(it); showGrid = false },
            onClose = { showGrid = false }
        )
    }
}

/**
 * 选集面板：顶部分页标签（每页 30 集）+ 3 列网格，照参考项目 `dialog_episode_grid.xml`。
 */
@Composable
private fun EpisodeGridDialog(
    names: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit
) {
    val pageSize = 30
    val pages = remember(names) { (names.size + pageSize - 1) / pageSize }
    var tab by remember(selected) { mutableIntStateOf(if (selected >= 0) selected / pageSize else 0) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xFFFDFBF7))
                    .padding(top = 16.dp, bottom = 10.dp)
            ) {
                Text(
                    text = "选集",
                    color = TitleColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(pages) { index ->
                        val active = index == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { tab = index }
                        ) {
                            Text(
                                text = "${index * pageSize + 1} - ${minOf((index + 1) * pageSize, names.size)}",
                                fontSize = 15.sp,
                                color = if (active) CellActiveBg else SubColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .height(2.dp)
                                    .background(if (active) CellActiveBg else Color.Transparent)
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f, fill = false).padding(top = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val first = tab * pageSize
                    val size = (minOf(first + pageSize, names.size) - first).coerceAtLeast(0)
                    items(size) { offset ->
                        val real = first + offset
                        Cell(
                            text = names[real],
                            selected = real == selected,
                            corner = 8.dp,
                            fill = true,
                            outline = true,
                            onClick = { onPick(real) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TitleColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun DetailLine(
    text: String,
    top: Dp,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = TextColor
) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = top)
    )
}

/** 线路 / 选集 通用格子：浅灰圆角，选中用主题蓝。 */
@Composable
private fun Cell(
    text: String,
    selected: Boolean,
    corner: Dp,
    fill: Boolean = false,
    outline: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = when {
            selected && outline -> CellActiveBg
            selected -> Color.White
            else -> TextColor
        },
        modifier = Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(if (selected && !outline) CellActiveBg else Color.White)
            .border(
                1.dp,
                if (selected) CellActiveBg else Color(0x22000000),
                RoundedCornerShape(corner)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
