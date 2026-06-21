package com.junnz.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.domain.Trigger
import com.junnz.wear.ui.theme.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ReminderListScreen(
    reminders: List<Reminder>,
    onReminderTap: (Reminder) -> Unit,
    onCaptureTap: () -> Unit,
    scalingLazyListState: ScalingLazyListState = ScalingLazyListState(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground),
    ) {
        ScalingLazyColumn(
            state = scalingLazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ListHeader {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.caption1,
                        fontWeight = FontWeight.Bold,
                        color = JunnzAmber,
                    )
                }
            }

            if (reminders.isEmpty()) {
                item {
                    Text(
                        text = "No reminders yet.\nTap mic to add one.",
                        style = MaterialTheme.typography.caption1,
                        color = WearOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                }
            } else {
                items(reminders.take(20)) { reminder ->
                    RecentReminderChip(
                        reminder = reminder,
                        onTap = { onReminderTap(reminder) },
                    )
                }
            }
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun RecentReminderChip(reminder: Reminder, onTap: () -> Unit) {
    val icon = reminderIcon(reminder)

    Chip(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
        icon = {
            Box(
                modifier = Modifier
                    .size(ChipDefaults.LargeIconSize)
                    .clip(CircleShape)
                    .background(icon.bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = icon.fgColor,
                )
            }
        },
        label = {
            Text(
                text = reminder.text,
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.SemiBold,
                color = WearOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = triggerSummary(reminder),
                style = MaterialTheme.typography.caption2,
                color = WearOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private data class ReminderIcon(
    val imageVector: ImageVector,
    val bgColor: Color,
    val fgColor: Color,
)

private fun reminderIcon(reminder: Reminder): ReminderIcon = when {
    reminder.status == ReminderStatus.FIRED ->
        ReminderIcon(Icons.Rounded.Warning, Color(0xFF3D1515), JunnzCoral)
    reminder.triggers.any { it is Trigger.TimeTrigger } ->
        ReminderIcon(Icons.Rounded.Event, Color(0xFF3D2800), JunnzAmber)
    reminder.triggers.any { it is Trigger.GeofenceTrigger } ->
        ReminderIcon(Icons.Rounded.ShoppingCart, Color(0xFF0D2B1A), JunnzGreen)
    reminder.triggers.any { it is Trigger.SemanticTrigger } ->
        ReminderIcon(Icons.Rounded.Phone, Color(0xFF0D1F3D), Color(0xFF64B5F6))
    else ->
        ReminderIcon(Icons.Rounded.NotificationsNone, Color(0xFF3D2800), JunnzAmber)
}

private fun triggerSummary(reminder: Reminder): String {
    val trigger = reminder.triggers.firstOrNull() ?: return "No trigger"
    return when (trigger) {
        is Trigger.TimeTrigger -> {
            val local = trigger.fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val day = when (local.dayOfWeek.value) {
                1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"
                4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"
                else -> "Sun"
            }
            val h = local.hour
            val hour12 = if (h == 0) 12 else if (h > 12) h - 12 else h
            val ampm = if (h < 12) "AM" else "PM"
            "$day, $hour12:${"%02d".format(local.minute)} $ampm"
        }
        is Trigger.GeofenceTrigger -> trigger.label.ifEmpty { "Location" }
        is Trigger.SemanticTrigger -> trigger.anchorText.take(24)
        is Trigger.AppContextTrigger -> trigger.label.ifEmpty { "App trigger" }
    }
}
