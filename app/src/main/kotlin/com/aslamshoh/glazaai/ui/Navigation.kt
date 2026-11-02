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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aslamshoh.glazaai.util.SharedInbox
import com.aslamshoh.glazaai.util.VoiceCommand

/** Какая вкладка нижнего меню подсвечена на этом маршруте. */
private fun tabFor(route: String?): AppTab? = when {
    route == null -> AppTab.HOME
    route.startsWith(Routes.HOME) || route.startsWith(Routes.FIND) -> AppTab.HOME
    route == Routes.NEAR -> AppTab.NEAR
    route.startsWith("scan/") -> AppTab.SCAN
    route == Routes.HISTORY -> AppTab.HISTORY
    route.startsWith(Routes.HELP) || route == Routes.PROFILE || route == Routes.SETTINGS || route == Routes.OFFLINE || route == Routes.PRO || route == Routes.FEEDBACK -> AppTab.PROFILE
    else -> null
}

@Composable
fun GlazaNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // На экране тарифов нижнего меню нет — как на макете.
    val showBar = route != Routes.PRO

    // «Поделиться» из другого приложения: картинка приходит через SharedInbox, открываем экран описания.
    var sharedUri by remember { mutableStateOf<Uri?>(null) }
    val incoming = SharedInbox.pending
    LaunchedEffect(incoming) {
        val u = SharedInbox.take()
        if (u != null) {
            sharedUri = u
            navController.navigate(Routes.SHARED) { launchSingleTop = true }
        }
    }

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
                    onHelp = { navController.navigate("${Routes.HELP}?auto=0") { launchSingleTop = true } },
                    onVoiceCommand = { command -> openVoiceCommand(navController, command) }
                )
            }
            composable(Routes.BARCODE) {
                BarcodeScreen(
                    mode = ScanMode.PRODUCT,
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
            composable(Routes.DOCUMENT) {
                DocumentScreen(
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.INSPECT) {
                InspectScreen(
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${Routes.MEDIA}?kind={kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                MediaScreen(
                    initialKind = entry.arguments?.getString("kind").orEmpty(),
                    onSwitchMode = { openScanMode(navController, it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.NEAR) { NearScreen(onBack = { navController.popBackStack() }) }
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
                    onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                    onOpenHelp = { navController.navigate("${Routes.HELP}?auto=0") { launchSingleTop = true } },
                    onOpenPro = { navController.navigate(Routes.PRO) }
                )
            }
            composable(
                route = "${Routes.HELP}?auto={auto}",
                arguments = listOf(navArgument("auto") { type = NavType.IntType; defaultValue = 0 })
            ) { entry ->
                HelpScreen(
                    autoStart = (entry.arguments?.getInt("auto") ?: 0) == 1,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.FEEDBACK) { FeedbackScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.OFFLINE) { OfflineModelsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.PRO) { ProScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SHARED) {
                val u = sharedUri
                if (u != null) SharedImageScreen(uri = u, onBack = { navController.popBackStack() })
            }
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
        HomeChip.HAND -> nav.navigate(Routes.INSPECT) { launchSingleTop = true }
        HomeChip.FIND -> nav.navigate(Routes.FIND) { launchSingleTop = true }
    }
}

private fun openScanMode(nav: NavHostController, mode: ScanMode) {
    val route = if (mode == ScanMode.MEDIA) "${Routes.MEDIA}?kind=" else mode.route
    nav.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

private fun openVoiceCommand(nav: NavHostController, command: VoiceCommand) {
    when (command) {
        is VoiceCommand.Find -> nav.navigate("${Routes.FIND}?query=${Uri.encode(command.query)}") { launchSingleTop = true }
        VoiceCommand.ReadText -> nav.navigate(Routes.TEXT) { launchSingleTop = true }
        VoiceCommand.Currency -> nav.navigate(Routes.CURRENCY) { launchSingleTop = true }
        VoiceCommand.Product -> nav.navigate(Routes.BARCODE) { launchSingleTop = true }
        VoiceCommand.Document -> nav.navigate(Routes.DOCUMENT) { launchSingleTop = true }
        VoiceCommand.Inspect -> nav.navigate(Routes.INSPECT) { launchSingleTop = true }
        VoiceCommand.Feedback -> nav.navigate(Routes.FEEDBACK) { launchSingleTop = true }
        VoiceCommand.Near -> openTab(nav, AppTab.NEAR)
        is VoiceCommand.Media -> nav.navigate("${Routes.MEDIA}?kind=${command.kind}") { launchSingleTop = true }
        is VoiceCommand.Help -> nav.navigate("${Routes.HELP}?auto=${if (command.volunteer) 1 else 0}") { launchSingleTop = true }
        VoiceCommand.History -> openTab(nav, AppTab.HISTORY)
        VoiceCommand.WhatsAround, VoiceCommand.Detail, VoiceCommand.People, VoiceCommand.Stop, is VoiceCommand.ColorOf, VoiceCommand.Unknown -> Unit
    }
}
