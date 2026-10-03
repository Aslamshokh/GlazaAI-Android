package com.aslamshoh.glazaai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.aslamshoh.glazaai.model.RecognitionMode

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_CAPTURE = "capture/{mode}"
private const val ROUTE_BARCODE = "barcode/{mode}"

@Composable
fun GlazaNavHost() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Theme.background,
        topBar = { GlazaTopBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.fillMaxSize().background(Theme.background).let {
                it.padding(padding)
            }
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onOpenMode = { mode -> navController.navigate(routeFor(mode)) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                )
            }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
            composable(ROUTE_HISTORY) { HistoryScreen() }
            composable(ROUTE_CAPTURE) { backStackEntry ->
                val modeName = backStackEntry.arguments?.getString("mode") ?: RecognitionMode.OBJECTS.name
                val mode = RecognitionMode.entries.firstOrNull { it.name == modeName } ?: RecognitionMode.OBJECTS
                CaptureScreen(initialMode = mode)
            }
            composable(ROUTE_BARCODE) { backStackEntry ->
                val modeName = backStackEntry.arguments?.getString("mode") ?: RecognitionMode.BARCODE.name
                val mode = RecognitionMode.entries.firstOrNull { it.name == modeName } ?: RecognitionMode.BARCODE
                BarcodeScreen(mode = mode)
            }
        }
    }
}

private fun routeFor(mode: RecognitionMode): String = when (mode) {
    RecognitionMode.BARCODE, RecognitionMode.QR -> "barcode/${mode.name}"
    else -> "capture/${mode.name}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlazaTopBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: ROUTE_HOME
    val title = when {
        route == ROUTE_HOME -> "ИИ ГЛАЗ"
        route == ROUTE_SETTINGS -> "Настройки"
        route == ROUTE_HISTORY -> "История"
        route.startsWith("capture") || route.startsWith("barcode") -> {
            val modeName = backStackEntry?.arguments?.getString("mode")
            RecognitionMode.entries.firstOrNull { it.name == modeName }?.title ?: "ИИ ГЛАЗ"
        }
        else -> "ИИ ГЛАЗ"
    }

    TopAppBar(
        title = { Text(title, color = Theme.textPrimary) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Theme.background),
        actions = {
            if (route == ROUTE_HOME) {
                IconButton(onClick = { navController.navigate(ROUTE_HISTORY) }) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "История",
                        tint = Theme.textPrimary
                    )
                }
                IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Настройки",
                        tint = Theme.textPrimary
                    )
                }
            }
        },
        navigationIcon = {
            if (route != ROUTE_HOME) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Theme.textPrimary
                    )
                }
            }
        }
    )
}
