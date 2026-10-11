package com.xiaohetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosGrayCard
import com.xiaohetv.ui.theme.IosSecondary

@Composable
fun VodRow(vod: Vod, extra: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = IosGrayCard)
        ) {
            Box(modifier = Modifier.size(width = 62.dp, height = 86.dp)) {
                AsyncImage(
                    model = vod.vodPic,
                    contentDescription = vod.vodName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = vod.vodName, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = listOf(vod.typeName, vod.vodYear, vod.vodArea).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp,
                color = IosSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (extra.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = extra, fontSize = 12.sp, color = IosBlue, maxLines = 1)
            }
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(IosBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun MetaText(text: String) {
    Text(text = text, fontSize = 12.sp, color = IosSecondary)
}

@Composable
fun TitleText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
