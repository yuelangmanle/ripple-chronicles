
package com.dlovel.plankton.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.dlovel.plankton.data.AppSettings
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.StorageMode
import com.dlovel.plankton.service.CacheService
import com.dlovel.plankton.service.DocumentCrypto
import com.dlovel.plankton.service.GithubReleaseService
import com.dlovel.plankton.service.ReleaseInfo
import com.dlovel.plankton.service.SupabaseService
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.ScreenEnter
import com.dlovel.plankton.ui.components.SoftCard
import com.dlovel.plankton.util.ReleaseLinks
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by LocalAppStore.state.collectAsState()
    val settings = state.settings
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var homeName by remember { mutableStateOf(sanitizeSurname(settings.homeUserName)) }
    var docsUnlocked by remember { mutableStateOf(false) }
    var pendingDoc by remember { mutableStateOf<DocType?>(null) }
    var showDocDialog by remember { mutableStateOf(false) }
    var showDocPasswordDialog by remember { mutableStateOf(false) }
    var docPassword by remember { mutableStateOf("") }
    var projectSpec by remember { mutableStateOf<String?>(null) }
    var devProgress by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                docsUnlocked = false
                projectSpec = null
                devProgress = null
                showDocDialog = false
                showDocPasswordDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val rawChangelog = remember {
        context.resources.openRawResource(com.dlovel.plankton.R.raw.changelog)
            .bufferedReader()
            .use { it.readText().trim() }
    }
    val changelog = remember(rawChangelog) { formatChangelog(rawChangelog) }
    val versionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            "1.0.0"
        }
    }
    var expanded by remember { mutableStateOf(false) }
    val previewLines = 12
    val preview = remember(changelog) {
        changelog.lines().take(previewLines).joinToString("\n")
    }
    val extensionOptions = listOf(
        "AUTO" to "自动选择（推荐）",
        "HDR" to "HDR",
        "NIGHT" to "夜景",
        "AUTO_ENHANCE" to "自动增强"
    )
    LaunchedEffect(settings.homeUserName) {
        homeName = sanitizeSurname(settings.homeUserName)
    }
    val openDoc: (DocType) -> Unit = { docType ->
        pendingDoc = docType
        if (docsUnlocked) {
            showDocDialog = true
        } else {
            docPassword = ""
            showDocPasswordDialog = true
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            updateSettings(
                context,
                scope,
                settings.copy(storageMode = StorageMode.CUSTOM, customRootUri = uri.toString())
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        ScreenEnter(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            GradientHeaderCard(
                title = "设置",
                subtitle = "账号信息与应用信息"
            )
            Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "应用信息")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("开发人员: 邓梓杰")
            Text("开发单位: 江西水利电力大学")
            Text("QQ: 3335196397")
            Text("版本: $versionName")
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (checkingUpdate) return@OutlinedButton
                        checkingUpdate = true
                        scope.launch {
                            runCatching { GithubReleaseService.fetchLatestRelease() }
                                .onSuccess {
                                    latestRelease = it
                                    showUpdateDialog = true
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(
                                        "检查更新失败：" + (it.message ?: "网络不可用")
                                    )
                                }
                            checkingUpdate = false
                        }
                    }
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("检查更新")
                    }
                }
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseLinks.PROJECT_URL))
                            )
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbarHostState.showSnackbar("无法打开 GitHub 项目主页") }
                        }
                    }
                ) {
                    Text("GitHub 项目")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "首页显示")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("首页姓氏", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = homeName,
                onValueChange = { homeName = it },
                label = { Text("输入姓氏（默认邓）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "系统会自动拼接“研究员”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val nextName = sanitizeSurname(homeName).ifBlank { "邓" }
                    updateSettings(context, scope, settings.copy(homeUserName = nextName))
                }
            ) {
                Text("保存姓氏")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "存储设置")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("本地优先保存，支持自定义路径且相册不可见。", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.storageMode == StorageMode.INTERNAL,
                    onClick = {
                        updateSettings(
                            context,
                            scope,
                            settings.copy(storageMode = StorageMode.INTERNAL)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("App 私有路径（推荐）")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.storageMode == StorageMode.CUSTOM,
                    onClick = {
                        updateSettings(
                            context,
                            scope,
                            settings.copy(storageMode = StorageMode.CUSTOM)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("自定义路径（相册不可见）")
            }
            if (settings.storageMode == StorageMode.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Text("选择自定义路径")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "当前路径: ${resolveFolderName(context, settings)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("拍照同时保存到相册", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.saveToAlbum,
                    onCheckedChange = { checked ->
                        updateSettings(
                            context,
                            scope,
                            settings.copy(saveToAlbum = checked)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "导出设置")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("图片导出质量", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = settings.exportQuality.toFloat(),
                onValueChange = { value ->
                    updateSettings(
                        context,
                        scope,
                        settings.copy(exportQuality = value.toInt())
                    )
                },
                valueRange = 0f..100f,
                steps = 99
            )
            Text("当前质量: ${settings.exportQuality}%", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "相机画质")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("使用厂商算法增强画质（HDR/夜景/自动增强）", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("启用画质增强", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.enableExtensions,
                    onCheckedChange = { checked ->
                        updateSettings(context, scope, settings.copy(enableExtensions = checked))
                    }
                )
            }
            if (settings.enableExtensions) {
                Spacer(modifier = Modifier.height(10.dp))
                extensionOptions.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.extensionMode == value,
                            onClick = { updateSettings(context, scope, settings.copy(extensionMode = value)) }
                        )
                        Text(label)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("强制尝试增强（长焦/超广）", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.forceExtensions,
                        onCheckedChange = { checked ->
                            updateSettings(context, scope, settings.copy(forceExtensions = checked))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "隐私与同步")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (SupabaseService.isConfigured) "云端配置已就绪；同步仅在完成账号认证后启用。"
                else "云端同步尚未配置；当前数据始终只保存在本地。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "待同步操作：${state.pendingSyncOperations.count { it.conflictState == "PENDING" }}，冲突：${state.pendingSyncOperations.count { it.conflictState == "CONFLICT" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("匿名本地使用统计", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.telemetryEnabled,
                    onCheckedChange = { enabled ->
                        updateSettings(context, scope, settings.copy(telemetryEnabled = enabled))
                    }
                )
            }
            if (settings.telemetryEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "仅保存在本机：拍摄 ${state.usageMetrics.captures}，导入 ${state.usageMetrics.imports}，导出 ${state.usageMetrics.exports}。不记录图片、位置或物种数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { scope.launch { LocalAppStore.clearUsage(context) } }) {
                    Text("清除本地统计")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "视觉与动效")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("主题", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (value, label) ->
                    FilterChip(
                        selected = settings.themeMode == value,
                        onClick = {
                            updateSettings(context, scope, settings.copy(themeMode = value))
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("动效强度", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.animationScale,
                onValueChange = { value ->
                    updateSettings(context, scope, settings.copy(animationScale = value))
                },
                valueRange = 0f..1.5f,
                steps = 2
            )
            Text(
                when {
                    settings.animationScale <= 0.05f -> "关闭"
                    settings.animationScale < 0.75f -> "减少"
                    settings.animationScale > 1.25f -> "增强"
                    else -> "标准"
                },
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "缓存管理")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("清理临时缓存与历史分享文件。", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { showClearCacheDialog = true }) {
                Text("清理缓存")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "项目文档")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text("查看项目说明与开发进展。", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { openDoc(DocType.PROJECT_SPEC) }) {
                    Text("项目说明书")
                }
                OutlinedButton(onClick = { openDoc(DocType.DEV_PROGRESS) }) {
                    Text("开发进展书")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = "更新日志")
        Spacer(modifier = Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (expanded) changelog else preview + "\n...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "查看全部")
            }
        }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("将删除临时缓存与历史分享文件，是否继续？") },
            confirmButton = {
                Button(onClick = {
                    showClearCacheDialog = false
                    scope.launch {
                        CacheService.clearAllCache(context)
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                        snackbarHostState.showSnackbar("缓存已清理")
                    }
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDocPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showDocPasswordDialog = false },
            title = { Text("验证文档密码") },
            text = {
                OutlinedTextField(
                    value = docPassword,
                    onValueChange = { docPassword = it },
                    label = { Text("请输入密码") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            DocumentCrypto.decrypt(
                                context,
                                com.dlovel.plankton.R.raw.project_spec,
                                docPassword
                            ) to DocumentCrypto.decrypt(
                                context,
                                com.dlovel.plankton.R.raw.dev_progress,
                                docPassword
                            )
                        }.onSuccess { (spec, progress) ->
                            projectSpec = formatDoc(spec)
                            devProgress = formatDoc(progress)
                            docsUnlocked = true
                            showDocPasswordDialog = false
                            showDocDialog = true
                        }.onFailure {
                            scope.launch { snackbarHostState.showSnackbar("密码错误，无法解密项目文档") }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDocPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showUpdateDialog && latestRelease != null) {
        val release = latestRelease!!
        val hasNewVersion = isNewerVersion(release.tagName, versionName)
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(if (hasNewVersion) "发现新版本" else "当前已是最新版本") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = release.title + "（" + release.tagName + "）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (release.notes.isBlank()) "本次发布没有填写更新说明。" else release.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (release.downloadUrl == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "该版本暂未上传 APK。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (release.downloadUrl != null) {
                    Button(
                        onClick = {
                            runCatching {
                                GithubReleaseService.enqueueApkDownload(context, release)
                            }.onSuccess {
                                showUpdateDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "已开始下载，完成后请点击系统通知安装"
                                    )
                                }
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "下载失败：" + (it.message ?: "未知错误")
                                    )
                                }
                            }
                        }
                    ) {
                        Text("下载更新")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showDocDialog && pendingDoc != null) {
        val title = if (pendingDoc == DocType.PROJECT_SPEC) "项目说明书" else "开发进展书"
        val content = if (pendingDoc == DocType.PROJECT_SPEC) {
            projectSpec.orEmpty()
        } else {
            devProgress.orEmpty()
        }
        Dialog(onDismissRequest = { showDocDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(content, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun updateSettings(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, next: AppSettings) {
    scope.launch {
        LocalAppStore.updateSettings(context, next)
    }
}

private fun formatChangelog(raw: String): String {
    return raw.lineSequence().map { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@map ""
        val withoutStars = trimmed.replace("**", "")
        val withoutHashes = withoutStars.trimStart('#', ' ').trim()
        if (withoutHashes.startsWith("-")) {
            "• " + withoutHashes.removePrefix("-").trim()
        } else {
            withoutHashes
        }
    }.joinToString("\n").trim()
}

private fun formatDoc(raw: String): String {
    return raw.lineSequence().map { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@map ""
        val withoutHashes = trimmed.trimStart('#', ' ').trim()
        if (withoutHashes.startsWith("-")) {
            "• " + withoutHashes.removePrefix("-").trim()
        } else {
            withoutHashes
        }
    }.joinToString("\n").trim()
}

private fun sanitizeSurname(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.removeSuffix("研究员").trim()
}

private fun isNewerVersion(latest: String, current: String): Boolean {
    fun parse(value: String): List<Int> {
        return value.removePrefix("v")
            .split(".")
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }

    val latestParts = parse(latest)
    val currentParts = parse(current)
    val size = maxOf(latestParts.size, currentParts.size)
    for (index in 0 until size) {
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

private enum class DocType {
    PROJECT_SPEC,
    DEV_PROGRESS
}

private fun resolveFolderName(context: android.content.Context, settings: AppSettings): String {
    val uri = settings.customRootUri ?: return "未选择"
    val doc = DocumentFile.fromTreeUri(context, android.net.Uri.parse(uri)) ?: return "未选择"
    return doc.name ?: "未选择"
}
