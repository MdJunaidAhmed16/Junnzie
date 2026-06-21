package com.junnz.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.UserAction
import com.junnz.wear.ui.theme.JunnzAmber
import com.junnz.wear.ui.theme.JunnzCoral
import com.junnz.wear.ui.theme.WearBackground

@Composable
fun ReminderDetailScreen(
    reminder: Reminder,
    onAction: (UserAction.Action) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = reminder.text,
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Chip(
                    onClick = { onAction(UserAction.Action.COMPLETE) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1C4220)),
                    label = { Text("✓  Done", color = Color(0xFF4CAF82)) },
                )
            }

            item {
                Chip(
                    onClick = { onAction(UserAction.Action.SNOOZE_5M) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2C2C1A)),
                    label = { Text("⏱  Snooze 5 min", color = JunnzAmber) },
                )
            }

            item {
                Chip(
                    onClick = { onAction(UserAction.Action.SNOOZE_1H) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2C2C1A)),
                    label = { Text("⏰  Snooze 1 hour", color = JunnzAmber) },
                )
            }

            item {
                Chip(
                    onClick = { onAction(UserAction.Action.DISMISS) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A1A1A)),
                    label = { Text("✕  Dismiss", color = JunnzCoral) },
                )
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}
