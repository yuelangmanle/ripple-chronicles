
package com.dlovel.plankton.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.ChainOfCustodyEntry
import com.dlovel.plankton.data.GeoPoint
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.SampleMetadata
import com.dlovel.plankton.data.SamplingEvent
import com.dlovel.plankton.data.UsageEvent
import com.dlovel.plankton.service.DatasetTransferService
import com.dlovel.plankton.service.StorageManager
import com.dlovel.plankton.ui.components.EmptyStateCard
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.ScreenEnter
import com.dlovel.plankton.ui.components.SoftCard
import com.dlovel.plankton.util.ShareUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by LocalAppStore.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDatasetId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var samplingSite by remember { mutableStateOf("") }
    var sampleCode by remember { mutableStateOf("") }
    var sampledAt by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var waterDepth by remember { mutableStateOf("") }
    var waterTemperature by remember { mutableStateOf("") }
    var ph by remember { mutableStateOf("") }
    var salinity by remember { mutableStateOf("") }
    var eventStartedAt by remember { mutableStateOf("") }
    var eventEndedAt by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf("") }
    var tide by remember { mutableStateOf("") }
    var eventTemperature by remember { mutableStateOf("") }
    var eventPh by remember { mutableStateOf("") }
    var eventSalinity by remember { mutableStateOf("") }
    var qrCode by remember { mutableStateOf("") }
    var trackPoints by remember { mutableStateOf("") }
    var chainOperator by remember { mutableStateOf("") }
    var chainAction by remember { mutableStateOf("") }
    var chainNote by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Dataset?>(null) }
    var backupTarget by remember { mutableStateOf<Dataset?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importPreview by remember { mutableStateOf<DatasetTransferService.BackupPreview?>(null) }
    var importConflictStrategy by remember {
        mutableStateOf(DatasetTransferService.ImportConflictStrategy.RENAME)
    }
    var showImportPreview by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<DatasetTransferService.TransferProgress?>(null) }
    val datasets = state.datasets.sortedByDescending { it.created_at }
    val speciesMap = remember(state.species) { state.species.associateBy { it.id } }

    fun resetDatasetForm() {
        editingDatasetId = null
        newName = ""
        newDesc = ""
        samplingSite = ""
        sampleCode = ""
        sampledAt = ""
        latitude = ""
        longitude = ""
        waterDepth = ""
        waterTemperature = ""
        ph = ""
        salinity = ""
        eventStartedAt = ""
        eventEndedAt = ""
        weather = ""
        tide = ""
        eventTemperature = ""
        eventPh = ""
        eventSalinity = ""
        qrCode = ""
        trackPoints = ""
        chainOperator = ""
        chainAction = ""
        chainNote = ""
    }

    fun beginEdit(dataset: Dataset) {
        editingDatasetId = dataset.id
        newName = dataset.name
        newDesc = dataset.description.orEmpty()
        samplingSite = dataset.metadata.samplingSite.orEmpty()
        sampleCode = dataset.metadata.sampleCode.orEmpty()
        sampledAt = dataset.metadata.sampledAt.orEmpty()
        latitude = dataset.metadata.latitude?.toString().orEmpty()
        longitude = dataset.metadata.longitude?.toString().orEmpty()
        waterDepth = dataset.metadata.waterDepthMeters?.toString().orEmpty()
        waterTemperature = dataset.metadata.waterTemperatureCelsius?.toString().orEmpty()
        ph = dataset.metadata.ph?.toString().orEmpty()
        salinity = dataset.metadata.salinityPsu?.toString().orEmpty()
        val event = dataset.samplingEvents.firstOrNull()
        eventStartedAt = event?.startedAt.orEmpty()
        eventEndedAt = event?.endedAt.orEmpty()
        weather = event?.weather.orEmpty()
        tide = event?.tide.orEmpty()
        eventTemperature = event?.waterTemperatureCelsius?.toString().orEmpty()
        eventPh = event?.ph?.toString().orEmpty()
        eventSalinity = event?.salinityPsu?.toString().orEmpty()
        qrCode = event?.qrCode.orEmpty()
        trackPoints = event?.track?.joinToString(";") { "${it.latitude},${it.longitude}" }.orEmpty()
        val custody = event?.chainOfCustody?.lastOrNull()
        chainOperator = custody?.operator.orEmpty()
        chainAction = custody?.action.orEmpty()
        chainNote = custody?.note.orEmpty()
        showAddDialog = true
    }

    fun resolveDatasetName(baseName: String): String {
        val trimmed = baseName.trim().ifBlank { "导入数据集" }
        if (datasets.none { it.name == trimmed }) return trimmed
        var index = 1
        var candidate = "$trimmed-$index"
        while (datasets.any { it.name == candidate }) {
            index += 1
            candidate = "$trimmed-$index"
        }
        return candidate
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val target = backupTarget ?: return@rememberLauncherForActivityResult
        backupTarget = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val images = state.images.filter { it.dataset_id == target.id }
            val result = DatasetTransferService.exportDatasetToUri(context, target, images, speciesMap, uri)
            exporting = false
            if (result.error != null) {
                snackbarHostState.showSnackbar(result.error)
            } else {
                snackbarHostState.showSnackbar("已备份 ${result.displayName}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch {
            if (state.settings.storageMode == com.dlovel.plankton.data.StorageMode.CUSTOM &&
                state.settings.customRootUri.isNullOrBlank()
            ) {
                snackbarHostState.showSnackbar("请先在设置中选择自定义路径")
                return@launch
            }
            importing = true
            val previewResult = DatasetTransferService.previewBackupFromUri(context, uri)
            importing = false
            if (previewResult.error != null || previewResult.preview == null) {
                snackbarHostState.showSnackbar(previewResult.error ?: "无法读取备份清单")
                return@launch
            }
            importUri = uri
            importPreview = previewResult.preview
            importConflictStrategy = DatasetTransferService.ImportConflictStrategy.RENAME
            showImportPreview = true
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    resetDatasetForm()
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建数据集", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        ScreenEnter(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            GradientHeaderCard(
                title = "数据集管理",
                subtitle = "集中管理采样数据与图像记录",
                actionLabel = "新建数据集",
                onActionClick = {
                    resetDatasetForm()
                    showAddDialog = true
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "数据集列表")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip")) },
                    enabled = !importing
                ) {
                    Text(if (importing) "读取备份..." else "导入备份")
                }
            }
            importProgress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = if (progress.total == 0) 0f else progress.processed.toFloat() / progress.total,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "正在处理 ${progress.processed}/${progress.total}：${progress.currentItem}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (datasets.isEmpty()) {
                EmptyStateCard(
                    title = "还没有数据集",
                    subtitle = "创建一个数据集开始整理你的采样记录。",
                    actionLabel = "创建数据集",
                    onActionClick = {
                        resetDatasetForm()
                        showAddDialog = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(datasets) { ds ->
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.Top
                            ) {
                                Text(
                                    ds.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    TextButton(onClick = { beginEdit(ds) }) {
                                        Text("编辑")
                                    }
                                    TextButton(
                                        onClick = {
                                            backupTarget = ds
                                            val fileName = "数据集_${ds.name}_${System.currentTimeMillis()}.zip"
                                            backupLauncher.launch(fileName)
                                        },
                                        enabled = !exporting
                                    ) {
                                        Text("备份")
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                exporting = true
                                                val images = state.images.filter { it.dataset_id == ds.id }
                                                val result = DatasetTransferService.exportDatasetToCache(
                                                    context,
                                                    ds,
                                                    images,
                                                    speciesMap
                                                )
                                                exporting = false
                                                if (result.error != null || result.uri == null) {
                                                    snackbarHostState.showSnackbar(result.error ?: "分享失败")
                                                } else {
                                                    ShareUtils.shareUri(
                                                        context,
                                                        result.uri,
                                                        "application/zip",
                                                        "分享数据集"
                                                    )
                                                }
                                            }
                                        },
                                        enabled = !exporting
                                    ) {
                                        Text("分享")
                                    }
                                    TextButton(onClick = { deleteTarget = ds }) {
                                        Text("删除")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                ds.description ?: "无描述",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val metadata = ds.metadata
                            val metadataSummary = listOfNotNull(
                                metadata.samplingSite?.takeIf { it.isNotBlank() },
                                metadata.sampleCode?.takeIf { it.isNotBlank() }
                                    ?.let { "样品 $it" },
                                metadata.sampledAt?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (metadataSummary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    metadataSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (ds.samplingEvents.isNotEmpty()) {
                                Text(
                                    "采样事件 ${ds.samplingEvents.size} 个 · 轨迹点 ${ds.samplingEvents.sumOf { it.track.size }} 个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingDatasetId == null) "新建数据集" else "编辑数据集") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("名称") })
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("描述") })
                    OutlinedTextField(value = samplingSite, onValueChange = { samplingSite = it }, label = { Text("采样地点") })
                    OutlinedTextField(value = sampleCode, onValueChange = { sampleCode = it }, label = { Text("样品编号") })
                    OutlinedTextField(value = sampledAt, onValueChange = { sampledAt = it }, label = { Text("采样时间") })
                    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("纬度") })
                    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("经度") })
                    OutlinedTextField(value = waterDepth, onValueChange = { waterDepth = it }, label = { Text("水深（米）") })
                    OutlinedTextField(value = waterTemperature, onValueChange = { waterTemperature = it }, label = { Text("水温（℃）") })
                    OutlinedTextField(value = ph, onValueChange = { ph = it }, label = { Text("pH") })
                    OutlinedTextField(value = salinity, onValueChange = { salinity = it }, label = { Text("盐度（PSU）") })
                    Divider()
                    Text("采样事件（可选）", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = eventStartedAt, onValueChange = { eventStartedAt = it }, label = { Text("事件开始时间") })
                    OutlinedTextField(value = eventEndedAt, onValueChange = { eventEndedAt = it }, label = { Text("事件结束时间") })
                    OutlinedTextField(value = weather, onValueChange = { weather = it }, label = { Text("天气") })
                    OutlinedTextField(value = tide, onValueChange = { tide = it }, label = { Text("潮汐") })
                    OutlinedTextField(value = eventTemperature, onValueChange = { eventTemperature = it }, label = { Text("事件水温（℃）") })
                    OutlinedTextField(value = eventPh, onValueChange = { eventPh = it }, label = { Text("事件 pH") })
                    OutlinedTextField(value = eventSalinity, onValueChange = { eventSalinity = it }, label = { Text("事件盐度（PSU）") })
                    OutlinedTextField(value = qrCode, onValueChange = { qrCode = it }, label = { Text("样品二维码内容") })
                    OutlinedTextField(
                        value = trackPoints,
                        onValueChange = { trackPoints = it },
                        label = { Text("地图轨迹点（纬度,经度；分号分隔）") },
                        minLines = 2
                    )
                    Text("样品链追踪", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = chainOperator, onValueChange = { chainOperator = it }, label = { Text("操作人") })
                    OutlinedTextField(value = chainAction, onValueChange = { chainAction = it }, label = { Text("链路动作") })
                    OutlinedTextField(value = chainNote, onValueChange = { chainNote = it }, label = { Text("链路备注") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val name = newName.trim()
                        if (name.isEmpty()) {
                            snackbarHostState.showSnackbar("请输入数据集名称")
                            return@launch
                        }
                        val metadata = SampleMetadata(
                            samplingSite = samplingSite.trim().takeIf { it.isNotBlank() },
                            latitude = latitude.toDoubleOrNull(),
                            longitude = longitude.toDoubleOrNull(),
                            sampledAt = sampledAt.trim().takeIf { it.isNotBlank() },
                            waterDepthMeters = waterDepth.toDoubleOrNull(),
                            waterTemperatureCelsius = waterTemperature.toDoubleOrNull(),
                            ph = ph.toDoubleOrNull(),
                            salinityPsu = salinity.toDoubleOrNull(),
                            sampleCode = sampleCode.trim().takeIf { it.isNotBlank() }
                        )
                        val track = trackPoints.split(';', '；')
                            .mapNotNull { point ->
                                val parts = point.trim().split(',', '，')
                                if (parts.size != 2) null else {
                                    val lat = parts[0].trim().toDoubleOrNull()
                                    val lon = parts[1].trim().toDoubleOrNull()
                                    if (lat == null || lon == null) null else GeoPoint(lat, lon)
                                }
                            }
                        val chain = if (chainOperator.isNotBlank() || chainAction.isNotBlank() || chainNote.isNotBlank()) {
                            listOf(ChainOfCustodyEntry(
                                operator = chainOperator.trim().ifBlank { "未填写" },
                                action = chainAction.trim().ifBlank { "记录" },
                                note = chainNote.trim().takeIf { it.isNotBlank() }
                            ))
                        } else emptyList()
                        val hasSamplingEvent = listOf(
                            eventStartedAt, eventEndedAt, weather, tide, eventTemperature,
                            eventPh, eventSalinity, qrCode, trackPoints, chainOperator, chainAction, chainNote
                        ).any { it.isNotBlank() }
                        val samplingEvent = if (hasSamplingEvent) SamplingEvent(
                            site = samplingSite.trim().takeIf { it.isNotBlank() },
                            startedAt = eventStartedAt.trim().takeIf { it.isNotBlank() },
                            endedAt = eventEndedAt.trim().takeIf { it.isNotBlank() },
                            track = track,
                            weather = weather.trim().takeIf { it.isNotBlank() },
                            tide = tide.trim().takeIf { it.isNotBlank() },
                            waterTemperatureCelsius = eventTemperature.toDoubleOrNull(),
                            ph = eventPh.toDoubleOrNull(),
                            salinityPsu = eventSalinity.toDoubleOrNull(),
                            qrCode = qrCode.trim().takeIf { it.isNotBlank() },
                            chainOfCustody = chain
                        ) else null
                        val editingId = editingDatasetId
                        if (editingId == null) {
                            LocalAppStore.addDataset(
                                context = context,
                                name = name,
                                description = newDesc.trim(),
                                metadata = metadata,
                                samplingEvents = samplingEvent?.let { listOf(it) } ?: emptyList()
                            )
                        } else {
                            LocalAppStore.updateDataset(context, editingId) { dataset ->
                                dataset.copy(
                                    name = name,
                                    description = newDesc.trim(),
                                    metadata = metadata,
                                    samplingEvents = samplingEvent?.let { event ->
                                        if (dataset.samplingEvents.isEmpty()) listOf(event)
                                        else listOf(event) + dataset.samplingEvents.drop(1)
                                    } ?: dataset.samplingEvents
                                )
                            }
                        }
                        showAddDialog = false
                        resetDatasetForm()
                    }
                }) { Text(if (editingDatasetId == null) "创建" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; resetDatasetForm() }) { Text("取消") }
            }
        )
    }

    val preview = importPreview
    val previewUri = importUri
    if (showImportPreview && preview != null && previewUri != null) {
        val hasConflict = datasets.any { it.name == preview.datasetName }
        AlertDialog(
            onDismissRequest = {
                showImportPreview = false
                importPreview = null
                importUri = null
            },
            title = { Text("导入前预览") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("数据集：${preview.datasetName}")
                    Text("数据版本：v${preview.datasetVersion}")
                    Text("图片：${preview.imageCount} 张")
                    Text("解压后大小：${formatByteSize(preview.totalBytes)}")
                    preview.description?.takeIf { it.isNotBlank() }?.let { Text("说明：$it") }
                    if (hasConflict) {
                        Divider()
                        val existing = datasets.firstOrNull { it.name == preview.datasetName }
                        Text("发现同名数据集，请选择处理方式。")
                        if (existing != null) {
                            Text(
                                "差异预览：本地版本 v${existing.version}，备份版本 v${preview.datasetVersion}；默认重命名，不覆盖本地数据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = importConflictStrategy == DatasetTransferService.ImportConflictStrategy.RENAME,
                                onClick = {
                                    importConflictStrategy = DatasetTransferService.ImportConflictStrategy.RENAME
                                }
                            )
                            Text("重命名后导入")
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = importConflictStrategy == DatasetTransferService.ImportConflictStrategy.CANCEL,
                                onClick = {
                                    importConflictStrategy = DatasetTransferService.ImportConflictStrategy.CANCEL
                                }
                            )
                            Text("取消导入")
                        }
                    }
                    Text("导入失败的单个文件会列在结果提示中；导入中断会自动回滚已写入的图片。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = !importing,
                    onClick = {
                        showImportPreview = false
                        scope.launch {
                            importing = true
                            importProgress = DatasetTransferService.TransferProgress(0, preview.imageCount, "准备中")
                            val result = DatasetTransferService.importDatasetFromUri(
                                context = context,
                                uri = previewUri,
                                settings = state.settings,
                                speciesList = state.species,
                                nameResolver = { resolveDatasetName(it) },
                                existingDatasetNames = datasets.map { it.name }.toSet(),
                                conflictStrategy = importConflictStrategy,
                                onProgress = { importProgress = it }
                            )
                            importing = false
                            importProgress = null
                            importPreview = null
                            importUri = null
                            if (result.error != null) {
                                snackbarHostState.showSnackbar(result.error)
                                return@launch
                            }
                            val dataset = result.dataset ?: return@launch
                            LocalAppStore.update(context) {
                                it.copy(
                                    datasets = it.datasets + dataset,
                                    images = it.images + result.images
                                )
                            }
                            LocalAppStore.recordUsage(context, UsageEvent.IMPORT)
                            val failed = if (result.failedItems.isEmpty()) "" else "，失败 ${result.failedItems.size} 项"
                            snackbarHostState.showSnackbar("已导入 ${dataset.name}，图片 ${result.importedCount} 张$failed")
                        }
                    }
                ) { Text("开始导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportPreview = false
                    importPreview = null
                    importUri = null
                }) { Text("取消") }
            }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除数据集") },
            text = { Text("将删除该数据集及其所有图片，是否继续？") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val imagesToDelete = state.images.filter { it.dataset_id == target.id }
                        LocalAppStore.update(context) { currentState ->
                            currentState.copy(
                                datasets = currentState.datasets.filterNot { it.id == target.id },
                                images = currentState.images.filterNot { it.dataset_id == target.id }
                            )
                        }
                        deleteTarget = null
                        val result = snackbarHostState.showSnackbar("已删除 ${target.name}", "撤销")
                        if (result == SnackbarResult.ActionPerformed) {
                            LocalAppStore.update(context) { currentState ->
                                currentState.copy(
                                    datasets = currentState.datasets + target,
                                    images = currentState.images + imagesToDelete
                                )
                            }
                        } else {
                            imagesToDelete.forEach { image ->
                                StorageManager.deleteStoredUri(context, image.image_url)
                            }
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return "${"%.1f".format(java.util.Locale.US, bytes / 1024f / 1024f)} MB"
}
