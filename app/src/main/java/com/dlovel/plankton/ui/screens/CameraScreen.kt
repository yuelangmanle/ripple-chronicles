package com.dlovel.plankton.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.StorageMode
import com.dlovel.plankton.data.UsageEvent
import com.dlovel.plankton.service.StorageManager
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.SoftCard
import com.dlovel.plankton.ui.components.SpeciesAutocomplete
import com.dlovel.plankton.util.VibrationUtil
import com.dlovel.plankton.util.matchSpeciesIdByName
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@ExperimentalCamera2Interop
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by LocalAppStore.state.collectAsState()
    val datasets = state.datasets.sortedByDescending { it.created_at }
    val images = state.images.sortedByDescending { it.created_at }
    val settings = state.settings
    val scrollState = rememberScrollState()

    BackHandler {
        navController.popBackStack()
    }

    fun persistSaveToAlbum(enabled: Boolean) {
        scope.launch {
            LocalAppStore.updateSettings(context, settings.copy(saveToAlbum = enabled))
        }
    }

    var selectedDatasetId by remember { mutableStateOf("") }
    var datasetMenuExpanded by remember { mutableStateOf(false) }
    var sampleCodeInput by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraOptions by remember { mutableStateOf<List<CameraOption>>(emptyList()) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var activeExtensionLabel by remember { mutableStateOf("标准") }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    var exposureRange by remember { mutableStateOf(IntRange(0, 0)) }
    var exposureIndex by remember { mutableStateOf(0) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusVisible by remember { mutableStateOf(false) }
    var focusLockEnabled by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showScaleGuide by remember { mutableStateOf(false) }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("") }
    var selectedSpeciesId by remember { mutableStateOf<String?>(null) }
    var savingToAlbum by remember { mutableStateOf(settings.saveToAlbum) }
    var capturing by remember { mutableStateOf(false) }
    var systemPhotoFile by remember { mutableStateOf<File?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(datasets) {
        if (datasets.isEmpty()) {
            selectedDatasetId = ""
        } else if (selectedDatasetId.isBlank() || datasets.none { it.id == selectedDatasetId }) {
            selectedDatasetId = datasets.first().id
        }
    }

    LaunchedEffect(settings.saveToAlbum) {
        savingToAlbum = settings.saveToAlbum
    }

    LaunchedEffect(selectedDatasetId, datasets) {
        sampleCodeInput = datasets.firstOrNull { it.id == selectedDatasetId }
            ?.metadata
            ?.sampleCode
            .orEmpty()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            savingToAlbum = false
            scope.launch { snackbarHostState.showSnackbar("未授予相册写入权限") }
        } else {
            persistSaveToAlbum(true)
        }
    }

    val systemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val file = systemPhotoFile ?: return@rememberLauncherForActivityResult
        scope.launch {
                val datasetId = if (selectedDatasetId.isNotBlank()) {
                    selectedDatasetId
                } else {
                    LocalAppStore.addDataset(context, "默认数据集", "自动创建").id.also {
                        selectedDatasetId = it
                    }
                }
            try {
                val baseName = captureDisplayName(sampleCodeInput, file.nameWithoutExtension)
                val autoSpeciesId = matchSpeciesIdByName(baseName, state.species)
                if (savingToAlbum) {
                    saveToAlbum(context, file, baseName)
                }
                val storedUri = StorageManager.copyToStorage(
                    context,
                    Uri.fromFile(file),
                    settings,
                    baseName
                ) ?: Uri.fromFile(file).toString()
                val newImage = PlanktonImage(
                    dataset_id = datasetId,
                    image_url = storedUri,
                    custom_name = baseName,
                    species_id = autoSpeciesId
                )
                LocalAppStore.addImages(context, listOf(newImage))
                LocalAppStore.recordUsage(context, UsageEvent.CAPTURE)
                selectedImageId = newImage.id
                if (storedUri != Uri.fromFile(file).toString()) {
                    file.delete()
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("保存失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    val previewView = remember { PreviewView(context) }
    val focusRingSize = 72.dp
    val focusRingPx = with(LocalDensity.current) { focusRingSize.toPx() }
    val focusAlpha by animateFloatAsState(
        targetValue = if (focusVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "focusAlpha"
    )
    val focusScale by animateFloatAsState(
        targetValue = if (focusVisible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 180),
        label = "focusScale"
    )

    LaunchedEffect(
        hasPermission,
        lensFacing,
        selectedCameraId,
        settings.enableExtensions,
        settings.extensionMode,
        settings.forceExtensions
    ) {
        if (!hasPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                val cameraProvider = cameraProviderFuture.get()
                    val options = buildCameraOptions(cameraProvider, lensFacing)
                    cameraOptions = options
                    val resolvedCameraId = when {
                        selectedCameraId != null && options.any { it.cameraId == selectedCameraId } -> selectedCameraId
                        options.isNotEmpty() -> options.first().cameraId
                        else -> null
                    }
                    if (resolvedCameraId != selectedCameraId) {
                        selectedCameraId = resolvedCameraId
                    }
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(95)
                        .build()
                    val selectorBuilder = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                    if (resolvedCameraId != null) {
                        selectorBuilder.addCameraFilter { cameraInfos ->
                            cameraInfos.filter { info ->
                                Camera2CameraInfo.from(info).cameraId == resolvedCameraId
                            }
                        }
                    }
                    val baseSelector = selectorBuilder.build()

                    fun bindCamera(selector: CameraSelector, label: String) {
                        activeExtensionLabel = label
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        imageCapture = capture
                        cameraControl = camera.cameraControl
                        val zoomState = camera.cameraInfo.zoomState.value
                        zoomRatio = zoomState?.zoomRatio ?: 1f
                        minZoom = zoomState?.minZoomRatio ?: 1f
                        maxZoom = zoomState?.maxZoomRatio ?: 1f
                        val exposureState = camera.cameraInfo.exposureState
                        val range = exposureState.exposureCompensationRange
                        exposureRange = range.lower..range.upper
                        exposureIndex = exposureState.exposureCompensationIndex
                    }

                    if (!settings.enableExtensions) {
                        bindCamera(baseSelector, "标准")
                    } else {
                        val extensionsFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider)
                        extensionsFuture.addListener(
                            {
                                try {
                                    val extensionsManager = extensionsFuture.get()
                                    val choice = resolveExtensionChoice(
                                        extensionsManager,
                                        baseSelector,
                                        settings.extensionMode,
                                        settings.forceExtensions
                                    )
                                    val selector = choice?.selector ?: baseSelector
                                    val label = choice?.label ?: "标准"
                                    bindCamera(selector, label)
                                } catch (_: Exception) {
                                    bindCamera(baseSelector, "标准")
                                }
                            },
                            ContextCompat.getMainExecutor(context)
                        )
                    }
                } catch (e: Exception) {
                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        lensFacing = CameraSelector.LENS_FACING_BACK
                        scope.launch { snackbarHostState.showSnackbar("前置摄像头不可用，已切回后置") }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("相机初始化失败: ${e.message ?: "未知错误"}") }
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(selectedImageId) {
        val img = images.firstOrNull { it.id == selectedImageId }
        if (img != null) {
            customName = img.custom_name ?: ""
            selectedSpeciesId = img.species_id
        }
    }

    fun formatZoom(value: Float): String {
        val rounded = value.roundToInt().toFloat()
        return if (abs(value - rounded) < 0.05f) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    fun triggerFocus(offset: Offset) {
        val control = cameraControl ?: return
        focusPoint = offset
        focusVisible = true
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(offset.x, offset.y)
        val builder = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
        if (focusLockEnabled) {
            builder.setAutoCancelDuration(60, TimeUnit.SECONDS)
        } else {
            builder.setAutoCancelDuration(3, TimeUnit.SECONDS)
        }
        control.startFocusAndMetering(builder.build())
    }

    LaunchedEffect(focusPoint, focusLockEnabled) {
        if (focusPoint == null) return@LaunchedEffect
        delay(if (focusLockEnabled) 1400L else 800L)
        focusVisible = false
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            GradientHeaderCard(
                title = "连续拍照",
                subtitle = "本地优先保存，拍照后可关联物种",
                trailing = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text("当前数据集")
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { datasetMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val current = datasets.firstOrNull { it.id == selectedDatasetId }
                        Text(
                            current?.name ?: "默认数据集",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            text = { Text("新建数据集") },
                            onClick = {
                                datasetMenuExpanded = false
                                navController.navigate("datasets")
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = sampleCodeInput,
                    onValueChange = { sampleCodeInput = it },
                    label = { Text("本次样品编号") },
                    supportingText = { Text("将写入照片名称，便于后续追溯") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("保存相册")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = savingToAlbum,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    savingToAlbum = true
                                    persistSaveToAlbum(true)
                                } else {
                                    storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            } else {
                                savingToAlbum = checked
                                persistSaveToAlbum(checked)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("镜头")
                    Spacer(modifier = Modifier.width(8.dp))
                    val lensLabel = cameraOptions.firstOrNull { it.cameraId == selectedCameraId }?.label
                    val facingLabel = if (lensFacing == CameraSelector.LENS_FACING_BACK) "后置" else "前置"
                    Text(
                        if (lensLabel != null) "$facingLabel · $lensLabel" else facingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "切换镜头")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("画质")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        activeExtensionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cameraOptions.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cameraOptions, key = { it.cameraId }) { option ->
                            FilterChip(
                                selected = option.cameraId == selectedCameraId,
                                onClick = { selectedCameraId = option.cameraId },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(cameraControl, minZoom, maxZoom, zoomRatio) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (cameraControl != null) {
                                    val newZoom = (zoomRatio * zoomChange).coerceIn(minZoom, maxZoom)
                                    zoomRatio = newZoom
                                    cameraControl?.setZoomRatio(newZoom)
                                }
                            }
                        }
                        .pointerInput(cameraControl, focusLockEnabled) {
                            detectTapGestures(
                                onTap = { offset -> triggerFocus(offset) },
                                onDoubleTap = { offset ->
                                    triggerFocus(offset)
                                }
                            )
                        }
                ) {
                    if (hasPermission) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("请授权相机权限")
                        }
                    }
                    if (showGrid) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineColor = Color.White.copy(alpha = 0.3f)
                            val stroke = 1.dp.toPx()
                            val thirdW = size.width / 3f
                            val thirdH = size.height / 3f
                            drawLine(
                                color = lineColor,
                                start = Offset(thirdW, 0f),
                                end = Offset(thirdW, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(thirdW * 2f, 0f),
                                end = Offset(thirdW * 2f, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, thirdH),
                                end = Offset(size.width, thirdH),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, thirdH * 2f),
                                end = Offset(size.width, thirdH * 2f),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    if (showScaleGuide) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val y = size.height - 42.dp.toPx()
                            val start = 28.dp.toPx()
                            val end = start + size.width.coerceAtMost(180.dp.toPx()) / 3f
                            val stroke = 3.dp.toPx()
                            drawLine(Color.White, Offset(start, y), Offset(end, y), strokeWidth = stroke)
                            drawLine(Color.White, Offset(start, y - 7.dp.toPx()), Offset(start, y + 7.dp.toPx()), strokeWidth = stroke)
                            drawLine(Color.White, Offset(end, y - 7.dp.toPx()), Offset(end, y + 7.dp.toPx()), strokeWidth = stroke)
                        }
                    }
                    val point = focusPoint
                    if (point != null) {
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (point.x - focusRingPx / 2f).roundToInt(),
                                        (point.y - focusRingPx / 2f).roundToInt()
                                    )
                                }
                                .size(focusRingSize)
                                .graphicsLayer(
                                    alpha = focusAlpha,
                                    scaleX = focusScale,
                                    scaleY = focusScale
                                )
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("gallery") },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("去图库")
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (settings.storageMode == StorageMode.CUSTOM && settings.customRootUri.isNullOrBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("请先在设置中选择自定义路径") }
                            return@OutlinedButton
                        }
                        val file = createSystemCaptureFile(context)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        systemPhotoFile = file
                        systemCameraLauncher.launch(uri)
                    },
                    shape = RoundedCornerShape(14.dp),
                    enabled = hasPermission
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("系统相机")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (capturing) return@Button
                        if (settings.storageMode == StorageMode.CUSTOM && settings.customRootUri.isNullOrBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("请先在设置中选择自定义路径") }
                            return@Button
                        }
                        val capture = imageCapture ?: return@Button
                        capturing = true
                        VibrationUtil.vibrate(context, 30)
                        scope.launch {
                            val datasetId = if (selectedDatasetId.isNotBlank()) {
                                selectedDatasetId
                            } else {
                                LocalAppStore.addDataset(context, "默认数据集", "自动创建").id.also {
                                    selectedDatasetId = it
                                }
                            }
                            val file = createCaptureFile(context)
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                            try {
                                capture.takePicture(
                                    outputOptions,
                                    cameraExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                try {
                                                    val baseName = captureDisplayName(sampleCodeInput, file.nameWithoutExtension)
                                                    val autoSpeciesId = matchSpeciesIdByName(baseName, state.species)
                                                    if (savingToAlbum) {
                                                        saveToAlbum(context, file, baseName)
                                                    }
                                                    val storedUri = if (settings.storageMode == StorageMode.CUSTOM) {
                                                        val copied = StorageManager.copyToStorage(
                                                            context,
                                                            Uri.fromFile(file),
                                                            settings,
                                                            baseName
                                                        )
                                                        if (copied != null) {
                                                            file.delete()
                                                            copied
                                                        } else {
                                                            Uri.fromFile(file).toString()
                                                        }
                                                    } else {
                                                        Uri.fromFile(file).toString()
                                                    }
                                                    val newImage = PlanktonImage(
                                                        dataset_id = datasetId,
                                                        image_url = storedUri,
                                                        custom_name = baseName,
                                                        species_id = autoSpeciesId
                                                    )
                                                    LocalAppStore.addImages(context, listOf(newImage))
                                                    LocalAppStore.recordUsage(context, UsageEvent.CAPTURE)
                                                    selectedImageId = newImage.id
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar("保存失败: ${e.message ?: "未知错误"}")
                                                } finally {
                                                    capturing = false
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("拍照失败: ${exception.message ?: "未知错误"}")
                                            }
                                            capturing = false
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                capturing = false
                                snackbarHostState.showSnackbar("拍照失败: ${e.message ?: "未知错误"}")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    enabled = hasPermission && !capturing
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (capturing) "拍照中..." else "拍照")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hasPermission) {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    val showControlsSpacer =
                        maxZoom > minZoom || exposureRange.first != exposureRange.last
                    if (maxZoom > minZoom) {
                        Text("变焦", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        val zoomPresets = remember(minZoom, maxZoom) {
                            val candidates = listOf(0.6f, 1f, 2f, 3f, 4f)
                            (listOf(minZoom) + candidates + listOf(maxZoom))
                                .map { it.coerceIn(minZoom, maxZoom) }
                                .distinct()
                                .sorted()
                        }
                        if (zoomPresets.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(zoomPresets, key = { preset -> preset }) { preset ->
                                    val selected = abs(zoomRatio - preset) < 0.05f
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            zoomRatio = preset
                                            cameraControl?.setZoomRatio(preset)
                                        },
                                        label = { Text("${formatZoom(preset)}x") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Slider(
                            value = zoomRatio,
                            onValueChange = { value ->
                                val newZoom = value.coerceIn(minZoom, maxZoom)
                                zoomRatio = newZoom
                                cameraControl?.setZoomRatio(newZoom)
                            },
                            valueRange = minZoom..maxZoom
                        )
                        Text("当前倍率: ${"%.2f".format(zoomRatio)}x", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (exposureRange.first != exposureRange.last) {
                        Text("曝光补偿", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = { value ->
                                val index = value.toInt().coerceIn(exposureRange.first, exposureRange.last)
                                exposureIndex = index
                                cameraControl?.setExposureCompensationIndex(index)
                            },
                            valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                            steps = (exposureRange.last - exposureRange.first - 1).coerceAtLeast(0)
                        )
                        Text("当前 EV: $exposureIndex", style = MaterialTheme.typography.labelSmall)
                    }
                    if (showControlsSpacer) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = showGrid,
                            onClick = { showGrid = !showGrid },
                            label = { Text("网格线") }
                        )
                        FilterChip(
                            selected = showScaleGuide,
                            onClick = { showScaleGuide = !showScaleGuide },
                            label = { Text("比例尺辅助") }
                        )
                        FilterChip(
                            selected = focusLockEnabled,
                            onClick = {
                                focusLockEnabled = !focusLockEnabled
                                if (!focusLockEnabled) {
                                    cameraControl?.cancelFocusAndMetering()
                                    focusVisible = false
                                }
                            },
                            label = { Text("对焦锁定") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(title = "最近拍摄")
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images.take(20)) { img ->
                    SoftCard(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedImageId = img.id }
                    ) {
                        AsyncImage(
                            model = img.image_url,
                            contentDescription = img.custom_name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            val selectedImage = images.firstOrNull { it.id == selectedImageId }
            if (selectedImage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = "物种关联")
                Spacer(modifier = Modifier.height(8.dp))
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = selectedImage.image_url,
                        contentDescription = selectedImage.custom_name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("图片名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SpeciesAutocomplete(
                        initialValue = customName,
                        onSpeciesSelected = { species ->
                            customName = species.name_cn ?: customName
                            selectedSpeciesId = species.id
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            VibrationUtil.vibrate(context, 20)
                            scope.launch {
                                val autoSpeciesId = selectedSpeciesId
                                    ?: matchSpeciesIdByName(customName, state.species)
                                LocalAppStore.updateImage(context, selectedImage.id) { img ->
                                    img.copy(
                                        custom_name = customName.trim().ifBlank { img.custom_name },
                                        species_id = autoSpeciesId
                                    )
                                }
                                selectedSpeciesId = autoSpeciesId
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存关联")
                    }
                }
            }
        }
    }
}

private fun createCaptureFile(context: Context): File {
    val dir = StorageManager.ensureInternalDir(context)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    return File(dir, "IMG_$stamp.jpg")
}

private fun captureDisplayName(sampleCode: String, baseName: String): String {
    val prefix = sampleCode.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
    return if (prefix.isBlank()) baseName else "${prefix}_$baseName"
}

private fun createSystemCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, "camera_temp")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    return File(dir, "SYS_$stamp.jpg")
}

private fun saveToAlbum(context: Context, source: File, baseName: String) {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WaterBioAtlas")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WaterBioAtlas")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val target = File(dir, "$baseName.jpg")
        source.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
    }
}

private data class CameraOption(
    val cameraId: String,
    val label: String,
    val focalLength: Float
)

@ExperimentalCamera2Interop
private fun buildCameraOptions(
    cameraProvider: ProcessCameraProvider,
    lensFacing: Int
): List<CameraOption> {
    val options = mutableListOf<CameraOption>()
    for (info in cameraProvider.availableCameraInfos) {
        val camera2Info = Camera2CameraInfo.from(info)
        val facing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ?: continue
        val mappedFacing = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> CameraSelector.LENS_FACING_BACK
            CameraCharacteristics.LENS_FACING_FRONT -> CameraSelector.LENS_FACING_FRONT
            else -> null
        } ?: continue
        if (mappedFacing != lensFacing) continue
        val focals = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focal = focals?.minOrNull() ?: continue
        val label = buildLensLabel(focal)
        options.add(CameraOption(camera2Info.cameraId, label, focal))
    }
    return options.sortedBy { it.focalLength }
}

private data class ExtensionChoice(
    val selector: CameraSelector,
    val label: String
)

private fun resolveExtensionChoice(
    extensionsManager: ExtensionsManager,
    baseSelector: CameraSelector,
    preference: String,
    force: Boolean
): ExtensionChoice? {
    val candidates = when (preference) {
        "HDR" -> listOf(ExtensionMode.HDR to "HDR")
        "NIGHT" -> listOf(ExtensionMode.NIGHT to "夜景")
        "AUTO_ENHANCE" -> listOf(ExtensionMode.AUTO to "自动增强")
        else -> listOf(
            ExtensionMode.HDR to "HDR",
            ExtensionMode.NIGHT to "夜景",
            ExtensionMode.AUTO to "自动增强"
        )
    }
    candidates.forEach { (mode, label) ->
        val available = extensionsManager.isExtensionAvailable(baseSelector, mode)
        if (!available && !force) return@forEach
        val selector = try {
            extensionsManager.getExtensionEnabledCameraSelector(baseSelector, mode)
        } catch (_: Exception) {
            null
        }
        if (selector != null) {
            val display = if (force && !available) "${label}·强制" else label
            return ExtensionChoice(selector, display)
        }
    }
    return null
}

private fun buildLensLabel(focalLength: Float): String {
    val name = when {
        focalLength < 2.0f -> "超广角"
        focalLength < 3.5f -> "广角"
        focalLength < 5.0f -> "标准"
        else -> "长焦"
    }
    val display = String.format(Locale.US, "%.1fmm", focalLength)
    return "$name $display"
}
