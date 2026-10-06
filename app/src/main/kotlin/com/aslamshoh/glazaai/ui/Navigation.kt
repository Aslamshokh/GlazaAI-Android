package com.aslamshoh.glazaai.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aslamshoh.glazaai.util.VoiceCommand

/** Какая вкладка нижнего меню подсвечена на этом маршруте. */
private fun tabFor(route: String?): AppTab? = when {
    route == null -> AppTab.HOME
    route.startsWith(Routes.HOME) || route.startsWith(Routes.FIND) -> AppTab.HOME
    route.startsWith(Routes.NAV) -> AppTab.NAV
    route.startsWith("scan/") -> AppTab.SCAN
    route == Routes.HISTORY -> AppTab.HISTORY
    route == Routes.PROFILE || route == Routes.SETTINGS || route == Routes.OFFLINE || route == Routes.PRO || route == Routes.MEMORY -> AppTab.PROFILE
    else -> null
}

@Composable
fun GlazaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // На экране тарифов нижнего меню нет — как на макете.
    val showBar = route != Routes.PRO

    Scaffold(
        containerColor = Theme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                GlazaBottomBar(selected = tabFor(route)) { tab -> openTab(navController, tab) }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().background(Theme.background).padding(padding),
            // Без анимации перехода: на экранах с камерой старый экран должен сразу отпустить камеру.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onChip = { chip -> openChip(navController, chip) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onVoiceCommand = { command -> openVoiceCommand(navController, command) }
                )
            }
            composable(
                route = "${Routes.NAV}?dest={dest}",
                arguments = listOf(navArgument("dest") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                NavigationScreen(initialDestination = entry.arguments?.getString("dest").orEmpty())
            }
            composable(Routes.BARCODE) {
                BarcodeScreen(
                    mode = ScanMode.PRODUCT,
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.QR) {
                BarcodeScreen(
                    mode = ScanMode.QR,
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.TEXT) {
                TextScreen(
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CURRENCY) {
                CurrencyScreen(
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Routes.FIND}?query={query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                FindScreen(
                    initialQuery = entry.arguments?.getString("query").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenHistory = { openTab(navController, AppTab.HISTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenOffline = { navController.navigate(Routes.OFFLINE) },
                    onOpenMemory = { navController.navigate(Routes.MEMORY) },
                    onOpenPro = { navController.navigate(Routes.PRO) }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.MEMORY) { MemoryScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.OFFLINE) { OfflineModelsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PRO) { ProScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun openTab(nav: NavHostController, tab: AppTab) {
    if (tab == AppTab.HOME) {
        // «Главная» — это начало стека: возвращаемся к нему, а не наслаиваем второй экземпляр.
        if (!nav.popBackStack(Routes.HOME, false)) nav.navigate(Routes.HOME) { launchSingleTop = true }
        return
    }
    nav.navigate(tab.route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

private fun openChip(nav: NavHostController, chip: HomeChip) {
    when (chip) {
        HomeChip.OBJECTS -> Unit
        HomeChip.TEXT -> nav.navigate(Routes.TEXT) { launchSingleTop = true }
        HomeChip.PRODUCT -> nav.navigate(Routes.BARCODE) { launchSingleTop = true }
        HomeChip.CURRENCY -> nav.navigate(Routes.CURRENCY) { launchSingleTop = true }
        HomeChip.FIND -> nav.navigate(Routes.FIND) { launchSingleTop = true }
    }
}

private fun openScanMode(nav: NavHostController, mode: ScanMode) {
    nav.navigate(mode.route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

private fun openVoiceCommand(nav: NavHostController, command: VoiceCommand) {
    when (command) {
        is VoiceCommand.Find -> nav.navigate("${Routes.FIND}?query=${Uri.encode(command.query)}") { launchSingleTop = true }
        is VoiceCommand.Navigate -> nav.navigate("${Routes.NAV}?dest=${Uri.encode(command.destination.orEmpty())}") {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        VoiceCommand.ReadText -> nav.navigate(Routes.TEXT) { launchSingleTop = true }
        VoiceCommand.Currency -> nav.navigate(Routes.CURRENCY) { launchSingleTop = true }
        VoiceCommand.Product -> nav.navigate(Routes.BARCODE) { launchSingleTop = true }
        VoiceCommand.Qr -> nav.navigate(Routes.QR) { launchSingleTop = true }
        VoiceCommand.History -> openTab(nav, AppTab.HISTORY)
        VoiceCommand.WhatsAround, VoiceCommand.Unknown -> Unit
        // Память вещей обрабатывает главный экран сам (ему нужен кадр камеры); сюда доходит
        // только запасной вариант «где X», когда X не запомнен, — это обычный поиск камерой.
        is VoiceCommand.Recall -> nav.navigate("${Routes.FIND}?query=${Uri.encode(command.item)}") { launchSingleTop = true }
        is VoiceCommand.Remember, is VoiceCommand.Forget, VoiceCommand.MemoryList -> nav.navigate(Routes.MEMORY) { launchSingleTop = true }
    }
}
