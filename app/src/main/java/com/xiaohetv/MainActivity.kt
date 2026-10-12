package com.xiaohetv

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaohetv.data.Store
import com.xiaohetv.player.Playback
import com.xiaohetv.ui.Route
import com.xiaohetv.ui.screen.CollectScreen
import com.xiaohetv.ui.screen.DanmakuSettingScreen
import com.xiaohetv.ui.screen.DetailScreen
import com.xiaohetv.ui.screen.HistoryScreen
import com.xiaohetv.ui.screen.HomeScreen
import com.xiaohetv.ui.screen.LogScreen
import com.xiaohetv.ui.screen.LiveEntryScreen
import com.xiaohetv.ui.screen.MineScreen
import com.xiaohetv.ui.screen.PlayerSettingScreen
import com.xiaohetv.ui.screen.SearchScreen
import com.xiaohetv.ui.screen.SettingsScreen
import com.xiaohetv.ui.theme.XiaoheTheme
import com.xiaohetv.vm.ConfigVM
import com.xiaohetv.vm.SiteVM

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            XiaoheTheme {
                // 顺序：先申请权限 → 关掉之后才弹欢迎说明（两者不再同时出现）。
                // 用显式状态传递，保证权限弹窗关闭时能立刻触发欢迎弹窗（不依赖普通字段的重组时机）。
                var permDone by remember { mutableStateOf(Store.settings.permAsked) }
                PermissionGate(onDone = { permDone = true })
                WelcomeGate(enabled = permDone)
                // 外部分享/打开链接进来的磁力：统一在这里解析并起播
                MagnetGate()
                AppNav()
            }
        }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 回到 App 时恢复竖屏（直播/播放器的横屏由各自页面请求）。
        // 放在这里而不是直播页的 onDispose，否则打开播放器的一瞬间会闪一下竖屏。
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 支持被外部 App 分享文本 / 打开链接唤起播放。 */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        runCatching {
            val text = when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_VIEW -> intent.dataString
                else -> null
            } ?: return
            val url = extractUrl(text)
            if (url.isEmpty()) return
            // 磁力/ed2k/thunder 不能直接当媒体地址交给播放器（以前就是直接 direct：
            // 播放器既不认识 magnet:?xt=… 这个形式，也没有先解析出种子里的文件 →
            // 结果就是"没有速度、播不了"）。改成交给 MagnetGate 走迅雷解析流程：
            // 解析出文件列表 → 单个直接播、多个弹选择框。
            if (com.xiaohetv.player.Thunder.match(url)) {
                com.xiaohetv.data.Session.pendingLink = url
                Toast.makeText(this, "正在解析磁力链接…", Toast.LENGTH_SHORT).show()
            } else {
                Playback.direct(this, url)
            }
        }
    }

    private fun extractUrl(text: String): String {
        val regex = Regex("(https?|thunder|magnet|ed2k|content|file):\\S+")
        return regex.find(text)?.value ?: text.trim()
    }
}

/**
 * 首次启动的欢迎 / 使用说明（只弹一次，确认后才进权限申请）。
 * 说明三件事：① 本软件只是播放器，不提供任何影视订阅；② 需要自己添加配置；③ 合规免责。
 */
@Composable
fun WelcomeGate(enabled: Boolean = true) {
    var show by remember { mutableStateOf(!Store.settings.welcomeDone) }
    // 权限弹窗还开着时不弹（由外层传 enabled 精确控制顺序）
    if (!show || !enabled) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("xiaohe影视 欢迎你") },
        text = {
            Text(
                text = """
                    · 本软件只是播放器，不提供任何影视订阅，请自行添加配置。
                    · 添加配置：右下角「+」，或首页右上「更多」→「订阅设置」。
                    · 播放器：单击出控制条，双击播放/暂停，横滑快进快退，上滑下一集。
                    · 仅供个人学习研究使用，内容来自第三方，请确保来源合法。
                """.trimIndent(),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Store.settings.welcomeDone = true
                Store.saveSettings()
                show = false
            }) { Text("开始使用") }
        }
    )
}

/**
 * 磁力/ed2k/thunder 链接的统一入口（外部 Intent 或首页「+」都可走这里）。
 *
 * 为什么必须单独有这一层：磁力地址不是媒体地址，必须先交给迅雷核心解析出种子里的文件，
 * 再由用户选一个、用 `Playback.torrent()` 起播；直接丢给播放器只会"没有速度、播不了"。
 */
@Composable
fun MagnetGate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<com.xiaohetv.core.bean.Episode>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    val link = com.xiaohetv.data.Session.pendingLink
    LaunchedEffect(link) {
        if (link.isBlank()) return@LaunchedEffect
        com.xiaohetv.data.Session.pendingLink = ""
        if (!com.xiaohetv.player.Thunder.isAvailable()) {
            Toast.makeText(context, "当前设备不支持磁力引擎", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        val outcome = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.xiaohetv.player.Thunder.parse(link)
            }
        }
        val files = outcome.getOrNull().orEmpty()
        when {
            outcome.isFailure -> Toast.makeText(
                context,
                "磁力解析失败：${outcome.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()

            files.isEmpty() -> Toast.makeText(context, "没有解析到可播放文件", Toast.LENGTH_LONG).show()

            files.size == 1 -> Playback.torrent(context, files[0].url, files[0].name)

            else -> {
                items = files
                picking = true
            }
        }
    }
    if (picking) {
        com.xiaohetv.ui.screen.MultiPickDialog(
            title = "选择要播放的文件",
            items = items.map { "${it.name}  (${it.desc})" },
            onDismiss = { picking = false },
            onPick = { index ->
                picking = false
                items.getOrNull(index)?.let { Playback.torrent(context, it.url, it.name) }
            }
        )
    }
}

/** 首次启动申请必要权限（网络为系统默认，不需申请）。 */
@Composable
fun PermissionGate(onDone: () -> Unit = {}) {
    var show by remember { mutableStateOf(!Store.settings.permAsked) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Store.settings.permAsked = true
        Store.saveSettings()
        show = false
        onDone()          // 通知外层：权限环节结束，可以弹欢迎说明了
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("需要以下权限") },
        text = {
            Text(
                text = """
                    · 通知：后台播放状态、解析结果提示（可拒绝，不影响播放）
                    · 媒体/存储：播放本机视频音频、导入本地配置文件（可拒绝，不影响在线播放）

                    网络权限为系统默认，无需申请。
                    所有数据仅保存在本机，不会上传。
                """.trimIndent(),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { launcher.launch(requiredPermissions()) }) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = {
                Store.settings.permAsked = true
                Store.saveSettings()
                show = false
                onDone()
            }) { Text("稍后") }
        }
    )
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
        list.add(Manifest.permission.READ_MEDIA_VIDEO)
        list.add(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return list.toTypedArray()
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val sites: SiteVM = viewModel()
    val config: ConfigVM = viewModel()

    LaunchedEffect(Unit) {
        // boot 内部已含：缓存恢复 → 刷新站点 → 自动联网刷新
        config.boot(sites)
    }

    // 播放页「跨类」：把请求带到搜索页（Session.searchRequest 自增即触发）
    LaunchedEffect(com.xiaohetv.data.Session.searchRequest) {
        if (com.xiaohetv.data.Session.searchRequest > 0) nav.navigate(Route.Search)
    }

    NavHost(navController = nav, startDestination = Route.Home) {
        composable(Route.Home) { HomeScreen(nav, sites, config) }
        composable(Route.Search) { SearchScreen(nav, sites) }
        composable(Route.Detail) { DetailScreen(nav, sites) }
        composable(Route.Live) { LiveEntryScreen(nav) }
        composable(Route.Mine) { MineScreen(nav) }
        composable(Route.Settings) { SettingsScreen(nav, sites, config) }
        composable(Route.Log) { LogScreen(nav) }
        composable(Route.PlayerSetting) { PlayerSettingScreen(nav) }
        composable(Route.DanmakuSetting) { DanmakuSettingScreen(nav) }
        composable(Route.History) { HistoryScreen(nav, sites) }
        composable(Route.Collect) { CollectScreen(nav, sites) }
    }
}
