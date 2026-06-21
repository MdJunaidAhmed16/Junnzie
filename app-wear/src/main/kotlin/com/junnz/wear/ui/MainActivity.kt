package com.junnz.wear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.junnz.shared.domain.Reminder
import com.junnz.wear.capture.CaptureActivity
import com.junnz.wear.ui.screens.ReminderDetailScreen
import com.junnz.wear.ui.screens.ReminderListScreen
import com.junnz.wear.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JunnzWearTheme {
                JunnzWearNavGraph(
                    viewModel = viewModel,
                    onLaunchCapture = { startCaptureActivity() },
                )
            }
        }
    }

    private fun startCaptureActivity() {
        startActivity(Intent(this, CaptureActivity::class.java))
    }
}

@Composable
private fun JunnzWearNavGraph(
    viewModel: MainViewModel,
    onLaunchCapture: () -> Unit,
) {
    val navController = rememberSwipeDismissableNavController()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var selectedReminder by remember { mutableStateOf<Reminder?>(null) }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) {
            CaptureHomeScreen(
                onCaptureTap = onLaunchCapture,
                onRecentTap = { navController.navigate(Screen.Recent.route) },
            )
        }

        composable(Screen.Recent.route) {
            ReminderListScreen(
                reminders = reminders,
                onReminderTap = { reminder ->
                    selectedReminder = reminder
                    navController.navigate(Screen.Detail.route)
                },
                onCaptureTap = onLaunchCapture,
            )
        }

        composable(Screen.Detail.route) {
            val reminder = selectedReminder ?: return@composable
            ReminderDetailScreen(
                reminder = reminder,
                onAction = { action ->
                    viewModel.sendAction(reminder.id, action)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// ── Screen 1: Home ────────────────────────────────────────────────────────────

@Composable
private fun CaptureHomeScreen(onCaptureTap: () -> Unit, onRecentTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground),
    ) {
        TimeText(modifier = Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Ready to capture",
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                color = WearOnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap the mic and\nspeak your reminder",
                style = MaterialTheme.typography.caption2,
                color = WearOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onCaptureTap,
                modifier = Modifier.size(72.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = JunnzAmber),
            ) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Start capture",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        // Recent chip pinned to bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        ) {
            Chip(
                onClick = onRecentTap,
                modifier = Modifier.fillMaxWidth(0.65f),
                colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                icon = {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.SmallIconSize),
                    )
                },
                label = { Text("Recent", style = MaterialTheme.typography.caption1) },
            )
        }
    }
}

private sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Recent : Screen("recent")
    object Detail : Screen("detail")
}
