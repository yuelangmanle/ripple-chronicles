
package com.dlovel.plankton.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.LocalAppStore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by LocalAppStore.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Dataset?>(null) }
    var backupTarget by remember { mutableStateOf<Dataset?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val datasets = state.datasets.sortedByDescending { it.created_at }
    val speciesMap = remember(state.species) { state.species.associateBy { it.id } }

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
            val result = DatasetTransferService.importDatasetFromUri(
                context = context,
                uri = uri,
                settings = state.settings,
                speciesList = state.species,
                nameResolver = { resolveDatasetName(it) }
            )
            importing = false
            if (result.error != null) {
                snackbarHostState.showSnackbar(result.error)
                return@launch
            }
            val dataset = result.dataset ?: return@launch
            val images = result.images
            LocalAppStore.update(context) {
                it.copy(
                    datasets = it.datasets + dataset,
                    images = it.images + images
                )
            }
            snackbarHostState.showSnackbar("已导入 ${dataset.name}，图片 ${result.importedCount} 张")
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        ScreenEnter(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            GradientHeaderCard(
                title = "数据集管理",
                subtitle = "集中管理采样数据与图像记录",
                actionLabel = "新建数据集",
                onActionClick = { showAddDialog = true }
            )
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "数据集列表")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip")) },
                    enabled = !importing
                ) {
                    Text(if (importing) "导入中..." else "导入备份")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (datasets.isEmpty()) {
                EmptyStateCard(
                    title = "还没有数据集",
                    subtitle = "创建一个数据集开始整理你的采样记录。",
                    actionLabel = "创建数据集",
                    onActionClick = { showAddDialog = true }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(datasets) { ds ->
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(ds.name, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建数据集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("名称") })
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("描述") })
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
                        LocalAppStore.addDataset(context, name, newDesc.trim())
                        showAddDialog = false
                        newName = ""
                        newDesc = ""
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
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
                        imagesToDelete.forEach { img ->
                            StorageManager.deleteStoredUri(context, img.image_url)
                        }
                        LocalAppStore.deleteDataset(context, target.id)
                        deleteTarget = null
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}
