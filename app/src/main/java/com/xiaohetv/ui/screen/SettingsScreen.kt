package com.xiaohetv.ui.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.config.ConfigStore
import com.xiaohetv.core.config.LiveConfig
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.core.config.WallConfig
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Json
import com.xiaohetv.data.Backup
import com.xiaohetv.data.Store
import com.xiaohetv.ui.Route
import com.xiaohetv.ui.components.IosSection
import com.xiaohetv.ui.components.LargeTitle
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosSecondary
import com.xiaohetv.utils.FileUtil
import com.xiaohetv.vm.ConfigVM
import com.xiaohetv.vm.SiteVM
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val THEME_AUTO = 0

private val THEME_COLORS = listOf(
    -1 to "关",
    THEME_AUTO to "自动",
    0xFF3949AB.toInt() to "靛蓝",
    0xFF1E88E5.toInt() to "蓝色",
    0xFF00ACC1.toInt() to "青色",
    0xFF00897B.toInt() to "蓝绿",
    0xFF43A047.toInt() to "绿色",
    0xFF7CB342.toInt() to "黄绿",
    0xFFFB8C00.toInt() to "橙色",
    0xFFE53935.toInt() to "红色",
    0xFFD81B60.toInt() to "玫红",
    0xFF8E24AA.toInt() to "紫色",
    0xFF6D4C41.toInt() to "棕色"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(nav: NavHostController, sites: SiteVM, config: ConfigVM) {
    val context = LocalContext.current
    val vodDesc by config.vodDesc.collectAsState()
    val liveDesc by config.liveDesc.collectAsState()
    val wallDesc by config.wallDesc.collectAsState()
    val message by config.message.collectAsState()
    val busy by config.busy.collectAsState()
    val siteList by sites.sites.collectAsState()

    var inputTarget by remember { mutableStateOf<Pair<Int, Config?>?>(null) }
    var listTarget by remember { mutableStateOf<Int?>(null) }
    var homeTarget by remember { mutableStateOf<Int?>(null) }
    var sizeDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var dohDialog by remember { mutableStateOf(false) }
    var restoreDialog by remember { mutableStateOf(false) }
    var moduleDialog by remember { mutableStateOf(false) }
    var uaDialog by remember { mutableStateOf(false) }
    var cacheText by remember { mutableStateOf("计算中…") }
    var version by remember { mutableIntStateOf(0) }

    fun refreshCache() {
        cacheText = FileUtil.cacheSize(context)
    }

    LaunchedEffect(version) { refreshCache() }
    LaunchedEffect(Unit) { config.refreshDesc() }

    val dohList = remember(version) { VodConfig.getDoh() }
    val dohIndex = runCatching {
        val saved = Store.settings.doh
        if (saved.isEmpty()) 0 else dohList.indexOfFirst { Json.gson.toJson(it) == saved }.coerceAtLeast(0)
    }.getOrDefault(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .verticalScroll(rememberScrollState())
    ) {
        LargeTitle("设置", message)

        if (busy) {
            androidx.compose.material3.LinearProgressIndicator(
                color = IosBlue,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ============ 订阅 ============
        IosSection {
            ConfigRow(
                label = "点播",
                value = vodDesc.ifEmpty { "未配置" },
                icon1 = Icons.Default.Home,
                icon2 = Icons.Default.Star,
                onRow = { inputTarget = Config.VOD to null },
                onLongRow = { ConfigStore.current(Config.VOD)?.let { inputTarget = Config.VOD to it } },
                onIcon1 = { homeTarget = Config.VOD },
                onIcon2 = { listTarget = Config.VOD }
            )
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            ConfigRow(
                label = "直播",
                value = liveDesc.ifEmpty { "未配置" },
                icon1 = Icons.Default.Home,
                icon2 = Icons.Default.Star,
                onRow = { inputTarget = Config.LIVE to null },
                onLongRow = { ConfigStore.current(Config.LIVE)?.let { inputTarget = Config.LIVE to it } },
                onIcon1 = { homeTarget = Config.LIVE },
                onIcon2 = { listTarget = Config.LIVE }
            )
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            ConfigRow(
                label = "壁纸",
                value = wallDesc.ifEmpty { "默认" },
                icon1 = Icons.Default.Home,
                icon2 = Icons.Default.Refresh,
                onRow = { inputTarget = Config.WALL to null },
                onLongRow = null,
                onIcon1 = {
                    Store.settings.wall = (Store.settings.wall + 1) % 5
                    Store.saveSettings()
                    version++
                },
                onIcon2 = {
                    ConfigStore.current(Config.WALL)?.let { config.select(it, sites) { _, _ -> version++ } }
                }
            )
        }

        // ============ 功能 ============
        IosSection {
            SimpleRow("刷新全部订阅", value = "点播 + 直播") {
                config.reloadAll(sites) { msg ->
                    config.message.value = msg
                    version++
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("模块管理", value = "内置") { moduleDialog = true }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("播放设置", value = Store.engineText()) { nav.navigate(Route.PlayerSetting) }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("弹幕设置") { nav.navigate(Route.DanmakuSetting) }
        }

        // ============ 外观 ============
        IosSection {
            SwitchRow("无痕模式", Store.incognitoText(), Store.settings.incognito) {
                Store.settings.incognito = it
                Store.saveSettings()
                version++
            }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("图片尺寸", Store.sizeText()) { sizeDialog = true }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("主题色彩", Store.themeText()) { themeDialog = true }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("DoH", dohList.getOrNull(dohIndex)?.name ?: "系统") { dohDialog = true }
        }

        // ============ 数据 ============
        IosSection {
            SimpleRow("缓存", cacheText) {
                FileUtil.clearCache(context)
                version++
            }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("备份", "立即备份") {
                Backup.backup().onSuccess { config.message.value = "已备份：${it.name}" }
                    .onFailure { config.message.value = it.message ?: "备份失败" }
            }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            SimpleRow("恢复", "从备份恢复") { restoreDialog = true }
            Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
            // 真实更新检测（原来这里是写死的：点一下永远提示"当前已是最新版本"，也没有仓库地址）
            var updateBusy by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<com.xiaohetv.data.Updater.Release?>(null) }
            var updateProgress by remember { mutableIntStateOf(-1) }
            SimpleRow("版本", if (updateBusy) "正在检查…" else com.xiaohetv.data.Updater.currentVersion(context)) {
                if (updateBusy) return@SimpleRow
                updateBusy = true
                com.xiaohetv.data.Updater.checkAsync { result ->
                    updateBusy = false
                    result.onSuccess { release ->
                        val current = com.xiaohetv.data.Updater.currentVersion(context)
                        if (com.xiaohetv.data.Updater.isNewer(release.version, current)) {
                            updateInfo = release
                        } else {
                            config.message.value = "当前已是最新版本（$current）"
                        }
                    }.onFailure {
                        config.message.value = "检查更新失败：${it.message ?: "网络不可达"}"
                    }
                }
            }
            // 发现新版本：可下载安装，也可先去发布页看说明
            updateInfo?.let { release ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { if (updateProgress < 0) updateInfo = null },
                    title = { Text("发现新版本 ${release.version}") },
                    text = {
                        Text(
                            text = buildString {
                                append("当前版本：${com.xiaohetv.data.Updater.currentVersion(context)}\n")
                                append("最新版本：${release.version}\n")
                                if (release.notes.isNotBlank()) append("\n${release.notes.take(600)}")
                                if (updateProgress >= 0) append("\n\n正在下载：$updateProgress%")
                            },
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (updateProgress >= 0) return@TextButton
                                updateProgress = 0
                                com.xiaohetv.data.Updater.downloadAsync(
                                    context,
                                    release.url,
                                    { updateProgress = it }
                                ) { result ->
                                    updateProgress = -1
                                    result.onSuccess { file ->
                                        updateInfo = null
                                        com.xiaohetv.data.Updater.install(context, file)
                                    }.onFailure {
                                        config.message.value = "下载失败：${it.message}"
                                    }
                                }
                            }
                        ) { Text(if (updateProgress >= 0) "下载中 $updateProgress%" else "下载并安装") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(com.xiaohetv.data.Updater.releasesPage)
                                        )
                                    )
                                }
                            }
                        ) { Text("打开发布页") }
                    }
                )
            }
        }

        // 说明：这里不再列「站点」——按参考项目的逻辑，站点管理只在首页（标题栏点开站点菜单）。
        // 需要来回切换的项，右上角/首页入口即可完成，设置页只保留真正的"设置"。

        Text(
            text = "Xiaohe影视 · 兼容 CatVod 配置与 Spider 协议\nApp 本身不内置任何内容来源",
            fontSize = 12.sp,
            color = IosSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    // ================= 对话框 =================
    inputTarget?.let { (type, existing) ->
        val title = when (type) {
            Config.VOD -> if (existing == null) "添加点播订阅" else "编辑点播订阅"
            Config.LIVE -> if (existing == null) "添加直播订阅" else "编辑直播订阅"
            else -> if (existing == null) "设置壁纸" else "编辑壁纸"
        }
        ConfigInputDialog(
            title = title,
            config = existing,
            onDismiss = { inputTarget = null },
            onConfirm = { url, name ->
                inputTarget = null
                val done: (Boolean, String) -> Unit = { ok, msg ->
                    config.message.value = msg
                    version++
                    Toast.makeText(context, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                }
                if (existing == null) config.submit(sites, type, url, name, done)
                else config.edit(sites, existing, url, name, done)
            }
        )
    }

    listTarget?.let { type ->
        ConfigListDialog(
            type = type,
            onDismiss = { listTarget = null },
            onSelect = { item ->
                listTarget = null
                config.select(item, sites) { ok, msg ->
                    config.message.value = msg
                    version++
                    Toast.makeText(context, if (ok) "已切换到：${item.desc()}" else msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                }
            },
            onEdit = { item -> listTarget = null; inputTarget = item.type to item },
            onDelete = { item -> config.remove(item, sites); version++ }
        )
    }

    homeTarget?.let { type ->
        if (type == Config.VOD) {
            val items = remember(version) { VodConfig.visibleSites() }
            HomePickDialog(
                title = "选择点播主页",
                items = items.map { it.name },
                selected = VodConfig.home?.name.orEmpty(),
                onDismiss = { homeTarget = null },
                onPick = { index ->
                    homeTarget = null
                    items.getOrNull(index)?.let { sites.select(it) }
                    version++
                }
            )
        } else {
            val items = remember(version) { LiveConfig.lives.toList() }
            HomePickDialog(
                title = "选择直播主页",
                items = items.map { it.name },
                selected = LiveConfig.home?.name.orEmpty(),
                onDismiss = { homeTarget = null },
                onPick = { index ->
                    homeTarget = null
                    items.getOrNull(index)?.let { LiveConfig.setHomeLive(it) }
                    version++
                }
            )
        }
    }

    if (sizeDialog) {
        SingleChoiceDialog(
            title = "图片尺寸",
            items = listOf("小", "中", "大", "超大"),
            selected = Store.settings.size,
            onDismiss = { sizeDialog = false },
            onPick = {
                sizeDialog = false
                Store.settings.size = it
                Store.saveSettings()
                version++
            }
        )
    }

    if (themeDialog) {
        SingleChoiceDialog(
            title = "主题色彩",
            items = THEME_COLORS.map { it.second },
            selected = THEME_COLORS.indexOfFirst { it.first == Store.settings.themeColor }.coerceAtLeast(0),
            onDismiss = { themeDialog = false },
            onPick = {
                themeDialog = false
                Store.settings.themeColor = THEME_COLORS[it].first
                Store.saveSettings()
                version++
            }
        )
    }

    if (dohDialog) {
        SingleChoiceDialog(
            title = "DoH 服务器",
            items = dohList.map { it.name },
            selected = dohIndex,
            onDismiss = { dohDialog = false },
            onPick = {
                dohDialog = false
                val doh = dohList.getOrNull(it) ?: return@SingleChoiceDialog
                OkHttp.setDoh(listOf(doh))
                Store.settings.doh = Json.gson.toJson(doh)
                Store.saveSettings()
                config.message.value = "DoH：${doh.name}"
                version++
            }
        )
    }

    if (restoreDialog) {
        RestoreDialog(
            files = remember(version) { Backup.list() },
            onDismiss = { restoreDialog = false },
            onPick = { file: File ->
                restoreDialog = false
                Backup.restore(file).onSuccess {
                    config.boot(sites)
                    config.message.value = "已恢复：${file.name}"
                    version++
                }.onFailure { config.message.value = it.message ?: "恢复失败" }
            }
        )
    }

    if (moduleDialog) {
        ModuleDialog(onDismiss = { moduleDialog = false })
    }

    if (uaDialog) {
        TextInputDialog(
            title = "播放 UA",
            value = Store.settings.ua,
            placeholder = "留空使用默认 UA",
            onDismiss = { uaDialog = false },
            onConfirm = {
                uaDialog = false
                Store.settings.ua = it
                Store.saveSettings()
                version++
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigRow(
    label: String,
    value: String,
    icon1: androidx.compose.ui.graphics.vector.ImageVector,
    icon2: androidx.compose.ui.graphics.vector.ImageVector,
    onRow: () -> Unit,
    onLongRow: (() -> Unit)?,
    onIcon1: () -> Unit,
    onIcon2: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onRow, onLongClick = onLongRow)
                .padding(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = IosSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        IconButtonSmall(icon1, onIcon1)
        Spacer(modifier = Modifier.width(6.dp))
        IconButtonSmall(icon2, onIcon2)
    }
}

@Composable
private fun IconButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEFF3F7))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = IosBlue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SimpleRow(title: String, value: String = "", onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(text = value, fontSize = 13.sp, color = IosSecondary, maxLines = 1)
    }
}

@Composable
private fun SwitchRow(title: String, value: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 13.sp, color = IosSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 模块列表项：builtIn=已随包内置；url=可从本仓库下载的地址（空=本仓库不提供）。 */
private data class ModuleRow(
    val name: String,
    val desc: String,
    val builtIn: Boolean,
    val url: String = ""
)

/** 本仓库 raw 地址（模块下载用真实地址，不再让用户自己找）。 */
private const val REPO_RAW = "https://raw.githubusercontent.com/hezhanleiok/xhtv/main"

/**
 * 模块管理：照参考项目做成真实模块列表（名称 / 说明 / 状态），不再是纯文字说明。
 * 已内置的都是真实可用能力（点播 JAR+JS、直播、磁力/ed2k、EXO 播放核心）。
 */
@Composable
fun ModuleDialog(onDismiss: () -> Unit) {
    // 状态必须写实（之前这里把 MPV 写成"未内置"，与事实不符）：
    //   · MPV：libmpv.so + libplayer.so 已随包（app/src/main/jniLibs）
    //   · EXO：media3 1.7.1 + FFmpeg 扩展（nextlib 的 libmedia3ext.so）→ 软解是真 FFmpeg
    //   · 磁力：libxl_thunder_sdk.so（Thunder）已内置
    // 未内置的三项是参考项目额外打包的运行时（其 APK 148MB 主要就是这些）：
    //   QuickJS（我们以 Rhino 实现等价能力）、Chaquopy Python（libpython3.10 等）、Node.js（libnode 46MB）。
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var modVersion by remember { mutableStateOf(0) }
    var urlDialog by remember { mutableStateOf(false) }
    var pendingModule by remember { mutableStateOf("") }
    var busyModule by remember { mutableStateOf("") }
    // 下载地址（默认预填本仓库地址，用户可改）
    var pendingUrl by remember { mutableStateOf("") }
    fun fileOf(name: String): String = when {
        name.contains("QuickJS") -> "quickjs.so"
        name.contains("Python") -> "python3.10.so"
        name.contains("Node") -> "libnode.so"
        else -> name.replace(" ", "_") + ".so"
    }
    // 状态写实（此前这里把 MPV 写成"未内置"，与事实不符）：
    //   MPV = libmpv.so + libplayer.so 已随包；EXO = media3 1.7.1 + nextlib FFmpeg 扩展（软解=真 FFmpeg）；
    //   磁力 = libxl_thunder_sdk.so 已内置。未内置的三项是参考项目另行打包的运行时（其 148MB 主要就是这些）。
    // 照参考项目 Core 模型，这里给未内置项真实的「填地址 → 下载 → 落盘 → 加载」入口。
    val modules = remember {
        listOf(
            ModuleRow("EXO 播放核心", "media3 1.7.1 + FFmpeg 扩展：软解=真 FFmpeg 解码", true),
            ModuleRow("MPV 播放核心", "libmpv.so + libplayer.so 已随包（gpu-context=android）", true),
            ModuleRow("CMS 点播", "XML / JSON 接口（首页/分类/搜索/详情）", true),
            ModuleRow("JAR Spider", "csp_ 类，DexClassLoader 动态加载", true),
            ModuleRow("JS Spider", "QuickJS 3.2.3 原生引擎（4 ABI 已随包）+ Rhino 自动回退", true),
            ModuleRow("磁力 / ed2k", "Thunder 核心（libxl_thunder_sdk.so）已内置", true),
            ModuleRow("直播源", "M3U / TXT / JSON + XMLTV 节目表", true),
            ModuleRow("WebView 嗅探解析", "网页嗅探取流（parse=0 嗅探 / parse=4 并行解析）", true),
            ModuleRow(
                "QuickJS 原生库（独立下载）",
                "libquickjs-android-wrapper.so（arm64/armv7/x86/x86_64），可从本仓库下载",
                false,
                "$REPO_RAW/modules/quickjs/arm64-v8a/libquickjs-android-wrapper.so"
            ),
            ModuleRow(
                "Python 运行时（Chaquopy）",
                "libpython3.10 / libcrypto_python / libsqlite3_python / libssl_python / libchaquopy_java（arm64）",
                false,
                "$REPO_RAW/modules/python/arm64-v8a/libpython3.10.so"
            ),
            ModuleRow(
                "Node.js 运行时",
                "libnode.so（47MB）+ libnodebridge.so（arm64）",
                false,
                "$REPO_RAW/modules/node/arm64-v8a/libnode.so"
            )
        )
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模块管理") },
        text = {
            // 模块条目较多（11 项 + 顶部说明），必须可滚动，否则下面的项看不到
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                modules.forEach { item ->
                    // 独立下载项用地址里的文件名（例如 libquickjs-android-wrapper.so）
                    val file = if (item.url.isNotEmpty()) item.url.substringAfterLast('/') else fileOf(item.name)
                    val installed = !item.builtIn && com.xiaohetv.data.ModuleManager.isInstalled(context, file)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontSize = 14.sp)
                            Text(
                                text = item.desc,
                                fontSize = 11.sp,
                                color = IosSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val available = item.url.isNotEmpty()      // 本仓库是否提供该模块
                        val chipColor = when {
                            item.builtIn || installed -> Color(0xFF34C759)
                            busyModule == file -> Color(0xFFFF9500)
                            available -> Color(0xFF0A84FF)
                            else -> Color(0xFF8E8E93)              // 本仓库不提供：灰色，不可点
                        }
                        val chipText = when {
                            item.builtIn -> "已内置"
                            busyModule == file -> "下载中…"
                            installed -> "已安装"
                            available -> "下载"
                            else -> "需自行编译"      // Python/Node：本仓库不提供二进制
                        }
                        Text(
                            text = chipText,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(chipColor)
                                .clickable(enabled = available && !installed && busyModule.isEmpty()) {
                                    pendingModule = file
                                    pendingUrl = item.url        // 预填本仓库地址，用户可改
                                    urlDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (modVersion < 0) "" else
                        "已内置的模块无需下载；其余项已预填本仓库（hezhanleiok/xhtv）地址，可自行改。\n" +
                            "下载后落到应用私有目录，.so 会立即尝试加载 —— 只安装可信来源的模块。\n" +
                            "说明：Python / Node 这里提供的是**运行时二进制**；播放器侧接入对应 Spider 引擎后才能真正跑起来" +
                            "（需要的话可以继续做 Python 侧接入）。",
                    fontSize = 11.sp,
                    color = IosSecondary
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
    if (urlDialog) {
        TextInputDialog(
            title = "安装模块：" + pendingModule,
            value = pendingUrl,
            placeholder = "模块下载地址（.so / .jar）",
            onDismiss = { urlDialog = false },
            onConfirm = { url ->
                val name = pendingModule
                urlDialog = false
                busyModule = name
                scope.launch {
                    val result = com.xiaohetv.data.ModuleManager.install(context, name, url)
                    busyModule = ""
                    modVersion++
                    android.widget.Toast.makeText(
                        context,
                        if (result.isSuccess) "模块安装完成：" + name
                        else "模块安装失败：" + result.exceptionOrNull()?.message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@Composable
fun AlertDialogSimple(title: String, content: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(content, fontSize = 13.sp) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

fun formatBackupTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
