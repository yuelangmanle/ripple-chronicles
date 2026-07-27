
package com.dlovel.plankton.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.EmptyStateCard
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.ScreenEnter
import com.dlovel.plankton.ui.components.SoftCard
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun HomeScreen(navController: NavController) {
    val state by LocalAppStore.state.collectAsState()
    val datasets = remember(state.datasets) {
        state.datasets.sortedByDescending { it.created_at }.take(2)
    }
    val recentSpecies = remember(state.images, state.species) {
        val speciesById = state.species.associateBy { it.id }
        state.images.sortedByDescending { it.created_at }
            .mapNotNull { image -> image.species_id?.let(speciesById::get) }
            .distinctBy { it.id }
            .take(5)
    }
    val commonSpecies = remember(state.images, state.species) {
        val speciesById = state.species.associateBy { it.id }
        state.images.mapNotNull { it.species_id }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, count) -> speciesById[id]?.let { it to count } }
            .take(5)
    }
    val surname = sanitizeSurname(state.settings.homeUserName).ifBlank { "邓" }
    var greeting by remember { mutableStateOf(resolveGreeting()) }

    LaunchedEffect(Unit) {
        while (true) {
            greeting = resolveGreeting()
            delay(60_000L)
        }
    }

    ScreenEnter(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        GradientHeaderCard(
            title = "$greeting，${surname}研究员",
            subtitle = "欢迎回到溯澜录。\n准备好开始今天的鉴定了吗？",
            actionLabel = "开始拍照鉴定",
            onActionClick = { navController.navigate("camera") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(
            title = "最近数据集",
            actionLabel = "查看全部",
            onActionClick = { navController.navigate("datasets") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (datasets.isEmpty()) {
            EmptyStateCard(
                title = "还没有数据集",
                subtitle = "先创建数据集，再开始导入图片。",
                actionLabel = "创建数据集",
                onActionClick = { navController.navigate("datasets") }
            )
        } else {
            datasets.forEach { dataset ->
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📊", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(dataset.name, fontWeight = FontWeight.Bold)
                            Text(
                                dataset.description ?: "暂无描述",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "最近鉴定", actionLabel = "打开图库", onActionClick = { navController.navigate("gallery") })
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            if (recentSpecies.isEmpty()) {
                Text("完成物种关联后，最近鉴定会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recentSpecies.forEach { species ->
                    Text(species.name_cn ?: species.name_latin ?: "未命名物种")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(title = "常用物种")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            if (commonSpecies.isEmpty()) {
                Text("导入并关联图片后，会按使用次数列出常用物种。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                commonSpecies.forEach { (species, count) ->
                    Text("${species.name_cn ?: species.name_latin ?: "未命名物种"} · $count 次")
                }
            }
        }
    }
}

private fun resolveGreeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 0..2 -> "午夜好"
        in 3..5 -> "凌晨好"
        in 6..11 -> "上午好"
        in 12..16 -> "下午好"
        in 17..18 -> "傍晚好"
        else -> "晚上好"
    }
}

private fun sanitizeSurname(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.removeSuffix("研究员").trim()
}
