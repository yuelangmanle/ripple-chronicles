
package com.dlovel.plankton.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.dlovel.plankton.data.Species
import com.dlovel.plankton.data.LocalAppStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesAutocomplete(
    initialValue: String = "",
    onSpeciesSelected: (Species) -> Unit,
    onQueryChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf(initialValue) }
    var results by remember { mutableStateOf<List<Species>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val state by LocalAppStore.state.collectAsState()

    LaunchedEffect(initialValue) {
        query = initialValue
    }

    LaunchedEffect(query) {
        if (query.length < 1) {
            results = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }
        
        delay(300)
        isLoading = true
        val normalizedQuery = query.trim()
        val data = state.species.filter { species ->
            val fields = listOfNotNull(species.name_cn, species.name_latin) + species.synonyms
            fields.any { it.contains(normalizedQuery, ignoreCase = true) }
        }.take(10)
        results = data
        showDropdown = data.isNotEmpty()
        isLoading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                onQueryChanged(it)
                if (it.isEmpty()) showDropdown = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("搜索物种...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    query.isNotBlank() -> IconButton(onClick = {
                        query = ""
                        onQueryChanged("")
                        showDropdown = false
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空物种")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (showDropdown && results.isNotEmpty()) {
            Popup(
                onDismissRequest = { showDropdown = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 250.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    LazyColumn {
                        items(results) { species ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = species.name_cn ?: ""
                                        onQueryChanged(query)
                                        onSpeciesSelected(species)
                                        showDropdown = false
                                        scope.launch {
                                            delay(50)
                                            focusRequester.requestFocus()
                                        }
                                    }
                                    .padding(16.dp)
                            ) {
                                Text(species.name_cn ?: "未知", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(species.name_latin ?: "", fontSize = 12.sp, color = Color.Gray)
                            }
                            Divider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }
        }
    }
}
