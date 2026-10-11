package com.xiaohetv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import com.xiaohetv.ui.Route
import com.xiaohetv.ui.components.Chip
import com.xiaohetv.ui.components.EmptyHint
import com.xiaohetv.ui.components.LoadingBox
import com.xiaohetv.vm.SearchVM
import com.xiaohetv.vm.SiteVM

private val Bg = Color(0xFFF7F7F9)
private val TitleColor = Color(0xFF1C1C1E)
private val TextColor = Color(0xFF3A3A3C)
private val SubColor = Color(0xFF8A8A8E)
private val CardBg = Color(0xFFF2F2F7)
private val Accent = Color(0xFF007AFF)

/**
 * 搜索页，照参考项目 fongmi 手机版复刻（配色跟随 App 浅色主题）：
 * - 输入态：fragment_search.xml（搜索框 + 热门搜索）
 * - 结果态：fragment_collect.xml（左侧站源列 120dp + 右侧结果卡，adapter_search 样式 128dp 卡片）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController, sites: SiteVM) {
    val vm: SearchVM = viewModel()
    val site by sites.current.collectAsState()
    val siteList by sites.sites.collectAsState()
    val list by vm.list.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    // 从 VM 恢复：从详情页返回时组合会被重建，靠 VM 里的状态才能停在"结果态"（不重新搜索）
    var key by remember { mutableStateOf(vm.key.value.ifEmpty { Session.searchKey }) }
    var submitted by remember { mutableStateOf(vm.submitted.value) }
    var picked by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf(Store.searchRecord.toList()) }

    // 左侧站源列 = 有结果的站点（按结果到达顺序），参考项目即如此
    val hitKeys = remember(list) {
        val seen = LinkedHashSet<String>()
        list.forEach { if (it.siteKey.isNotBlank()) seen += it.siteKey }
        seen.toList()
    }
    val shown = remember(list, picked) {
        val value = picked
        if (value == null) list else list.filter { it.siteKey == value }
    }
    // 同一站点的第一条才显示站源角标
    val badgeIndexes = remember(shown) {
        val seen = HashSet<String>()
        shown.indices.filter { seen.add(shown[it].siteKey) }.toHashSet()
    }

    // 站源列宽度按最长站名自适应（照参考项目 CollectFragment.setWidth：min 132dp，最多约屏幕一半）。
    // 关键：宽度只能基于「全部可搜索站点」，不能基于「已有结果的站点(hitKeys)」——
    // 聚合搜索是逐站陆续返回结果的，hitKeys 会在加载途中不断变大，若宽度跟着算，
    // railWidth 就会中途变宽 → 整个左列（以及右侧结果列）突然向右跳一下（用户实测的"闪一下"）。
    // 用 siteList 保证进页面即定宽，整个搜索过程恒定不动。
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val railWidth = remember(siteList, density) {
        val names = listOf("全部") + siteList.filter { it.searchable == 1 }.map { it.name }
        val widest = names.maxOfOrNull {
            measurer.measure(AnnotatedString(it), TextStyle(fontSize = 14.sp)).size.width
        } ?: 0
        with(density) {
            val maxWidth = (screenWidth / 2 - 24.dp).coerceAtLeast(132.dp)
            (widest.toDp() + 44.dp).coerceIn(132.dp, maxWidth)
        }
    }

    fun run(word: String) {
        val text = word.trim()
        if (text.isEmpty()) return
        key = text
        Session.searchKey = text
        picked = null
        submitted = true
        vm.key.value = text
        vm.submitted.value = true
        Store.addSearch(text)
        records = Store.searchRecord.toList()
        vm.multi(siteList, text)
    }

    // 首页点海报进来：自动按片名搜一次
    LaunchedEffect(Unit) {
        if (Session.searchAuto) {
            Session.searchAuto = false
            run(Session.searchKey)
        }
    }

    fun open(vod: Vod) {
        // 聚合搜索的结果各自属于不同站点，必须用结果自己的站点，否则会拿错站点解析
        val owner = siteList.firstOrNull { it.key == vod.siteKey } ?: site ?: return
        Session.site = owner
        Session.vod = vod
        Session.flagIndex = 0
        Session.episodeIndex = 0
        nav.navigate(Route.Detail)
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SearchToolbar(
                key = key,
                editing = !submitted,
                onKey = { key = it; Session.searchKey = it },
                onBack = { if (submitted) submitted = false else nav.popBackStack() },
                onEdit = { submitted = false },
                onSubmit = { run(key) },
                onClear = { key = ""; Session.searchKey = "" }
            )

            if (!submitted) {
                Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    if (records.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "搜索记录", fontSize = 18.sp, color = TitleColor, modifier = Modifier.weight(1f))
                            IconButton(onClick = { Store.clearSearch(); records = emptyList() }) {
                                Icon(Icons.Default.Delete, contentDescription = "清空搜索记录", tint = SubColor)
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            items(records) { word ->
                                Chip(text = word, selected = false, onClick = { run(word) })
                            }
                        }
                    }
                    Text(
                        text = "热门搜索",
                        fontSize = 18.sp,
                        color = TitleColor,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        items(Session.hotKeys) { word ->
                            Chip(text = word, selected = false, onClick = { run(word) })
                        }
                    }
                    if (site != null) {
                        Text(
                            text = "聚合搜索全部站点（左侧可切换站源）",
                            fontSize = 12.sp,
                            color = SubColor,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧站源列（照 fragment_collect.xml：固定宽度 LinearLayoutManager）
                    LazyColumn(
                        modifier = Modifier.width(railWidth).fillMaxHeight(),
                        contentPadding = PaddingValues(start = 8.dp, bottom = 8.dp)
                    ) {
                        item {
                            RailItem(text = "全部", selected = picked == null) { picked = null }
                        }
                        items(hitKeys) { itemKey ->
                            val name = siteList.firstOrNull { it.key == itemKey }?.name ?: itemKey
                            RailItem(text = name, selected = picked == itemKey) { picked = itemKey }
                        }
                    }

                    // 右侧结果列（照 adapter_search.xml：128dp 卡片 + 96dp 海报 + 站源角标）
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            shown.isNotEmpty() -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(end = 8.dp, bottom = 8.dp)
                            ) {
                                itemsIndexed(shown) { index, vod ->
                                    SearchResultRow(
                                        vod = vod,
                                        siteName = siteList.firstOrNull { it.key == vod.siteKey }?.name.orEmpty(),
                                        showSite = badgeIndexes.contains(index),
                                        onClick = { open(vod) }
                                    )
                                }
                            }

                            loading -> LoadingBox("搜索中…")
                            else -> EmptyHint(error.ifEmpty { "没有找到相关内容" })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchToolbar(
    key: String,
    editing: Boolean,
    onKey: (String) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TitleColor)
        }
        if (editing) {
            TextField(
                value = key,
                onValueChange = onKey,
                singleLine = true,
                // 支持手机键盘「搜索/回车」直接发起搜索
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                placeholder = { Text("搜索影片", fontSize = 14.sp, color = SubColor) },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = TitleColor,
                    unfocusedTextColor = TitleColor,
                    cursorColor = Accent
                ),
                trailingIcon = {
                    if (key.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = SubColor)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSubmit) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            // 参考项目：结果页标题就是关键词，点击标题回到输入态编辑
            Text(
                text = key,
                fontSize = 18.sp,
                color = TitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { onEdit() }.padding(vertical = 10.dp)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Search, contentDescription = "重新搜索", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 左侧站源项（照 adapter_collect.xml：圆角方块、居中、选中高亮）。 */
@Composable
private fun RailItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = if (selected) Color.White else TextColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent else CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

/** 结果卡（照 adapter_search.xml：128dp 高，左侧 96dp 海报，右侧名称 + 备注，海报左上角站源角标）。 */
@Composable
private fun SearchResultRow(vod: Vod, siteName: String, showSite: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, Color(0x11000000), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.width(96.dp).fillMaxHeight()) {
            AsyncImage(
                model = vod.vodPic,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(CardBg)
            )
            if (showSite && siteName.isNotBlank()) {
                Text(
                    text = siteName,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(4.dp)
                        .widthIn(max = 90.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(10.dp)) {
            Text(
                text = vod.vodName,
                fontSize = 14.sp,
                color = TitleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (vod.vodRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vod.vodRemarks,
                    fontSize = 12.sp,
                    color = SubColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
