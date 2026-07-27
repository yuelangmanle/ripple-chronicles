package com.dlovel.plankton.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.Species

@Composable
fun TaxonomyMindMap(speciesList: List<Species>) {
    val tree = remember(speciesList) { buildTree(speciesList) }
    var expanded by remember { mutableStateOf(setOf(tree.id)) }
    var zoom by remember { mutableStateOf(1f) }
    var horizontalOffset by remember { mutableStateOf(0f) }
    val visibleNodes = remember(tree, expanded) { flattenTree(tree, expanded) }
    val allBranchIds = remember(tree) { collectBranchIds(tree) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.75f, 1.8f)
        horizontalOffset += panChange.x
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { expanded = allBranchIds }) { Text("展开全部") }
            TextButton(onClick = { expanded = setOf(tree.id) }) { Text("定位根节点") }
            Text("缩放 ${"%.0f".format(zoom * 100)}%", style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = horizontalOffset
                )
                .transformable(transformState),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visibleNodes) { node ->
                val hasChildren = node.children.isNotEmpty()
                val isExpanded = expanded.contains(node.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasChildren) {
                            expanded = if (isExpanded) expanded - node.id else expanded + node.id
                        }
                        .padding(start = (node.level * 14).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasChildren) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = if (isExpanded) "收起 ${node.title}" else "展开 ${node.title}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = node.title,
                        style = when (node.level) {
                            0 -> MaterialTheme.typography.titleMedium
                            1 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.bodySmall
                        }
                    )
                    if (node.count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${node.count})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class MindMapNode(
    val id: String,
    val title: String,
    val level: Int,
    val count: Int,
    val children: List<MindMapNode>
)

private fun buildTree(speciesList: List<Species>): MindMapNode {
    val rootName = "水生生物"
    val groupedByCategory = speciesList.groupBy { it.category.ifBlank { "未分类" } }

    val categoryNodes = groupedByCategory.map { (category, list) ->
        val speciesNodes = list
            .sortedBy { it.name_cn ?: it.name_latin ?: "" }
            .map { sp ->
                MindMapNode(
                    id = "sp:${sp.id}",
                    title = sp.name_cn ?: sp.name_latin ?: "未命名",
                    level = 2,
                    count = 0,
                    children = emptyList()
                )
            }
        MindMapNode(
            id = "cat:$category",
            title = category,
            level = 1,
            count = list.size,
            children = speciesNodes
        )
    }.sortedBy { it.title }

    return MindMapNode(
        id = "root",
        title = rootName,
        level = 0,
        count = speciesList.size,
        children = categoryNodes
    )
}

private fun flattenTree(root: MindMapNode, expanded: Set<String>): List<MindMapNode> {
    val result = mutableListOf<MindMapNode>()
    fun traverse(node: MindMapNode) {
        result.add(node)
        if (expanded.contains(node.id)) {
            node.children.forEach { child -> traverse(child) }
        }
    }
    traverse(root)
    return result
}

private fun collectBranchIds(root: MindMapNode): Set<String> {
    val ids = mutableSetOf<String>()
    fun collect(node: MindMapNode) {
        if (node.children.isNotEmpty()) {
            ids += node.id
            node.children.forEach(::collect)
        }
    }
    collect(root)
    return ids
}
