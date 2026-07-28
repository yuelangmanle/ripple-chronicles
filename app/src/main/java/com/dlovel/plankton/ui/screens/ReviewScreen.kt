package com.dlovel.plankton.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dlovel.plankton.data.IdentificationHistory
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.Species
import com.dlovel.plankton.ui.components.EmptyStateCard
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.ScreenEnter
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.SoftCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val state by LocalAppStore.state.collectAsState()
    val speciesMap = remember(state.species) { state.species.associateBy { it.id } }
    var showHistory by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf(state.settings.reviewConfidenceThreshold.toFloat()) }
    val reviewImages = remember(state.images, state.species, showHistory, threshold) {
        state.images.filter { image ->
            if (showHistory) image.identificationHistory.isNotEmpty()
            else image.reviewStatus == "UNREVIEWED" ||
                (image.identificationConfidence != null && image.identificationConfidence < threshold)
        }
    }

    ScreenEnter(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        GradientHeaderCard(
            title = "鉴定工作台",
            subtitle = "待复核队列、候选对比与可追溯鉴定历史"
        )
        Spacer(modifier = Modifier.height(16.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    label = { Text("待复核 ${reviewImages.size}") }
                )
                FilterChip(
                    selected = showHistory,
                    onClick = { showHistory = true },
                    label = { Text("鉴定历史") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("置信度阈值：${threshold.toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                onValueChangeFinished = {
                    scope.launch {
                        LocalAppStore.updateSettings(
                            context,
                            state.settings.copy(reviewConfidenceThreshold = threshold.toInt())
                        )
                    }
                },
                valueRange = 0f..100f,
                steps = 19
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(title = if (showHistory) "鉴定记录" else "待复核图片")
        Spacer(modifier = Modifier.height(8.dp))
        if (reviewImages.isEmpty()) {
            EmptyStateCard(
                title = if (showHistory) "还没有鉴定历史" else "待复核队列为空",
                subtitle = "可以在图库中导入图片，或降低置信度阈值。"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reviewImages, key = { it.id }) { image ->
                    ReviewCard(image, state.species, speciesMap, showHistory)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(
    image: PlanktonImage,
    species: List<Species>,
    speciesMap: Map<String, Species>,
    showHistory: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCandidate by remember(image.id, image.species_id) { mutableStateOf(image.species_id) }
    var confidence by remember(image.id, image.identificationConfidence) {
        mutableStateOf((image.identificationConfidence ?: 50).toFloat())
    }
    val candidates = remember(image.id, image.candidateSpeciesIds, image.custom_name, species) {
        val explicit = image.candidateSpeciesIds.mapNotNull { speciesMap[it] }
        val query = image.custom_name.orEmpty().trim()
        val inferred = if (query.isBlank()) emptyList() else species.filter { candidate ->
            listOfNotNull(candidate.name_cn, candidate.name_latin)
                .plus(candidate.synonyms)
                .any { it.contains(query, ignoreCase = true) }
        }
        (explicit + inferred + listOfNotNull(image.species_id?.let(speciesMap::get)))
            .distinctBy { it.id }
            .take(5)
    }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = image.image_url,
                contentDescription = image.custom_name,
                modifier = Modifier.size(108.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(image.custom_name ?: "未命名", style = MaterialTheme.typography.titleSmall)
                Text(
                    "当前：${image.species_id?.let(speciesMap::get)?.name_cn ?: "未关联"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("状态：${reviewStatusLabel(image.reviewStatus)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("候选物种对比", style = MaterialTheme.typography.labelMedium)
        if (candidates.isEmpty()) {
            Text("暂无候选，可先在图库中关联物种。", style = MaterialTheme.typography.bodySmall)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                candidates.forEach { candidate ->
                    FilterChip(
                        selected = selectedCandidate == candidate.id,
                        onClick = { selectedCandidate = candidate.id },
                        label = {
                            Text("${candidate.name_cn ?: "未命名"} · ${candidate.name_latin.orEmpty()}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text("置信度 ${confidence.toInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(value = confidence, onValueChange = { confidence = it }, valueRange = 0f..100f, steps = 19)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                saveReview(context, scope, image, selectedCandidate, confidence.toInt(), "CONFIRMED")
            }) { Text("确认") }
            OutlinedButton(onClick = {
                saveReview(context, scope, image, selectedCandidate, confidence.toInt(), "REJECTED")
            }) { Text("驳回") }
            if (showHistory && image.identificationHistory.isNotEmpty()) {
                Text("历史 ${image.identificationHistory.size} 条", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun saveReview(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    image: PlanktonImage,
    speciesId: String?,
    confidence: Int,
    status: String
) {
    scope.launch {
        LocalAppStore.updateImage(context, image.id) { current ->
            current.copy(
                species_id = speciesId ?: current.species_id,
                identificationConfidence = confidence.coerceIn(0, 100),
                reviewStatus = status,
                reviewedAt = System.currentTimeMillis(),
                identificationHistory = current.identificationHistory + IdentificationHistory(
                    speciesId = speciesId ?: current.species_id,
                    confidence = confidence.coerceIn(0, 100),
                    status = status
                )
            )
        }
    }
}

private fun reviewStatusLabel(status: String): String = when (status) {
    "CONFIRMED" -> "已确认"
    "REJECTED" -> "已驳回"
    else -> "待复核"
}
