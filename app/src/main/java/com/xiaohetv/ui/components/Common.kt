package com.xiaohetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosGrayCard
import com.xiaohetv.ui.theme.IosSecondary

@Composable
fun IosNavBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    Surface(color = Color.White.copy(alpha = 0.94f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp, bottom = 8.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = IosBlue)
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box { actions() }
        }
    }
}

@Composable
fun LargeTitle(title: String, subtitle: String = "", actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = IosSecondary)
            }
        }
        actions()
    }
}

/**
 * 首页标题栏：左上角「logo + 站点名」整体可点 → 弹出站点切换（对齐影视TV）。
 * 标题字号刻意小于 LargeTitle，避免过大不协调。
 */
@Composable
fun SiteTitleBar(
    title: String,
    subtitle: String = "",
    logo: String = "",
    onClickTitle: () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClickTitle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (logo.isEmpty()) Color.Transparent else IosBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (logo.isNotEmpty()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 没有站点 logo 时显示 App 自己的标识：只用 logo 的图形部分（不带 XHTV 文字）
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.xiaohetv.R.drawable.ic_logo_mark),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, fontSize = 11.sp, color = IosSecondary, maxLines = 1)
                }
            }
        }
        actions()
    }
}

@Composable
fun PosterCard(vod: Vod, ratio: Float = 0.75f, alpha: Float = 1f, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable { onClick() }) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = IosGrayCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth().height((110 * ratio).dp + 40.dp)) {
                AsyncImage(
                    model = vod.vodPic,
                    contentDescription = vod.vodName,
                    contentScale = ContentScale.Crop,
                    alpha = alpha,
                    modifier = Modifier.fillMaxSize()
                )
                if (vod.vodRemarks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text(text = vod.vodRemarks, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = vod.vodName,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) IosBlue else IosGrayCard
    val content = if (selected) Color.White else Color(0xFF1C1C1E)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = content, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = IosBlue, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = text, color = IosSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun EmptyHint(text: String, action: String = "", onClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, color = IosSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            if (action.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Chip(text = action, selected = true, onClick = onClick)
            }
        }
    }
}

@Composable
fun IosSection(title: String = "", content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = IosSecondary,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
fun IosRow(
    icon: ImageVector? = null,
    title: String,
    value: String = "",
    tint: Color = IosBlue,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value.isNotEmpty()) {
            Text(text = value, fontSize = 13.sp, color = IosSecondary, maxLines = 1)
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (onClick != null) {
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFC7C7CC), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun DividerItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(start = 14.dp)
            .background(Color(0xFFE5E5EA))
    )
}

@Composable
fun IosTabBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White.copy(alpha = 0.95f),
        contentColor = IosBlue
    ) {
        // 底部只保留 3 个纯图标：点播 / 直播 / 设置（不要文字）
        val items = listOf(
            Triple(0, "点播", Icons.Default.Home),
            Triple(1, "直播", Icons.Default.LiveTv),
            Triple(2, "设置", Icons.Default.Settings)
        )
        items.forEach { (index, label, icon) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IosBlue,
                    unselectedIconColor = IosSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

val iconSearch: ImageVector get() = Icons.Default.Search
val iconSettings: ImageVector get() = Icons.Default.Settings
val iconHistory: ImageVector get() = Icons.Default.History
val iconFavorite: ImageVector get() = Icons.Default.Favorite
val iconFavoriteBorder: ImageVector get() = Icons.Default.FavoriteBorder
val iconRefresh: ImageVector get() = Icons.Default.Refresh
val iconAdd: ImageVector get() = Icons.Default.Add
val iconDelete: ImageVector get() = Icons.Default.Delete
val iconPlay: ImageVector get() = Icons.Default.PlayArrow
val iconPause: ImageVector get() = Icons.Default.Pause
val iconClose: ImageVector get() = Icons.Default.Close
val iconBack: ImageVector get() = Icons.Default.ArrowBack

@Composable
fun SectionTitle(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
