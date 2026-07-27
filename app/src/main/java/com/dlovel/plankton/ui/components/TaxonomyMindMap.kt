package com.dlovel.plankton.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.Species

@Composable
fun TaxonomyMindMap(speciesList: List<Species>) {
    val tree = remember(speciesList) { buildTree(speciesList) }
    var expanded by remember { mutableStateOf(setOf(tree.id)) }
    val visibleNodes = remember(tree, expanded) { flattenTree(tree, expanded) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        contentDescription = null,
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
