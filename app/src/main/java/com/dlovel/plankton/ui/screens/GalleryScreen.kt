package com.dlovel.plankton.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.AnnotationType
import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.StorageMode
import com.dlovel.plankton.data.UsageEvent
import com.dlovel.plankton.service.ExportService
import com.dlovel.plankton.service.StorageManager
import com.dlovel.plankton.ui.components.EmptyStateCard
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.ImageAnnotationEditor
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.SoftCard
import com.dlovel.plankton.ui.components.SpeciesAutocomplete
import com.dlovel.plankton.util.ShareUtils
import com.dlovel.plankton.util.VibrationUtil
import com.dlovel.plankton.util.matchSpeciesIdByName
import com.dlovel.plankton.util.matchesGalleryQuery
import com.dlovel.plankton.util.matchCandidateSpeciesIds
import com.dlovel.plankton.util.speciesIdAfterQueryChange
import com.dlovel.plankton.util.textFieldValueAtEnd
import com.dlovel.plankton.util.visibleSelectionIds
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val state by LocalAppStore.state.collectAsState()
    val datasets = state.datasets.sortedByDescending { it.created_at }
    val images = state.images.sortedByDescending { it.created_at }
    val settings = state.settings

    val speciesMap = remember(state.species) { state.species.associateBy { it.id } }
    val datasetMap = remember(datasets) { datasets.associateBy { it.id } }
    val categories = remember(state.species) {
        (state.species.mapNotNull { it.category }.distinct() + "未分类").distinct()
    }

    var exportMenuExpanded by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var datasetMenuExpanded by remember { mutableStateOf(false) }
    var selectedDatasetId by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("grid") }
    var searchInput by remember { mutableStateOf(textFieldValueAtEnd("")) }
    val searchQuery = searchInput.text
    var suggestionsExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var datasetFilterDialog by remember { mutableStateOf(false) }
    var categoryFilterDialog by remember { mutableStateOf(false) }
    var selectedDatasetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedImageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<PlanktonImage?>(null) }
    var renameName by remember { mutableStateOf("") }
    var renameSpeciesId by remember { mutableStateOf<String?>(null) }
    var showBatchLinkDialog by remember { mutableStateOf(false) }
    var batchSpeciesId by remember { mutableStateOf<String?>(null) }
    var batchSpeciesName by remember { mutableStateOf("") }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var batchRenamePrefix by remember { mutableStateOf("") }
    var confidenceText by remember { mutableStateOf("") }
    var reviewStatus by remember { mutableStateOf("UNREVIEWED") }
    var reviewNote by remember { mutableStateOf("") }
    var annotationTarget by remember { mutableStateOf<PlanktonImage?>(null) }
    var viewerImageId by remember { mutableStateOf<String?>(null) }
    var pendingExportItems by remember { mutableStateOf<List<ExportService.ExportItem>>(emptyList()) }
    var deleteImageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var exportPreviewMode by remember { mutableStateOf<ExportMode?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    var autoLinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reportMetadataSummary = remember(selectedDatasetIds, selectedDatasetId, datasetMap) {
        val dataset = selectedDatasetIds.singleOrNull()?.let { datasetMap[it] }
            ?: datasetMap[selectedDatasetId]
        dataset?.let(::formatReportMetadata)
    }
    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(datasets) {
        if (datasets.isEmpty()) {
            selectedDatasetId = ""
        } else if (selectedDatasetId.isBlank() || datasets.none { it.id == selectedDatasetId }) {
            selectedDatasetId = datasets.first().id
        }
    }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(renameTarget?.id) {
        val target = renameTarget
        confidenceText = target?.identificationConfidence?.toString().orEmpty()
        reviewStatus = target?.reviewStatus ?: "UNREVIEWED"
        reviewNote = target?.reviewNote.orEmpty()
    }

    val enterUp = remember {
        fadeIn(tween(260)) + slideInVertically(initialOffsetY = { it / 6 })
    }

    BackHandler(
        enabled = viewerImageId != null ||
            renameTarget != null ||
            datasetFilterDialog ||
            categoryFilterDialog ||
            selectionMode ||
            showBatchLinkDialog ||
            showBatchRenameDialog ||
            annotationTarget != null ||
            deleteImageIds.isNotEmpty()
    ) {
        when {
            viewerImageId != null -> viewerImageId = null
            renameTarget != null -> renameTarget = null
            datasetFilterDialog -> datasetFilterDialog = false
            categoryFilterDialog -> categoryFilterDialog = false
            showBatchLinkDialog -> showBatchLinkDialog = false
            showBatchRenameDialog -> showBatchRenameDialog = false
            annotationTarget != null -> annotationTarget = null
            deleteImageIds.isNotEmpty() -> deleteImageIds = emptySet()
            selectionMode -> {
                selectionMode = false
                selectedImageIds = emptySet()
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val targetDatasetId = if (selectedDatasetId.isNotBlank()) {
                selectedDatasetId
            } else {
                LocalAppStore.addDataset(context, "默认数据集", "自动创建").id.also {
                    selectedDatasetId = it
                }
            }

            if (settings.storageMode == StorageMode.CUSTOM && settings.customRootUri.isNullOrBlank()) {
                snackbarHostState.showSnackbar("请先在设置中选择自定义路径")
                return@launch
            }

            val newImages = mutableListOf<PlanktonImage>()
            uris.forEachIndexed { index, uri ->
                val baseName = safeBaseName(queryDisplayName(context, uri))
                    ?: "图片_${images.size + index + 1}"
                val savedUri = StorageManager.copyToStorage(context, uri, settings, baseName)
                if (savedUri != null) {
                    newImages.add(
                        PlanktonImage(
                            dataset_id = targetDatasetId,
                            image_url = savedUri,
                            custom_name = baseName,
                            candidateSpeciesIds = matchCandidateSpeciesIds(baseName, state.species)
                        )
                    )
                }
            }
            if (newImages.isNotEmpty()) {
                LocalAppStore.addImages(context, newImages)
                snackbarHostState.showSnackbar("已保存到本地 ${newImages.size} 张")
            } else {
                snackbarHostState.showSnackbar("导入失败，请重试")
            }
        }
    }

    val zipExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (pendingExportItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            exporting = true
            VibrationUtil.vibrate(context, 20)
            val result = ExportService.exportImagesZipToUri(
                context,
                pendingExportItems,
                uri,
                quality = settings.exportQuality
            )
            exporting = false
            if (result.uri != null) {
                LocalAppStore.recordUsage(context, UsageEvent.EXPORT)
                val snack = snackbarHostState.showSnackbar("已导出 ${result.displayName}", "分享")
                if (snack == SnackbarResult.ActionPerformed) {
                    ShareUtils.shareUri(context, result.uri, result.mimeType, "分享导出图片")
                }
            } else {
                snackbarHostState.showSnackbar(result.error ?: "导出失败")
            }
        }
    }

    val docExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(DOCX_MIME)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (pendingExportItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            exporting = true
            VibrationUtil.vibrate(context, 20)
            val result = ExportService.exportReportToUri(
                context,
                pendingExportItems,
                uri,
                quality = settings.exportQuality,
                metadataSummary = reportMetadataSummary
            )
            exporting = false
            if (result.uri != null) {
                LocalAppStore.recordUsage(context, UsageEvent.EXPORT)
                val snack = snackbarHostState.showSnackbar("已导出 ${result.displayName}", "分享")
                if (snack == SnackbarResult.ActionPerformed) {
                    ShareUtils.shareUri(context, result.uri, result.mimeType, "分享鉴定报告")
                }
            } else {
                snackbarHostState.showSnackbar(result.error ?: "导出失败")
            }
        }
    }

    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val result = ExportService.exportCsvToUri(context, pendingExportItems, uri)
            exporting = false
            snackbarHostState.showSnackbar(result.error ?: "已导出 ${result.displayName}")
        }
    }

    val excelExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val result = ExportService.exportExcelToUri(context, pendingExportItems, uri)
            exporting = false
            snackbarHostState.showSnackbar(result.error ?: "已导出 ${result.displayName}")
        }
    }

    val pdfExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val result = ExportService.exportPdfToUri(context, pendingExportItems, uri)
            exporting = false
            snackbarHostState.showSnackbar(result.error ?: "已导出 ${result.displayName}")
        }
    }

    val folderExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (pendingExportItems.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
            return@rememberLauncherForActivityResult
        }
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        scope.launch {
            exporting = true
            VibrationUtil.vibrate(context, 20)
            val result = ExportService.exportImagesToFolderUri(
                context,
                pendingExportItems,
                uri,
                quality = settings.exportQuality
            )
            exporting = false
            if (result.error != null) {
                snackbarHostState.showSnackbar(result.error)
            } else {
                LocalAppStore.recordUsage(context, UsageEvent.EXPORT)
                snackbarHostState.showSnackbar("已导出 ${result.successCount} 张")
            }
        }
    }

    val filteredImages = images.filter { img ->
        val datasetMatch = selectedDatasetIds.isEmpty() || selectedDatasetIds.contains(img.dataset_id)
        val category = speciesMap[img.species_id]?.category ?: "未分类"
        val categoryMatch = selectedCategories.isEmpty() || selectedCategories.contains(category)
        val favoritesMatch = !favoritesOnly || img.isFavorite
        val searchMatch = matchesGalleryQuery(
            image = img,
            species = speciesMap[img.species_id],
            dataset = datasetMap[img.dataset_id],
            query = searchQuery
        )
        datasetMatch && categoryMatch && favoritesMatch && searchMatch
    }
    var page by remember { mutableStateOf(1) }
    val pageSize = 40
    val pagedImages = remember(filteredImages, page) {
        filteredImages.take(page * pageSize)
    }
    val gridRows = remember(pagedImages) { pagedImages.chunked(2) }
    val exportImages = if (selectionMode && selectedImageIds.isNotEmpty()) {
        filteredImages.filter { selectedImageIds.contains(it.id) }
    } else {
        filteredImages
    }
    val previewRows = remember(exportImages) { exportImages.chunked(2) }
    val suggestions = remember(searchQuery, state.species, datasets, images) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            val speciesNames = state.species.flatMap {
                listOfNotNull(it.name_cn, it.name_latin) + it.synonyms
            }
            val datasetNames = datasets.map { it.name }
            val imageNames = images.mapNotNull { it.custom_name }
            (speciesNames + datasetNames + imageNames)
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }
                .take(6)
        }
    }
    val imageLoader = context.imageLoader
    val thumbSize = 512
    val unlinkedCount = remember(filteredImages, speciesMap) {
        filteredImages.count { img ->
            img.species_id.isNullOrBlank() || !speciesMap.containsKey(img.species_id)
        }
    }

    LaunchedEffect(listState, filteredImages, page) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                if (lastVisible >= listState.layoutInfo.totalItemsCount - 4) {
                    if (page * pageSize < filteredImages.size) {
                        page += 1
                    }
                }
            }
    }

    LaunchedEffect(pagedImages, filteredImages) {
        if (filteredImages.isEmpty()) return@LaunchedEffect
        val start = pagedImages.size
        val end = (start + 12).coerceAtMost(filteredImages.size)
        for (i in start until end) {
            val img = filteredImages[i]
            val request = ImageRequest.Builder(context)
                .data(img.image_url)
                .size(thumbSize)
                .build()
            imageLoader.enqueue(request)
        }
    }

    LaunchedEffect(filteredImages, viewerImageId) {
        selectedImageIds = visibleSelectionIds(selectedImageIds, filteredImages)
        if (viewerImageId != null && filteredImages.none { it.id == viewerImageId }) {
            viewerImageId = null
        }
        if (deleteImageIds.isNotEmpty() && state.images.none { it.id in deleteImageIds }) {
            deleteImageIds = emptySet()
        }
    }

    LaunchedEffect(filteredImages) {
        page = 1
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = showScrollTop,
                    enter = fadeIn(tween(180)) + scaleIn(),
                    exit = fadeOut(tween(180)) + scaleOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "回到顶部")
                    }
                }
                FloatingActionButton(
                    onClick = {
                        VibrationUtil.vibrate(context, 20)
                        launcher.launch(arrayOf("image/*"))
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "导入图片", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Column {
            AnimatedVisibility(visible = contentVisible, enter = enterUp) {
                GradientHeaderCard(
                    title = "图库",
                    subtitle = "本地优先保存与管理采样照片",
                    actionLabel = "导入图片",
                    onActionClick = {
                        VibrationUtil.vibrate(context, 20)
                        launcher.launch(arrayOf("image/*"))
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            AnimatedVisibility(visible = contentVisible, enter = enterUp) {
                SectionHeader(title = "图片列表")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { navController.navigate("review") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("打开鉴定工作台")
            }
            Spacer(modifier = Modifier.height(12.dp))

            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("导入到", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box {
                        OutlinedButton(
                            onClick = { datasetMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val current = datasets.firstOrNull { it.id == selectedDatasetId }
                            Text(current?.name ?: if (datasets.isEmpty()) "自动创建默认数据集" else "请选择数据集")
                        }
                        DropdownMenu(
                            expanded = datasetMenuExpanded,
                            onDismissRequest = { datasetMenuExpanded = false }
                        ) {
                            datasets.forEach { dataset ->
                                DropdownMenuItem(
                                    text = { Text(dataset.name) },
                                    onClick = {
                                        selectedDatasetId = dataset.id
                                        datasetMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("管理数据集") },
                                onClick = {
                                    datasetMenuExpanded = false
                                    navController.navigate("datasets")
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = suggestionsExpanded && suggestions.isNotEmpty(),
                onExpandedChange = { expanded -> suggestionsExpanded = expanded }
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { nextValue ->
                        searchInput = nextValue
                        suggestionsExpanded = nextValue.text.isNotBlank()
                    },
                    label = { Text("搜索物种/数据集/名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .menuAnchor(),
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = {
                                searchInput = textFieldValueAtEnd("")
                                suggestionsExpanded = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "清空搜索")
                            }
                        }
                    } else null
                )
                ExposedDropdownMenu(
                    expanded = suggestionsExpanded && suggestions.isNotEmpty(),
                    onDismissRequest = { suggestionsExpanded = false }
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                searchInput = textFieldValueAtEnd(suggestion)
                                suggestionsExpanded = false
                                scope.launch {
                                    delay(50)
                                    searchFocusRequester.requestFocus()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    selectedDatasetIds = emptySet()
                    selectedCategories = emptySet()
                    favoritesOnly = false
                    searchInput = textFieldValueAtEnd("")
                }) {
                    Text("查看全部")
                }
                if (selectedDatasetId.isNotBlank()) {
                    OutlinedButton(
                        onClick = { selectedDatasetIds = setOf(selectedDatasetId) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val name = datasetMap[selectedDatasetId]?.name ?: "当前数据集"
                        Text("仅看 $name")
                    }
                }
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("收藏") },
                    leadingIcon = if (favoritesOnly) {
                        { Icon(Icons.Default.Star, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (autoLinking) return@OutlinedButton
                        if (unlinkedCount == 0) {
                            scope.launch { snackbarHostState.showSnackbar("当前列表没有未关联的图片") }
                            return@OutlinedButton
                        }
                        autoLinking = true
                        scope.launch {
                            val targetIds = filteredImages.map { it.id }.toSet()
                            var matched = 0
                            var total = 0
                            LocalAppStore.update(context) { current ->
                                val speciesIdSet = current.species.map { it.id }.toSet()
                                val updatedImages = current.images.map { img ->
                                    val missingSpecies = img.species_id.isNullOrBlank() || !speciesIdSet.contains(img.species_id)
                                    if (img.id in targetIds && missingSpecies) {
                                        total += 1
                                        val autoId = matchSpeciesIdByName(img.custom_name, current.species)
                                        if (autoId != null) {
                                            matched += 1
                                            img.copy(species_id = autoId)
                                        } else {
                                            img
                                        }
                                    } else {
                                        img
                                    }
                                }
                                current.copy(images = updatedImages)
                            }
                            autoLinking = false
                            val notMatched = total - matched
                            snackbarHostState.showSnackbar("已自动关联 $matched 张，未匹配 $notMatched 张")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !autoLinking
                ) {
                    Text(if (autoLinking) "关联中..." else "按名称一键关联")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        VibrationUtil.vibrate(context, 20)
                        datasetFilterDialog = true
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (selectedDatasetIds.isEmpty()) "筛选数据集" else "已筛选数据集")
                }
                OutlinedButton(
                    onClick = {
                        VibrationUtil.vibrate(context, 20)
                        categoryFilterDialog = true
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (selectedCategories.isEmpty()) "筛选分类" else "已筛选分类")
                }
                Box {
                    Button(
                        onClick = {
                            VibrationUtil.vibrate(context, 20)
                            exportMenuExpanded = true
                        },
                        enabled = !exporting,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (exporting) "导出中..." else "导出")
                    }
                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出图片（ZIP）") },
                            onClick = {
                                exportMenuExpanded = false
                                if (selectionMode && selectedImageIds.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("请先勾选图片") }
                                    return@DropdownMenuItem
                                }
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                exportPreviewMode = ExportMode.ZIP
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出鉴定报告（Word）") },
                            onClick = {
                                exportMenuExpanded = false
                                if (selectionMode && selectedImageIds.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("请先勾选图片") }
                                    return@DropdownMenuItem
                                }
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                exportPreviewMode = ExportMode.WORD
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出图片（文件夹）") },
                            onClick = {
                                exportMenuExpanded = false
                                if (selectionMode && selectedImageIds.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("请先勾选图片") }
                                    return@DropdownMenuItem
                                }
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                exportPreviewMode = ExportMode.FOLDER
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出科研 CSV") },
                            onClick = {
                                exportMenuExpanded = false
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                pendingExportItems = buildExportItems(exportImages, speciesMap)
                                csvExporter.launch("鉴定记录_${exportTimestamp()}.csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出科研 Excel") },
                            onClick = {
                                exportMenuExpanded = false
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                pendingExportItems = buildExportItems(exportImages, speciesMap)
                                excelExporter.launch("鉴定记录_${exportTimestamp()}.xlsx")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出科研 PDF") },
                            onClick = {
                                exportMenuExpanded = false
                                if (exportImages.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                    return@DropdownMenuItem
                                }
                                pendingExportItems = buildExportItems(exportImages, speciesMap)
                                pdfExporter.launch("鉴定报告_${exportTimestamp()}.pdf")
                            }
                        )
                    }
                }
                IconButton(onClick = { viewMode = if (viewMode == "grid") "list" else "grid" }) {
                    Icon(
                        imageVector = if (viewMode == "grid") Icons.Default.ViewList else Icons.Default.ViewModule,
                        contentDescription = if (viewMode == "grid") "切换为列表" else "切换为网格"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectionMode) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已选 ${selectedImageIds.size} 张", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { selectedImageIds = filteredImages.map { it.id }.toSet() }) { Text("全选") }
                        TextButton(onClick = { selectedImageIds = emptySet() }) { Text("清空") }
                        TextButton(onClick = {
                            selectionMode = false
                            selectedImageIds = emptySet()
                        }) { Text("退出选择") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = selectedImageIds.isNotEmpty(),
                            onClick = { deleteImageIds = selectedImageIds }
                        ) { Text("删除") }
                        TextButton(
                            enabled = selectedImageIds.isNotEmpty(),
                            onClick = {
                                val ids = selectedImageIds
                                scope.launch {
                                    LocalAppStore.update(context) { currentState ->
                                        currentState.copy(
                                            images = currentState.images.map { image ->
                                                if (image.id in ids) {
                                                    image.copy(
                                                        reviewStatus = "CONFIRMED",
                                                        reviewedAt = System.currentTimeMillis()
                                                    )
                                                } else image
                                            }
                                        )
                                    }
                                    snackbarHostState.showSnackbar("已批量标记 ${ids.size} 张图片为已确认")
                                }
                            }
                        ) { Text("批量确认") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = selectedImageIds.isNotEmpty(),
                            onClick = {
                                batchSpeciesId = null
                                batchSpeciesName = ""
                                showBatchLinkDialog = true
                            }
                        ) { Text("批量关联") }
                        TextButton(
                            enabled = selectedImageIds.isNotEmpty(),
                            onClick = {
                                batchRenamePrefix = ""
                                showBatchRenameDialog = true
                            }
                        ) { Text("批量重命名") }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        selectionMode = true
                        selectedImageIds = emptySet()
                    }) {
                        Text("选择图片")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

                }
            }

            if (filteredImages.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "图库暂无内容",
                        subtitle = "导入图片后可进行查看与导出。",
                        actionLabel = "导入图片",
                        onActionClick = {
                            VibrationUtil.vibrate(context, 20)
                            launcher.launch(arrayOf("image/*"))
                        }
                    )
                }
            } else if (viewMode == "grid") {
                itemsIndexed(gridRows) { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEachIndexed { colIndex, img ->
                            val name = img.custom_name ?: "未命名"
                            val speciesName = speciesMap[img.species_id]?.name_cn
                            val isSelected = selectedImageIds.contains(img.id)
                            val toggleSelect = {
                                selectedImageIds = if (isSelected) {
                                    selectedImageIds - img.id
                                } else {
                                    selectedImageIds + img.id
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                toggleSelect()
                                            } else {
                                                viewerImageId = img.id
                                            }
                                        },
                                        onDoubleClick = if (selectionMode) {
                                            null
                                        } else {
                                            {
                                                VibrationUtil.vibrate(context, 20)
                                                renameTarget = img
                                                renameName = name
                                                renameSpeciesId = img.species_id
                                            }
                                        },
                                        onLongClick = {
                                            if (selectionMode) {
                                                toggleSelect()
                                            } else {
                                                VibrationUtil.vibrate(context, 20)
                                                renameTarget = img
                                                renameName = name
                                                renameSpeciesId = img.species_id
                                            }
                                        }
                                    )
                            ) {
                                Box {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(img.image_url)
                                            .size(thumbSize)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = name,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { toggleSelect() },
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                if (!speciesName.isNullOrBlank() && speciesName != name) {
                                    Text(
                                        text = speciesName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (rowIndex < gridRows.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else {
                itemsIndexed(pagedImages) { index, img ->
                    val name = img.custom_name ?: "未命名"
                    val speciesName = speciesMap[img.species_id]?.name_cn
                    val datasetName = datasetMap[img.dataset_id]?.name ?: "未归档"
                    val isSelected = selectedImageIds.contains(img.id)
                    val toggleSelect = {
                        selectedImageIds = if (isSelected) {
                            selectedImageIds - img.id
                        } else {
                            selectedImageIds + img.id
                        }
                    }
                    SoftCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        toggleSelect()
                                    } else {
                                        viewerImageId = img.id
                                    }
                                },
                                onDoubleClick = if (selectionMode) {
                                    null
                                } else {
                                    {
                                        VibrationUtil.vibrate(context, 20)
                                        renameTarget = img
                                        renameName = name
                                        renameSpeciesId = img.species_id
                                    }
                                },
                                onLongClick = {
                                    if (selectionMode) {
                                        toggleSelect()
                                    } else {
                                        VibrationUtil.vibrate(context, 20)
                                        renameTarget = img
                                        renameName = name
                                        renameSpeciesId = img.species_id
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(img.image_url)
                                    .size(thumbSize)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = name,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier
                                )
                                if (!speciesName.isNullOrBlank() && speciesName != name) {
                                    Text(
                                        text = speciesName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = datasetName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { toggleSelect() }
                                )
                            }
                        }
                    }
                    if (index < pagedImages.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    val previewMode = exportPreviewMode
    if (previewMode != null) {
        val previewTitle = when (previewMode) {
            ExportMode.ZIP -> "导出图片（ZIP）"
            ExportMode.WORD -> "导出鉴定报告（Word）"
            ExportMode.FOLDER -> "导出图片（文件夹）"
        }
        val previewScroll = rememberScrollState()
        Dialog(onDismissRequest = { exportPreviewMode = null }) {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text(previewTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("将导出 ${exportImages.size} 张图片，请确认。", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(previewScroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    previewRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { img ->
                                Column(modifier = Modifier.weight(1f)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(img.image_url)
                                            .size(thumbSize)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = img.custom_name,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = img.custom_name ?: "未命名",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { exportPreviewMode = null }) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (exportImages.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("暂无可导出的图片") }
                                exportPreviewMode = null
                                return@Button
                            }
                            pendingExportItems = exportImages.mapIndexed { index, img ->
                                ExportService.ExportItem(
                                    source = img.image_url,
                                    name = img.custom_name ?: "图片${index + 1}",
                                    speciesName = speciesMap[img.species_id]?.name_cn,
                                    speciesLatin = speciesMap[img.species_id]?.name_latin,
                                    confidence = img.identificationConfidence,
                                    reviewStatus = img.reviewStatus,
                                    reviewNote = img.reviewNote,
                                    createdAt = img.created_at
                                )
                            }
                            exportPreviewMode = null
                            when (previewMode) {
                                ExportMode.ZIP -> zipExporter.launch("图片导出_${exportTimestamp()}.zip")
                                ExportMode.WORD -> docExporter.launch("鉴定报告_${exportTimestamp()}.docx")
                                ExportMode.FOLDER -> folderExporter.launch(null)
                            }
                        }
                    ) {
                        Text("确认导出")
                    }
                }
            }
        }
    }

    if (datasetFilterDialog) {
        AlertDialog(
            onDismissRequest = { datasetFilterDialog = false },
            title = { Text("筛选数据集") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    datasets.forEach { dataset ->
                        val checked = selectedDatasetIds.contains(dataset.id)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    selectedDatasetIds = if (value) {
                                        selectedDatasetIds + dataset.id
                                    } else {
                                        selectedDatasetIds - dataset.id
                                    }
                                }
                            )
                            Text(dataset.name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { datasetFilterDialog = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDatasetIds = emptySet()
                    datasetFilterDialog = false
                }) { Text("清空") }
            }
        )
    }

    if (categoryFilterDialog) {
        AlertDialog(
            onDismissRequest = { categoryFilterDialog = false },
            title = { Text("筛选分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        val checked = selectedCategories.contains(category)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    selectedCategories = if (value) {
                                        selectedCategories + category
                                    } else {
                                        selectedCategories - category
                                    }
                                }
                            )
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { categoryFilterDialog = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedCategories = emptySet()
                    categoryFilterDialog = false
                }) { Text("清空") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名与关联") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    key(renameTarget?.id) {
                        SpeciesAutocomplete(
                            initialValue = renameName,
                            onQueryChanged = { nextQuery ->
                                renameSpeciesId = speciesIdAfterQueryChange(
                                    previousQuery = renameName,
                                    nextQuery = nextQuery,
                                    selectedSpeciesId = renameSpeciesId
                                )
                                renameName = nextQuery
                            },
                            onSpeciesSelected = { species ->
                                renameName = species.name_cn ?: renameName
                                renameSpeciesId = species.id
                            }
                        )
                    }
                    OutlinedTextField(
                        value = confidenceText,
                        onValueChange = { value ->
                            confidenceText = value.filter(Char::isDigit).take(3)
                        },
                        label = { Text("鉴定置信度（0-100）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("复核状态", style = MaterialTheme.typography.labelMedium)
                    listOf(
                        "UNREVIEWED" to "待复核",
                        "CONFIRMED" to "已确认",
                        "REJECTED" to "已驳回"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = reviewStatus == value,
                                onClick = { reviewStatus = value }
                            )
                            Text(label)
                        }
                    }
                    OutlinedTextField(
                        value = reviewNote,
                        onValueChange = { reviewNote = it },
                        label = { Text("复核备注") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = renameTarget ?: return@Button
                    VibrationUtil.vibrate(context, 20)
                    scope.launch {
                        LocalAppStore.updateImage(context, target.id) { img ->
                            img.copy(
                                custom_name = renameName.trim().ifBlank { img.custom_name },
                                species_id = renameSpeciesId,
                                identificationConfidence = confidenceText.toIntOrNull()
                                    ?.coerceIn(0, 100),
                                reviewStatus = reviewStatus,
                                reviewNote = reviewNote.trim().takeIf { it.isNotBlank() },
                                reviewedAt = if (reviewStatus == "UNREVIEWED") {
                                    null
                                } else {
                                    System.currentTimeMillis()
                                }
                            )
                        }
                    }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    if (showBatchLinkDialog) {
        AlertDialog(
            onDismissRequest = { showBatchLinkDialog = false },
            title = { Text("批量关联物种") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesAutocomplete(
                        initialValue = batchSpeciesName,
                        onQueryChanged = { nextQuery ->
                            batchSpeciesName = nextQuery
                            batchSpeciesId = null
                        },
                        onSpeciesSelected = { species ->
                            batchSpeciesId = species.id
                            batchSpeciesName = species.name_cn.orEmpty()
                        }
                    )
                    Text("将为 ${selectedImageIds.size} 张图片写入相同物种关联。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = batchSpeciesId != null,
                    onClick = {
                        val ids = selectedImageIds
                        val speciesId = batchSpeciesId ?: return@Button
                        scope.launch {
                            LocalAppStore.update(context) { currentState ->
                                currentState.copy(images = currentState.images.map { image ->
                                    if (image.id in ids) image.copy(species_id = speciesId) else image
                                })
                            }
                            snackbarHostState.showSnackbar("已批量关联 ${ids.size} 张图片")
                        }
                        showBatchLinkDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showBatchLinkDialog = false }) { Text("取消") } }
        )
    }

    if (showBatchRenameDialog) {
        AlertDialog(
            onDismissRequest = { showBatchRenameDialog = false },
            title = { Text("批量重命名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = batchRenamePrefix,
                        onValueChange = { batchRenamePrefix = it },
                        label = { Text("名称前缀") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("将按当前列表顺序命名为“前缀_001、前缀_002 ...”。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = batchRenamePrefix.trim().isNotBlank(),
                    onClick = {
                        val ids = filteredImages.filter { it.id in selectedImageIds }.map { it.id }
                        val names = ids.mapIndexed { index, id ->
                            id to "${batchRenamePrefix.trim()}_${(index + 1).toString().padStart(3, '0')}"
                        }.toMap()
                        scope.launch {
                            LocalAppStore.update(context) { currentState ->
                                currentState.copy(images = currentState.images.map { image ->
                                    names[image.id]?.let { image.copy(custom_name = it) } ?: image
                                })
                            }
                            snackbarHostState.showSnackbar("已重命名 ${names.size} 张图片")
                        }
                        showBatchRenameDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showBatchRenameDialog = false }) { Text("取消") } }
        )
    }

    if (deleteImageIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteImageIds = emptySet() },
            title = { Text("删除图片") },
            text = { Text("将删除选中的图片，是否继续？") },
            confirmButton = {
                Button(onClick = {
                    val ids = deleteImageIds.toList()
                    deleteImageIds = emptySet()
                    selectionMode = false
                    selectedImageIds = emptySet()
                    viewerImageId = null
                    scope.launch {
                        val removed = LocalAppStore.state.value.images.filter { it.id in ids }
                        LocalAppStore.update(context) { currentState ->
                            currentState.copy(images = currentState.images.filterNot { it.id in ids })
                        }
                        val result = snackbarHostState.showSnackbar("已删除 ${removed.size} 张图片", "撤销")
                        if (result == SnackbarResult.ActionPerformed) {
                            LocalAppStore.update(context) { currentState ->
                                currentState.copy(images = currentState.images + removed)
                            }
                        } else {
                            removed.forEach { image ->
                                StorageManager.deleteStoredUri(context, image.image_url)
                            }
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteImageIds = emptySet() }) { Text("取消") }
            }
        )
    }

    annotationTarget?.let { target ->
        ImageAnnotationEditor(
            image = target,
            onDismiss = { annotationTarget = null },
            onSave = { updated ->
                scope.launch {
                    LocalAppStore.updateImage(context, updated.id) { updated }
                }
            }
        )
    }

    val currentId = viewerImageId
    if (currentId != null && filteredImages.isNotEmpty()) {
        val currentIndex = filteredImages.indexOfFirst { it.id == currentId }
        val safeIndex = currentIndex.coerceIn(0, filteredImages.lastIndex)
        val pagerState = rememberPagerState(
            initialPage = safeIndex,
            pageCount = { filteredImages.size }
        )
        var currentZoomFraction by remember { mutableStateOf(0f) }
        LaunchedEffect(safeIndex) {
            if (pagerState.currentPage != safeIndex) {
                pagerState.scrollToPage(safeIndex)
            }
        }
        LaunchedEffect(pagerState.currentPage) {
            val pageId = filteredImages.getOrNull(pagerState.currentPage)?.id
            if (pageId != null && viewerImageId != pageId) {
                viewerImageId = pageId
            }
        }
        val activeIndex = pagerState.currentPage.coerceIn(0, filteredImages.lastIndex)
        val current = filteredImages[activeIndex]
        val speciesName = speciesMap[current.species_id]?.name_cn ?: "未关联物种"
        val datasetName = datasetMap[current.dataset_id]?.name ?: "未归档"
        val allowPagerScroll by remember(currentZoomFraction) {
            derivedStateOf { currentZoomFraction <= 0.01f }
        }

        Dialog(
            onDismissRequest = { viewerImageId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                SoftCard(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("图片浏览", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { viewerImageId = null }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = allowPagerScroll,
                            key = { page -> filteredImages[page].id },
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val img = filteredImages[page]
                            val zoomState = rememberZoomableImageState(
                                rememberZoomableState(
                                    zoomSpec = me.saket.telephoto.zoomable.ZoomSpec(maxZoomFactor = 4f)
                                )
                            )
                            val zoomFraction = zoomState.zoomableState.zoomFraction ?: 0f
                            val isCurrent = page == pagerState.currentPage
                            LaunchedEffect(isCurrent, zoomFraction) {
                                if (isCurrent) {
                                    currentZoomFraction = zoomFraction
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                ZoomableAsyncImage(
                                    model = img.image_url,
                                    contentDescription = img.custom_name,
                                    modifier = Modifier.fillMaxSize(),
                                    state = zoomState,
                                    contentScale = ContentScale.Fit
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = androidx.compose.ui.graphics.Paint().apply {
                                        color = androidx.compose.ui.graphics.Color(0xFFFFC107)
                                        this.strokeWidth = 4f
                                        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                                    }
                                    img.annotations.forEach { annotation ->
                                        val startX = annotation.x.coerceIn(0f, 1f) * size.width
                                        val startY = annotation.y.coerceIn(0f, 1f) * size.height
                                        val endX = (annotation.endX ?: annotation.x).coerceIn(0f, 1f) * size.width
                                        val endY = (annotation.endY ?: annotation.y).coerceIn(0f, 1f) * size.height
                                        when (annotation.type) {
                                            AnnotationType.POINT -> drawCircle(stroke.color, radius = 8f, center = androidx.compose.ui.geometry.Offset(startX, startY), style = androidx.compose.ui.graphics.drawscope.Fill)
                                            AnnotationType.ARROW, AnnotationType.MEASUREMENT -> drawLine(stroke.color, androidx.compose.ui.geometry.Offset(startX, startY), androidx.compose.ui.geometry.Offset(endX, endY), strokeWidth = 4f)
                                            AnnotationType.RECTANGLE -> drawRect(stroke.color, topLeft = androidx.compose.ui.geometry.Offset(startX, startY), size = androidx.compose.ui.geometry.Size(endX - startX, endY - startY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                                            AnnotationType.TEXT -> drawContext.canvas.nativeCanvas.drawText(annotation.text.orEmpty(), startX, startY, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.YELLOW; textSize = 32f })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(current.custom_name ?: "未命名", style = MaterialTheme.typography.titleSmall)
                    Text(speciesName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(datasetName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "复核：" + reviewStatusLabel(current.reviewStatus) +
                            " · 置信度：" + (current.identificationConfidence?.toString() ?: "未填写"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("缩放提示", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "双指缩放 / 双击放大",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (activeIndex > 0) {
                                    scope.launch { pagerState.animateScrollToPage(activeIndex - 1) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        LocalAppStore.updateImage(context, current.id) {
                                            it.copy(isFavorite = !it.isFavorite)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = if (current.isFavorite) "取消收藏" else "收藏",
                                    tint = if (current.isFavorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            OutlinedButton(onClick = {
                                VibrationUtil.vibrate(context, 20)
                                renameTarget = current
                                renameName = current.custom_name ?: ""
                                renameSpeciesId = current.species_id
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重命名")
                            }
                            OutlinedButton(onClick = { annotationTarget = current }) {
                                Text("标注")
                            }
                            OutlinedButton(onClick = { deleteImageIds = setOf(current.id) }) {
                                Text("删除")
                            }
                            OutlinedButton(onClick = { viewerImageId = null }) {
                                Text("关闭")
                            }
                        }
                        IconButton(
                            onClick = {
                                if (activeIndex < filteredImages.lastIndex) {
                                    scope.launch { pagerState.animateScrollToPage(activeIndex + 1) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            return it.getString(0)
        }
    }
    return null
}

private fun safeBaseName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    return name.substringBeforeLast('.', name).trim().ifBlank { null }
}

private fun exportTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
}

private fun reviewStatusLabel(status: String): String {
    return when (status) {
        "CONFIRMED" -> "已确认"
        "REJECTED" -> "已驳回"
        else -> "待复核"
    }
}

private fun formatReportMetadata(dataset: Dataset): String {
    val metadata = dataset.metadata
    val values = listOfNotNull(
        "数据集：" + dataset.name,
        metadata.sampleCode?.takeIf { it.isNotBlank() }?.let { "样品编号：" + it },
        metadata.samplingSite?.takeIf { it.isNotBlank() }?.let { "采样地点：" + it },
        metadata.sampledAt?.takeIf { it.isNotBlank() }?.let { "采样时间：" + it },
        metadata.latitude?.let { "纬度：" + it },
        metadata.longitude?.let { "经度：" + it },
        metadata.waterDepthMeters?.let { "水深：" + it + " m" },
        metadata.waterTemperatureCelsius?.let { "水温：" + it + " ℃" },
        metadata.ph?.let { "pH：" + it },
        metadata.salinityPsu?.let { "盐度：" + it + " PSU" }
    )
    return values.joinToString("  |  ")
}

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

private enum class ExportMode {
    ZIP,
    WORD,
    FOLDER
}

private fun buildExportItems(
    images: List<PlanktonImage>,
    speciesMap: Map<String, com.dlovel.plankton.data.Species>
): List<ExportService.ExportItem> = images.mapIndexed { index, image ->
    ExportService.ExportItem(
        source = image.image_url,
        name = image.custom_name ?: "图片${index + 1}",
        speciesName = speciesMap[image.species_id]?.name_cn,
        speciesLatin = speciesMap[image.species_id]?.name_latin,
        confidence = image.identificationConfidence,
        reviewStatus = image.reviewStatus,
        reviewNote = image.reviewNote,
        createdAt = image.created_at
    )
}
