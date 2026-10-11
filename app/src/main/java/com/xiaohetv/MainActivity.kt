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
                PermissionGate()
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
            Playback.direct(this, url)
        }
    }

    private fun extractUrl(text: String): String {
        val regex = Regex("(https?|thunder|magnet|ed2k|content|file):\\S+")
        return regex.find(text)?.value ?: text.trim()
    }
}

/** 首次启动申请必要权限（网络为系统默认，不需申请）。 */
@Composable
fun PermissionGate() {
    var show by remember { mutableStateOf(!Store.settings.permAsked) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Store.settings.permAsked = true
        Store.saveSettings()
        show = false
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("需要以下权限") },
        text = {
            Text(
                text = """
                    · 网络：加载订阅、播放（必需）
                    · 通知：后台播放 / 磁力解析状态提示
                    · 存储与媒体：播放本机视频、导入本地配置文件
                    · 电话状态：磁力引擎生成设备标识

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
    list.add(Manifest.permission.READ_PHONE_STATE)
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
