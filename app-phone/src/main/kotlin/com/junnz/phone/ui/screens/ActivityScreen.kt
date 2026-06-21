package com.junnz.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.components.JunnzBottomNav
import com.junnz.phone.ui.components.NavTab
import com.junnz.phone.ui.theme.*
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.domain.Trigger
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ActivityScreen(
    reminders: List<Reminder>,
    onReminderTap: (Reminder) -> Unit,
    onCaptureTap: () -> Unit,
    onSelectTab: (NavTab) -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val todayEpochDay = remember {
        Clock.System.now().toLocalDateTime(tz).date.toEpochDays()
    }

    val grouped = remember(reminders) {
        reminders
            .sortedByDescending { it.createdAt }
            .groupBy { reminder ->
                val days = todayEpochDay - reminder.createdAt.toLocalDateTime(tz).date.toEpochDays()
                when {
                    days <= 0 -> "Today"
                    days == 1 -> "Yesterday"
                    days <= 7 -> "This week"
                    else -> "Earlier"
                }
            }
    }
    val order = listOf("Today", "Yesterday", "This week", "Earlier")

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            JunnzBottomNav(
                selected = NavTab.Activity,
                onSelect = { tab -> if (tab != NavTab.Activity) onSelectTab(tab) },
                onCapture = onCaptureTap,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { Spacer(Modifier.statusBarsPadding()) }

            // Header
            item {
                Column(
                    modifier = Modifier.padding(
                        start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp,
                    ),
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Everything Junnz captured and triggered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                    )
                }
            }

            if (reminders.isEmpty()) {
                item { EmptyActivity() }
            } else {
                order.forEach { bucket ->
                    val items = grouped[bucket].orEmpty()
                    if (items.isNotEmpty()) {
                        item(key = "h_$bucket") {
                            Text(
                                text = bucket,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 24.dp, end = 24.dp, top = 8.dp, bottom = 10.dp,
                                ),
                            )
                        }
                        itemsIndexed(items) { index, reminder ->
                            TimelineRow(
                                reminder = reminder,
                                isFirst = index == 0,
                                isLast = index == items.lastIndex,
                                onTap = { onReminderTap(reminder) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Timeline row ──────────────────────────────────────────────────────────────

@Composable
private fun TimelineRow(
    reminder: Reminder,
    isFirst: Boolean,
    isLast: Boolean,
    onTap: () -> Unit,
) {
    val status = reminder.activityStatus()
    val time = remember(reminder) {
        val t = reminder.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val h = t.hour
        val hour12 = if (h == 0) 12 else if (h > 12) h - 12 else h
        val ampm = if (h < 12) "AM" else "PM"
        "$hour12:${"%02d".format(t.minute)} $ampm"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Left rail: connector line + status dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(10.dp)
                    .background(if (isFirst) Color.Transparent else Outline),
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(status.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(status.color),
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else Outline),
            )
        }

        // Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.16f),
                    spotColor = Color(0xFFB6CFC0).copy(alpha = 0.14f),
                )
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(status.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(reminder.activityEmoji(), fontSize = 20.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = status.color.copy(alpha = 0.14f)) {
                            Text(
                                text = status.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = status.color,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(time, fontSize = 11.sp, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyActivity() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🗂️", fontSize = 40.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No activity yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Reminders you capture and the moments Junnz triggers them will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    }
}

// ── Status mapping ────────────────────────────────────────────────────────────

private data class ActivityStatus(val label: String, val color: Color)

private val NeutralStatus = Color(0xFF8A8E94) // constant; readable on light & dark

private fun Reminder.activityStatus(): ActivityStatus = when (status) {
    ReminderStatus.FIRED -> ActivityStatus("Triggered", JunnzGreen)
    ReminderStatus.SNOOZED -> ActivityStatus("Snoozed", BadgeBlueFg)
    ReminderStatus.DISMISSED -> ActivityStatus("Completed", JunnzGreen)
    ReminderStatus.EXPIRED -> ActivityStatus("Expired", NeutralStatus)
    ReminderStatus.PENDING -> when {
        triggers.any { it is Trigger.GeofenceTrigger } -> ActivityStatus("Nearby", BadgeBlueFg)
        triggers.any { it is Trigger.AppContextTrigger } -> ActivityStatus("App context", BadgePurpleFg)
        triggers.any { it is Trigger.TimeTrigger } -> ActivityStatus("Scheduled", JunnzGreen)
        else -> ActivityStatus("Captured", NeutralStatus)
    }
}

private fun Reminder.activityEmoji(): String {
    val t = text.lowercase()
    return when {
        listOf("milk", "grocer", "cucumber", "buy", "vegetable").any { t.contains(it) } -> "🛒"
        listOf("ramen", "food", "lunch", "dinner", "eat").any { t.contains(it) } -> "🍜"
        listOf("coffee", "cafe", "café", "tea").any { t.contains(it) } -> "☕"
        listOf("call", "mom", "dad", "phone").any { t.contains(it) } -> "📞"
        listOf("med", "pill", "serum", "hair", "doctor").any { t.contains(it) } -> "💊"
        listOf("pay", "bill", "rent").any { t.contains(it) } -> "💳"
        listOf("meet", "work", "office").any { t.contains(it) } -> "💼"
        else -> when {
            triggers.any { it is Trigger.GeofenceTrigger } -> "📍"
            triggers.any { it is Trigger.TimeTrigger } -> "⏰"
            else -> "📝"
        }
    }
}
