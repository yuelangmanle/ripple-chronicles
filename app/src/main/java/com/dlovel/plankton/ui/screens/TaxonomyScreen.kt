package com.dlovel.plankton.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.data.Species
import com.dlovel.plankton.ui.components.EmptyStateCard
import com.dlovel.plankton.ui.components.GradientHeaderCard
import com.dlovel.plankton.ui.components.SectionHeader
import com.dlovel.plankton.ui.components.ScreenEnter
import com.dlovel.plankton.ui.components.SoftCard
import com.dlovel.plankton.ui.components.TaxonomyMindMap
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaxonomyScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val state by LocalAppStore.state.collectAsState()
    val speciesAll = state.species
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("list") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("浮游动物") }
    var importCategory by remember { mutableStateOf("浮游动物") }
    var addName by remember { mutableStateOf("") }
    var addLatin by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<Species?>(null) }
    var editName by remember { mutableStateOf("") }
    var editLatin by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("浮游动物") }
    val defaultCategories = listOf("浮游动物", "浮游植物", "着生藻类", "底栖动物")
    val categories = remember(speciesAll) {
        val userCategories = speciesAll
            .filter { it.source.isNullOrBlank() }
            .map { it.category }
            .filter { it.isNotBlank() }
        (defaultCategories + userCategories).distinct()
    }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && !categories.contains(selectedCategory)) {
            selectedCategory = defaultCategories.first()
        }
        if (categories.isNotEmpty() && !categories.contains(editCategory)) {
            editCategory = defaultCategories.first()
        }
        if (categories.isNotEmpty() && importCategory.isNotBlank() && !categories.contains(importCategory)) {
            importCategory = defaultCategories.first()
        }
    }

    val importer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            scope.launch {
                val list = readSpeciesFromExcel(context, uri, importCategory)
                if (list.isNotEmpty()) {
                    LocalAppStore.addSpecies(context, list)
                }
                showImportDialog = false
            }
        }
    }

    val speciesList = remember(speciesAll, searchQuery) {
        if (searchQuery.isBlank()) {
            speciesAll
        } else {
            speciesAll.filter { sp ->
                (sp.name_cn ?: "").contains(searchQuery) ||
                    (sp.name_latin ?: "").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ScreenEnter(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        GradientHeaderCard(
            title = "物种分类库",
            subtitle = "检索、导入与管理物种名录",
            trailing = {
                IconButton(onClick = { viewMode = if (viewMode == "list") "map" else "list" }) {
                    Icon(
                        imageVector = if (viewMode == "list") Icons.Filled.Share else Icons.Filled.List,
                        contentDescription = if (viewMode == "list") "切换为分类树" else "切换为列表",
                        tint = Color.White
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewMode == "list") {
            SectionHeader(title = "物种列表")
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索物种...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showImportDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("导入物种名单")
                }
                OutlinedButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("新增物种")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (speciesList.isEmpty()) {
                EmptyStateCard(
                    title = "暂无物种数据",
                    subtitle = "请导入 Excel 或手动添加物种。",
                    actionLabel = "导入物种",
                    onActionClick = { showImportDialog = true }
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speciesList) { species ->
                        SoftCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        editTarget = species
                                        editName = species.name_cn ?: ""
                                        editLatin = species.name_latin ?: ""
                                        editCategory = species.category
                                    }
                                )
                        ) {
                            Text(species.name_cn ?: "未知", style = MaterialTheme.typography.titleMedium)
                            Text(
                                species.name_latin ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                species.category.ifBlank { "未分类" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            SectionHeader(title = "分类树")
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索并定位物种节点...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索物种") },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                TaxonomyMindMap(speciesList)
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入物种名单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导入模板：")
                    Text("1) 第一列为大类（浮游动物/浮游植物/着生藻类/底栖动物），第二列为物种名，第三列为拉丁名。")
                    Text("2) 使用默认大类时：第一列为物种名，第二列为拉丁名。")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("默认大类：")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = importCategory.isBlank(),
                            onClick = { importCategory = "" }
                        )
                        Text("不指定")
                    }
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = importCategory == category,
                                onClick = { importCategory = category }
                            )
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { importer.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增物种") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("中文名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addLatin,
                        onValueChange = { addLatin = it },
                        label = { Text("拉丁名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = addName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            LocalAppStore.addSpecies(
                                context,
                                listOf(
                                    Species(
                                        id = UUID.randomUUID().toString(),
                                        name_cn = name,
                                        name_latin = addLatin.trim().ifBlank { null },
                                        category = selectedCategory
                                    )
                                )
                            )
                        }
                    }
                    addName = ""
                    addLatin = ""
                    showAddDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    val target = editTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑物种") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("中文名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editLatin,
                        onValueChange = { editLatin = it },
                        label = { Text("拉丁名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = editCategory == category,
                                onClick = { editCategory = category }
                            )
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        LocalAppStore.updateSpecies(context, target.id) { sp ->
                            sp.copy(
                                name_cn = editName.trim().ifBlank { sp.name_cn },
                                name_latin = editLatin.trim().ifBlank { null },
                                category = editCategory
                            )
                        }
                    }
                    editTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        LocalAppStore.deleteSpecies(context, target.id)
                    }
                    editTarget = null
                }) { Text("删除") }
            }
        )
    }
}

private fun readSpeciesFromExcel(
    context: Context,
    uri: Uri,
    fallbackCategory: String
): List<Species> {
    val list = mutableListOf<Species>()
    val categoryOptions = setOf("浮游动物", "浮游植物", "着生藻类", "底栖动物")
    val resolvedFallback = fallbackCategory.ifBlank { "浮游动物" }
    var lastCategory = ""
    context.contentResolver.openInputStream(uri)?.use { input ->
        val workbook = WorkbookFactory.create(input)
        val sheet = workbook.getSheetAt(0)
        for (row in sheet) {
            val cells = (0..7).map { idx -> row.getCell(idx)?.toString()?.trim().orEmpty() }
            if (cells.all { it.isBlank() }) continue
            if (row.rowNum == 0 && isHeaderRow(cells)) continue
            if (isHeaderRow(cells)) continue

            val col0 = cells.getOrNull(0).orEmpty()
            val col1 = cells.getOrNull(1).orEmpty()
            val col2 = cells.getOrNull(2).orEmpty()
            val nonBlankCount = cells.count { it.isNotBlank() }
            val isCategoryTemplateRow = (categoryOptions.contains(col0) || (col0.isBlank() && lastCategory.isNotBlank())) &&
                col1.isNotBlank() && nonBlankCount <= 3
            val hasNewColumns = cells[6].isNotBlank() || cells[7].isNotBlank()

            if (isCategoryTemplateRow) {
                if (col0.isNotBlank() && categoryOptions.contains(col0)) {
                    lastCategory = col0
                }
                val match = Regex("^(.*?)\\s*\\((.*?)\\)$").find(col1)
                val nameCn = match?.groupValues?.getOrNull(1)?.trim() ?: col1
                val nameLatin = col2.ifBlank { match?.groupValues?.getOrNull(2)?.trim() }
                val category = lastCategory.ifBlank { resolvedFallback }
                list.add(
                    Species(
                        id = UUID.randomUUID().toString(),
                        name_cn = nameCn,
                        name_latin = nameLatin,
                        category = category
                    )
                )
            } else if (col0.isNotBlank() && col1.isNotBlank() && nonBlankCount <= 2) {
                val match = Regex("^(.*?)\\s*\\((.*?)\\)$").find(col0)
                val nameCn = match?.groupValues?.getOrNull(1)?.trim() ?: col0
                val nameLatin = col1.ifBlank { match?.groupValues?.getOrNull(2)?.trim() }
                list.add(
                    Species(
                        id = UUID.randomUUID().toString(),
                        name_cn = nameCn,
                        name_latin = nameLatin,
                        category = resolvedFallback
                    )
                )
            } else if (col0.isNotBlank() && nonBlankCount == 1) {
                val match = Regex("^(.*?)\\s*\\((.*?)\\)$").find(col0)
                val nameCn = match?.groupValues?.getOrNull(1)?.trim() ?: col0
                val nameLatin = match?.groupValues?.getOrNull(2)?.trim()
                list.add(
                    Species(
                        id = UUID.randomUUID().toString(),
                        name_cn = nameCn,
                        name_latin = nameLatin,
                        category = resolvedFallback
                    )
                )
            } else if (hasNewColumns) {
                val nameCnCell = cells[6]
                val nameLatinCell = cells[7]

                val raw = nameCnCell
                if (raw.isBlank()) continue
                val match = Regex("^(.*?)\\s*\\((.*?)\\)$").find(raw)
                val nameCn = match?.groupValues?.getOrNull(1)?.trim() ?: raw
                val latinFromCell = nameLatinCell.ifBlank { null }
                val nameLatin = latinFromCell ?: match?.groupValues?.getOrNull(2)?.trim()
                val category = resolvedFallback

                list.add(
                    Species(
                        id = UUID.randomUUID().toString(),
                        name_cn = nameCn,
                        name_latin = nameLatin,
                        category = category
                    )
                )
            } else {
                val legacyCells = cells.take(6)
                if (row.rowNum == 0 && isHeaderRow(legacyCells)) continue
                val isSimpleList = legacyCells.drop(1).all { it.isBlank() }
                val categoryCell = if (isSimpleList) "" else legacyCells[0]
                val speciesCell = if (isSimpleList) legacyCells[0] else legacyCells[5]

                if (categoryCell.isNotBlank()) lastCategory = categoryCell

                val raw = speciesCell
                if (raw.isBlank()) continue
                val match = Regex("^(.*?)\\s*\\((.*?)\\)$").find(raw)
                val nameCn = match?.groupValues?.getOrNull(1)?.trim() ?: raw
                val nameLatin = match?.groupValues?.getOrNull(2)?.trim()
                val category = if (isSimpleList) {
                    resolvedFallback
                } else {
                    if (categoryOptions.contains(lastCategory)) lastCategory else resolvedFallback
                }
                list.add(
                    Species(
                        id = UUID.randomUUID().toString(),
                        name_cn = nameCn,
                        name_latin = nameLatin,
                        category = category
                    )
                )
            }
        }
        workbook.close()
    }
    return list
}

private fun isHeaderRow(cells: List<String>): Boolean {
    val headerTokens = listOf(
        "四大类",
        "大类",
        "类别",
        "物种",
        "门",
        "亚门",
        "纲",
        "目",
        "科",
        "属",
        "中文名",
        "拉丁名",
        "类",
        "种",
        "Class",
        "Order",
        "Family",
        "Genus",
        "Species"
    )
    val hits = cells.count { cell -> headerTokens.contains(cell) }
    return hits >= 3
}
