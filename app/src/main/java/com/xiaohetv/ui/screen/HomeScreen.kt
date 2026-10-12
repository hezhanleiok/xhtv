package com.xiaohetv.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.xiaohetv.core.bean.Episode
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import com.xiaohetv.player.Playback
import com.xiaohetv.player.Thunder
import com.xiaohetv.ui.Route
import com.xiaohetv.ui.components.Chip
import com.xiaohetv.ui.components.IosTabBar
import com.xiaohetv.ui.components.LoadingBox
import com.xiaohetv.ui.components.PosterCard
import com.xiaohetv.ui.components.SiteTitleBar
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosSecondary
import com.xiaohetv.vm.ConfigVM
import com.xiaohetv.vm.HomeVM
import com.xiaohetv.vm.SiteVM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页（点播）：站点切换 + 分类/筛选 + 海报网格 + 「+」添加链接播放。
 *
 * 说明：本文件曾因一次脚本误写被清空，这里按反编译产物（HomeScreenKt.class / dex）
 * 逐段还原：站点标题栏、分类 LazyRow、筛选行、海报网格（3 列）、加载/空态/错误态、
 * 右下角「+」链接按钮、底部三项标签栏（点播/直播/设置），与原先行为一致。
 */
@Composable
fun HomeScreen(nav: NavHostController, sites: SiteVM, config: ConfigVM) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: HomeVM = viewModel()

    val siteList by sites.sites.collectAsStateWithLifecycle()
    val site by sites.current.collectAsStateWithLifecycle()
    val siteVersion by sites.version.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val picks by vm.picks.collectAsStateWithLifecycle()
    val configMsg by config.message.collectAsStateWithLifecycle()

    var tid by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var torrentItems by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var torrentDialog by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // 本地文件选择（打开即播）
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            linkDialog = false
            Playback.direct(context, uri.toString(), "本地文件")
        }
    }

    // 换站点 / 配置重载 → 重新加载
    LaunchedEffect(site?.key, siteVersion) {
        site?.let { vm.home(it) }
    }

    // 诊断：确认界面确实收到了列表（区分“数据没到”还是“渲染/图片问题”）
    LaunchedEffect(list.size, loading) {
        Diag.log("UI", "首页网格 items=${list.size} loading=$loading")
    }

    // 首页没有内容但站点有分类时，自动加载第一个分类，保证首页有海报（TVBox/FongMi 行为）
    LaunchedEffect(types, list.size, loading) {
        if (tid.isEmpty() && list.isEmpty() && !loading && types.isNotEmpty()) {
            val first = types.first()
            tid = first.typeId
            site?.let { vm.category(it, tid) }
        }
    }

    // 触底加载更多
    LaunchedEffect(gridState, list.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (list.size > 6 && last >= list.size - 6) vm.more()
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WallBackground()

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                // 照参考项目：底部固定三项（点播 / 直播 / 设置）
                IosTabBar(
                    selected = 0,
                    onSelect = { index ->
                        when (index) {
                            1 -> Playback.liveEntry(context)
                            2 -> nav.navigate(Route.Settings)
                        }
                    }
                )
            },
            topBar = {
                Column {
                    // 站点对话框开关：照参考项目，站点名点开的是「站点列表 + 搜索/换源开关」
                    var siteDialog by remember { mutableStateOf(false) }
                    SiteTitleBar(
                        title = site?.name ?: "Xiaohe影视",
                        subtitle = when {
                            linking -> "正在解析磁力链接…"
                            siteList.isEmpty() -> "还没有可用的配置源"
                            else -> "${siteList.size} 个站点"
                        },
                        // 左上角固定用 App 自己的标识（只用 logo 的图形部分，不带 XHTV）
                        logo = "",
                        // 照参考项目 VodFragment：图标 → 观看历史；站点名 → 站点对话框（两者不再一样）
                        onClickLogo = { nav.navigate(Route.History) },
                        onClickTitle = { siteDialog = true },
                        actions = {
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("站点（搜索 / 换源开关）") },
                                    onClick = { menu = false; siteDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新配置") },
                                    onClick = { menu = false; config.refresh(sites) }
                                )
                                DropdownMenuItem(
                                    text = { Text("订阅设置") },
                                    onClick = { menu = false; nav.navigate(Route.Settings) }
                                )
                                DropdownMenuItem(
                                    text = { Text("输入网址播放") },
                                    onClick = { menu = false; linkDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("运行日志（诊断）") },
                                    onClick = { menu = false; nav.navigate(Route.Log) }
                                )
                            }
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color(0xFF2C2C2E))
                            }
                            IconButton(onClick = { nav.navigate(Route.Search) }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color(0xFF2C2C2E))
                            }
                            IconButton(onClick = { nav.navigate(Route.Collect) }) {
                                Icon(Icons.Default.Star, contentDescription = "收藏", tint = Color(0xFF2C2C2E))
                            }
                            IconButton(onClick = { nav.navigate(Route.History) }) {
                                Icon(Icons.Default.History, contentDescription = "历史", tint = Color(0xFF2C2C2E))
                            }
                        }
                    )
                    if (siteDialog) {
                        SiteManageDialog(
                            sites = siteList,
                            currentKey = site?.key.orEmpty(),
                            onDismiss = { siteDialog = false },
                            onSelect = { sites.select(it); siteDialog = false },
                            onSearchable = { item, enable ->
                                sites.setSearchable(item, enable)
                                sites.refresh()       // 让列表拿到最新开关状态并触发重组
                            },
                            onChangeable = { item, enable ->
                                sites.setChangeable(item, enable)
                                sites.refresh()
                            }
                        )
                    }

                    if (types.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Chip(text = "首页", selected = tid.isEmpty(), onClick = {
                                    tid = ""
                                    site?.let { vm.home(it) }
                                })
                            }
                            items(types.size) { index ->
                                val item = types[index]
                                Chip(
                                    text = item.typeName,
                                    selected = tid == item.typeId,
                                    onClick = {
                                        tid = item.typeId
                                        site?.let { vm.category(it, item.typeId) }
                                    }
                                )
                            }
                        }
                    }

                    // 筛选项（分类下才有；首页不显示，避免把海报挤出屏幕）
                    for ((name, items) in filters) {
                        FilterRow(name = name, items = items, picks = picks, onPick = { key, value -> vm.pick(key, value) })
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    loading && list.isEmpty() -> LoadingBox()

                    siteList.isEmpty() -> HomeHint(
                        text = "还没有配置任何站点，点右下角「+」添加配置地址",
                        chips = listOf(
                            "重新加载" to { config.refresh(sites) },
                            "运行日志" to { nav.navigate(Route.Log) }
                        )
                    )

                    list.isEmpty() -> HomeHint(
                        text = error.ifEmpty { configMsg.ifEmpty { "没有内容，试试切换分类或搜索" } },
                        chips = listOf(
                            "重试" to { vm.retry() },
                            "运行日志" to { nav.navigate(Route.Log) }
                        )
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list.size) { index ->
                            val vod = list[index]
                            PosterCard(vod = vod, onClick = {
                                // 照原实现：点海报 = 「按片名找源」，进搜索页看多站点结果，
                                // 而不是直接进详情页（详情页内嵌播放器一进就自动播，表现为"点海报就开始播放"）。
                                // SearchScreen 顶部的自动搜索就是靠这两个 Session 字段触发的。
                                Session.searchKey = vod.vodName
                                Session.searchAuto = true
                                nav.navigate(Route.Search)
                            })
                        }
                    }
                }

                // 右下角「+」：添加链接播放（m3u8 / 直链 / 磁力 / ed2k / 本地文件）
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp)) {
                    FloatingActionButton(
                        onClick = { linkDialog = true },
                        containerColor = IosBlue,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "添加播放网址")
                    }
                }
            }
        }

        if (linkDialog) {
            TextInputDialog(
                title = "添加播放网址",
                value = "",
                placeholder = "m3u8 / 直链 / magnet:?xt=...",
                onDismiss = { linkDialog = false },
                onConfirm = { url ->
                    linkDialog = false
                    playLink(
                        context = context,
                        scope = scope,
                        setLinking = { linking = it },
                        setItems = { torrentItems = it },
                        openDialog = { torrentDialog = true },
                        url = url
                    )
                },
                extraLabel = "选择本地文件",
                onExtra = { filePicker.launch(arrayOf("*/*")) }
            )
        }

        if (torrentDialog) {
            MultiPickDialog(
                title = "选择要播放的文件",
                items = torrentItems.map { "${it.name}  (${it.desc})" },
                onDismiss = { torrentDialog = false },
                onPick = { index ->
                    torrentDialog = false
                    torrentItems.getOrNull(index)?.let { Playback.torrent(context, it.url, it.name) }
                }
            )
        }
    }
}

/** 空态/错误态提示：一句说明 + 若干操作（照原实现的「重试 / 运行日志」胶囊按钮）。 */
@Composable
private fun HomeHint(text: String, chips: List<Pair<String, () -> Unit>>) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF3C3C43),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chips.size) { index ->
                    val (label, action) = chips[index]
                    Chip(text = label, selected = false, onClick = action)
                }
            }
        }
    }
}

/**
 * 背景壁纸：用户设了本地壁纸且类型为图片时铺图（压暗 62% 白遮罩保证文字可读），
 * 否则用设置里选的渐变预设（照原实现 5 套配色）。
 */
@Composable
fun WallBackground() {
    val wallFile = remember { runCatching { com.xiaohetv.core.config.WallConfig.wallFile() }.getOrNull() }
    if (wallFile != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = wallFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.62f)))
        }
        return
    }
    val presets = listOf(
        listOf(Color(0xFFF7F9FC), Color(0xFFF7F7FB)),
        listOf(Color(0xFFEDF2F8), Color(0xFFF7F7FB)),
        listOf(Color(0xFFEAF4FF), Color(0xFFF7F7FB)),
        listOf(Color(0xFFFDF3F0), Color(0xFFF7F7FB)),
        listOf(Color(0xFFEDF7F1), Color(0xFFF7F7FB)),
        listOf(Color(0xFFF3EEFB), Color(0xFFF7F7FB))
    )
    val index = Store.settings.wall
    val colors = presets.getOrElse(index) { presets[0] }
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
}

/**
 * 筛选项一行（照原实现）：一个筛选组的标题 + 横向可滚动的选项胶囊。
 * onPick(筛选键, 选项值) 交给 HomeVM.pick 重新拉分类数据。
 */
@Composable
fun FilterRow(
    name: String,
    items: List<com.xiaohetv.core.bean.Filter>,
    picks: Map<String, String>,
    onPick: (String, String) -> Unit
) {
    val filter = items.firstOrNull() ?: return
    val values = filter.value
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = name,
            fontSize = 12.sp,
            color = IosSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(values.size) { index ->
                val value = values[index]
                Chip(
                    text = value.n,
                    selected = picks[filter.key] == value.v ||
                        (picks[filter.key].isNullOrEmpty() && value.v == filter.init),
                    onClick = { onPick(filter.key, value.v) }
                )
            }
        }
    }
}

/**
 * 处理「添加链接」：磁力/ed2k 走内置 Thunder 引擎（解析出文件列表后让用户选），
 * 其余（m3u8 / 直链 / content / file）直接交给播放器。文案与原实现一致。
 */
private fun playLink(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    setLinking: (Boolean) -> Unit,
    setItems: (List<Episode>) -> Unit,
    openDialog: () -> Unit,
    url: String
) {
    val link = url.trim()
    if (link.isEmpty()) return
    if (!Thunder.match(link)) {
        Playback.direct(context, link)
        return
    }
    if (!Thunder.isAvailable()) {
        Toast.makeText(context, "当前设备不支持磁力引擎（仅 arm64/armv7 真机可用）", Toast.LENGTH_LONG).show()
        return
    }
    setLinking(true)
    scope.launch {
        val outcome = runCatching { withContext(Dispatchers.IO) { Thunder.parse(link) } }
        setLinking(false)
        val files = outcome.getOrNull().orEmpty()
        when {
            outcome.isFailure ->
                Toast.makeText(context, "磁力解析失败：${outcome.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            files.isEmpty() ->
                Toast.makeText(context, "没有解析到可播放文件", Toast.LENGTH_LONG).show()
            files.size == 1 ->
                Playback.torrent(context, files[0].url, files[0].name)
            else -> {
                setItems(files)
                openDialog()
            }
        }
    }
}
