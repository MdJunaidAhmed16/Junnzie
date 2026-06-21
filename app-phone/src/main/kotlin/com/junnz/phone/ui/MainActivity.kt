package com.junnz.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.junnz.phone.data.settings.SettingsRepository
import com.junnz.phone.data.settings.ThemeMode
import com.junnz.phone.service.JunnzForegroundService
import com.junnz.phone.ui.components.NavTab
import com.junnz.phone.ui.screens.*
import com.junnz.phone.ui.theme.JunnzTheme
import com.junnz.phone.ui.viewmodel.ReminderViewModel
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val startupPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* results ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStartupPermissions()
        // Starts context detection; the service self-stops if nothing to watch.
        JunnzForegroundService.start(this)
        setContent {
            val themeMode by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val darkTheme = when (themeMode?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            JunnzTheme(darkTheme = darkTheme) {
                JunnzNavGraph(viewModel = viewModel)
            }
        }
    }

    private fun requestStartupPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (wanted.isNotEmpty()) startupPermissions.launch(wanted.toTypedArray())
    }
}

@Composable
private fun JunnzNavGraph(viewModel: ReminderViewModel) {
    val navController = rememberNavController()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    var selectedReminder by remember { mutableStateOf<Reminder?>(null) }
    var contextReminder by remember { mutableStateOf<Reminder?>(null) }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ReminderListScreen(
                reminders = reminders,
                onReminderTap = { reminder ->
                    selectedReminder = reminder
                    navController.navigate("edit")
                },
                onCaptureTap = { navController.navigate("create") },
                onSettingsTap = { navController.switchTab("settings") },
                onCompleteTap = { id -> viewModel.complete(id) },
                onSelectTab = { tab -> navController.switchTab(tab.route()) },
                onShoppingContextTap = {
                    contextReminder = reminders.firstOrNull { r ->
                        r.triggers.any { it is Trigger.AppContextTrigger }
                    }
                    navController.navigate("overlay")
                },
            )
        }

        composable("explore") {
            ExploreScreen(
                onCaptureTap = { navController.navigate("create") },
                onSelectTab = { tab -> navController.switchTab(tab.route()) },
            )
        }

        composable("activity") {
            ActivityScreen(
                reminders = allReminders,
                onReminderTap = { reminder ->
                    selectedReminder = reminder
                    navController.navigate("edit")
                },
                onCaptureTap = { navController.navigate("create") },
                onSelectTab = { tab -> navController.switchTab(tab.route()) },
            )
        }

        composable("create") {
            CreateReminderScreen(
                onSave = { reminder ->
                    viewModel.save(reminder)
                    navController.popBackStack()
                },
                onVoiceTap = { navController.navigate("capture") },
                onBack = { navController.popBackStack() },
            )
        }

        composable("capture") {
            VoiceCaptureScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable("edit") {
            val reminder = selectedReminder ?: return@composable
            EditReminderScreen(
                reminder = reminder,
                onSave = { updated ->
                    viewModel.save(updated)
                    navController.popBackStack()
                },
                onDelete = {
                    viewModel.dismiss(reminder.id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.switchTab("list") },
                onCaptureTap = { navController.navigate("create") },
                onSelectTab = { tab -> navController.switchTab(tab.route()) },
            )
        }

        dialog(
            "overlay",
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val r = contextReminder
            val appLabel = r?.triggers
                ?.filterIsInstance<Trigger.AppContextTrigger>()
                ?.firstOrNull()
                ?.label
                ?.substringBefore(",")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Blinkit"
            ContextReminderOverlay(
                reminderText = r?.text ?: "buy cucumber",
                appName = appLabel,
                onPrimary = { navController.popBackStack() },
                onSnooze = { navController.popBackStack() },
                onMarkDone = {
                    r?.let { viewModel.complete(it.id) }
                    navController.popBackStack()
                },
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}

/** Maps a primary tab to its top-level route. */
private fun NavTab.route(): String = when (this) {
    NavTab.Home -> "list"
    NavTab.Explore -> "explore"
    NavTab.Activity -> "activity"
    NavTab.Profile -> "settings"
}

/**
 * Switches between primary tabs without stacking duplicate destinations —
 * pops back to the home graph root, then opens the target tab as a single top.
 */
private fun NavController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo("list") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
