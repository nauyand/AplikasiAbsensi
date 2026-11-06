package com.teknosolvo.personalhub.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.teknosolvo.personalhub.AppContainer
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.dashboard.DashboardScreen
import com.teknosolvo.personalhub.ui.dashboard.NotificationCenterScreen
import com.teknosolvo.personalhub.ui.dashboard.OnboardingScreen
import com.teknosolvo.personalhub.ui.personal.NoteEditScreen
import com.teknosolvo.personalhub.ui.personal.PersonalHubScreen
import com.teknosolvo.personalhub.ui.search.SearchScreen
import com.teknosolvo.personalhub.ui.settings.BackupScreen
import com.teknosolvo.personalhub.ui.settings.GpsSettingsScreen
import com.teknosolvo.personalhub.ui.settings.NotificationSettingsScreen
import com.teknosolvo.personalhub.ui.settings.PrivacyDashboardScreen
import com.teknosolvo.personalhub.ui.settings.SecuritySettingsScreen
import com.teknosolvo.personalhub.ui.settings.SettingsScreen
import com.teknosolvo.personalhub.ui.settings.SimulationScreen
import com.teknosolvo.personalhub.ui.settings.ThemeSettingsScreen
import com.teknosolvo.personalhub.ui.tracker.ActivityEditScreen
import com.teknosolvo.personalhub.ui.tracker.PlaceEditScreen
import com.teknosolvo.personalhub.ui.tracker.TrackerHubScreen
import com.teknosolvo.personalhub.ui.vault.AccountDetailScreen
import com.teknosolvo.personalhub.ui.vault.AccountEditScreen
import com.teknosolvo.personalhub.ui.vault.AppLockGate
import com.teknosolvo.personalhub.ui.vault.GeneratorScreen
import com.teknosolvo.personalhub.ui.vault.VaultGate
import com.teknosolvo.personalhub.ui.vault.VaultHomeScreen
import com.teknosolvo.personalhub.ui.vault.VaultSettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val TRACKER = "tracker?tab={tab}"
    const val VAULT = "vault"
    const val PERSONAL = "personal?tab={tab}"
    const val SETTINGS = "settings"

    fun tracker(tab: Int = 0) = "tracker?tab=$tab"
    fun personal(tab: Int = 0) = "personal?tab=$tab"
    fun place(id: Long) = "place/$id"
    fun activity(id: Long, date: String = "") = "activity/$id?date=$date"
    fun account(id: Long) = "vault/account/$id"
    fun editAccount(id: Long) = "vault/edit/$id"
    fun note(id: Long) = "note/$id"
    const val GENERATOR = "vault/generator"
    const val VAULT_SETTINGS = "vault/settings"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val SET_SECURITY = "settings/security"
    const val SET_GPS = "settings/gps"
    const val SET_NOTIF = "settings/notifications"
    const val SET_THEME = "settings/theme"
    const val SET_BACKUP = "settings/backup"
    const val SET_PRIVACY = "settings/privacy"
    const val SET_SIMULATION = "settings/simulation"
}

private data class Tab(val route: String, val target: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, Routes.DASHBOARD, "Beranda", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    Tab(Routes.TRACKER, Routes.tracker(), "Tracker", Icons.Outlined.MyLocation, Icons.Filled.MyLocation),
    Tab(Routes.VAULT, Routes.VAULT, "Vault", Icons.Outlined.Lock, Icons.Filled.Lock),
    Tab(Routes.PERSONAL, Routes.personal(), "Personal", Icons.Outlined.EditNote, Icons.Filled.EditNote),
    Tab(Routes.SETTINGS, Routes.SETTINGS, "Setelan", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalContainer provides container) {
        val prefs by container.settings.prefs.collectAsStateWithLifecycle()
        if (!prefs.onboardingDone) {
            OnboardingScreen()
        } else {
            AppLockGate { MainShell() }
        }
    }
}

fun NavHostController.navigateTab(route: String) {
    val start = graph.findStartDestination()
    if (route == Routes.DASHBOARD) {
        // Kembali ke Beranda = buang semua layar di atasnya. JANGAN pakai saveState/restoreState di sini:
        // state yang disimpan di bawah id start destination berisi tumpukan tab lain (Setelan → Privacy, dst.),
        // sehingga restoreState memunculkan layar itu lagi dan Beranda tidak pernah tampil.
        if (!popBackStack(start.id, inclusive = false)) navigate(route) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** True bila layar saat ini sudah selesai transisi; mencegah tap ganda menumpuk layar yang sama. */
private fun NavHostController.isIdle() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun NavHostController.navigateOnce(route: String) {
    if (isIdle()) navigate(route)
}

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = tabs.any { it.route == current }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected) nav.navigateTab(tab.target) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 12 } },
        ) {
            // Jangan pernah mem-pop layar terakhir (Beranda): tap "Kembali" berulang bisa membuat NavHost kosong.
            val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }

            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenTracker = { nav.navigateTab(Routes.tracker(it)) },
                    onOpenVault = { nav.navigateTab(Routes.VAULT) },
                    onOpenNotes = { nav.navigateTab(Routes.personal(0)) },
                    onSearch = { nav.navigateOnce(Routes.SEARCH) },
                    onNotifications = { nav.navigateOnce(Routes.NOTIFICATIONS) },
                    onAddPlace = { nav.navigateOnce(Routes.place(0)) },
                )
            }
            composable(Routes.TRACKER, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) {
                TrackerHubScreen(
                    initialTab = it.arguments?.getInt("tab") ?: 0,
                    onEditPlace = { id -> nav.navigateOnce(Routes.place(id)) },
                    onEditActivity = { id, date -> nav.navigateOnce(Routes.activity(id, date)) },
                    onSearch = { nav.navigateOnce(Routes.SEARCH) },
                )
            }
            composable("place/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PlaceEditScreen(id = it.arguments?.getLong("id") ?: 0, onBack = back)
            }
            composable(
                "activity/{id}?date={date}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                ActivityEditScreen(
                    id = it.arguments?.getLong("id") ?: 0,
                    date = it.arguments?.getString("date").orEmpty(),
                    onBack = back,
                )
            }

            composable(Routes.VAULT) {
                VaultGate {
                    VaultHomeScreen(
                        onOpen = { id -> nav.navigateOnce(Routes.account(id)) },
                        onAdd = { nav.navigateOnce(Routes.editAccount(0)) },
                        onGenerator = { nav.navigateOnce(Routes.GENERATOR) },
                        onSettings = { nav.navigateOnce(Routes.VAULT_SETTINGS) },
                    )
                }
            }
            composable("vault/account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                val id = it.arguments?.getLong("id") ?: 0
                VaultGate { AccountDetailScreen(id, onBack = back, onEdit = { nav.navigateOnce(Routes.editAccount(id)) }) }
            }
            composable("vault/edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                VaultGate { AccountEditScreen(it.arguments?.getLong("id") ?: 0, onBack = back) }
            }
            composable(Routes.GENERATOR) { GeneratorScreen(onBack = back) }
            composable(Routes.VAULT_SETTINGS) { VaultGate { VaultSettingsScreen(onBack = back) } }

            composable(Routes.PERSONAL, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) {
                PersonalHubScreen(
                    initialTab = it.arguments?.getInt("tab") ?: 0,
                    onEditNote = { id -> nav.navigateOnce(Routes.note(id)) },
                    onEditActivity = { id -> nav.navigateOnce(Routes.activity(id)) },
                    onAddPersonal = { nav.navigateOnce(Routes.activity(-1)) },
                )
            }
            composable("note/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                NoteEditScreen(it.arguments?.getLong("id") ?: 0, onBack = back)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onNavigate = { r -> nav.navigateOnce(r) })
            }
            composable(Routes.SET_SECURITY) { SecuritySettingsScreen(onBack = back, onVaultSettings = { nav.navigateOnce(Routes.VAULT_SETTINGS) }) }
            composable(Routes.SET_GPS) { GpsSettingsScreen(onBack = back, onSimulation = { nav.navigateOnce(Routes.SET_SIMULATION) }) }
            composable(Routes.SET_NOTIF) { NotificationSettingsScreen(onBack = back) }
            composable(Routes.SET_THEME) { ThemeSettingsScreen(onBack = back) }
            composable(Routes.SET_BACKUP) { BackupScreen(onBack = back) }
            composable(Routes.SET_PRIVACY) { PrivacyDashboardScreen(onBack = back) }
            composable(Routes.SET_SIMULATION) { SimulationScreen(onBack = back) }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = back,
                    onOpenActivity = { id -> nav.navigateOnce(Routes.activity(id)) },
                    onOpenPlace = { id -> nav.navigateOnce(Routes.place(id)) },
                    onOpenAccount = { id -> nav.navigateOnce(Routes.account(id)) },
                    onOpenNote = { id -> nav.navigateOnce(Routes.note(id)) },
                )
            }
            composable(Routes.NOTIFICATIONS) { NotificationCenterScreen(onBack = back) }
        }
    }
}

