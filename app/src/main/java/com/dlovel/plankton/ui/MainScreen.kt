
package com.dlovel.plankton.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dlovel.plankton.data.LocalAppStore
import com.dlovel.plankton.service.CacheService
import com.dlovel.plankton.ui.screens.*

@ExperimentalCamera2Interop
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        CacheService.cleanupTempCache(context)
        LocalAppStore.load(context)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val items = listOf(
        "home" to Icons.Default.Home,
        "datasets" to Icons.Default.List,
        "gallery" to Icons.Default.DateRange,
        "taxonomy" to Icons.Default.Search,
        "settings" to Icons.Default.Settings
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                items.forEach { (route, icon) ->
                    val label = when (route) {
                        "home" -> "首页"
                        "datasets" -> "数据集"
                        "gallery" -> "图库"
                        "taxonomy" -> "分类"
                        "settings" -> "设置"
                        else -> route
                    }
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo("home") { saveState = true }
                            }
                        },
                    icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("camera") { CameraScreen(navController) }
            composable("datasets") { DatasetsScreen() }
            composable("gallery") { GalleryScreen(navController) }
            composable("taxonomy") { TaxonomyScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
