package com.junnz.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
fun ReminderListScreen(
    reminders: List<Reminder>,
    onReminderTap: (Reminder) -> Unit,
    onCaptureTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onCompleteTap: (String) -> Unit,
    onSelectTab: (NavTab) -> Unit = {},
    onShoppingContextTap: () -> Unit = {},
) {
    val greeting = remember {
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val pending = reminders.filter { it.status == ReminderStatus.PENDING }
    val today = pending.take(3)

    val locationCount = pending.count { r -> r.triggers.any { it is Trigger.GeofenceTrigger } }
    val appCount = pending.count { r -> r.triggers.any { it is Trigger.AppContextTrigger } }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            JunnzBottomNav(
                selected = NavTab.Home,
                onSelect = { tab -> if (tab != NavTab.Home) onSelectTab(tab) },
                onCapture = onCaptureTap,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // Soft ambient blob behind the header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                JunnzGreen.copy(alpha = 0.07f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item { Spacer(Modifier.statusBarsPadding()) }

                // ── Top bar ───────────────────────────────────────────────
                item { TopBar(onAvatarTap = onSettingsTap) }

                // ── Greeting ──────────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnSurface,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text = "👋", fontSize = 22.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Here's what's smart for you today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                        )
                    }
                }

                // ── Search bar ────────────────────────────────────────────
                item { SearchBar(modifier = Modifier.padding(horizontal = 20.dp)) }

                // ── Today ─────────────────────────────────────────────────
                item {
                    SectionHeader(
                        title = "Today",
                        action = "See all",
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp,
                        ),
                    )
                }

                if (today.isEmpty()) {
                    item { EmptyTodayCard(onCaptureTap = onCaptureTap) }
                } else {
                    items(today, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onTap = { onReminderTap(reminder) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }

                // ── Smart Context ─────────────────────────────────────────
                item {
                    SectionHeader(
                        title = "Smart Context",
                        action = "See all",
                        modifier = Modifier.padding(
                            start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp,
                        ),
                    )
                }
                item {
                    val dark = JunnzIsDark
                    val surface = SurfaceContainer
                    val surfaceHigh = SurfaceContainerHigh
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ContextCard(
                            emoji = "🛍️",
                            tint = if (dark) Brush.linearGradient(listOf(surfaceHigh, surface))
                            else Brush.linearGradient(listOf(Color(0xFFDCF0E4), Color(0xFFEAF6EF))),
                            blobColor = JunnzGreen.copy(alpha = if (dark) 0.28f else 0.18f),
                            title = "Shopping context",
                            subtitle = "${appCount.coerceAtLeast(3)} reminders",
                            onClick = onShoppingContextTap,
                            modifier = Modifier.weight(1f),
                        )
                        ContextCard(
                            emoji = "📍",
                            tint = if (dark) Brush.linearGradient(listOf(surfaceHigh, surface))
                            else Brush.linearGradient(listOf(Color(0xFFE7EFFB), Color(0xFFF0F5FD))),
                            blobColor = JunnzBlue.copy(alpha = if (dark) 0.30f else 0.16f),
                            title = "Nearby places",
                            subtitle = "${locationCount.coerceAtLeast(2)} reminders",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onAvatarTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "Junnz",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = JunnzGreen,
            )
            Text(
                text = "✨",
                fontSize = 13.sp,
                modifier = Modifier.offset(x = 1.dp, y = (-2).dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = OnSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(JunnzGreen)
                        .border(1.5.dp, Background, CircleShape)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp),
                )
            }
            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JunnzGreenLight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAvatarTap,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = "Profile",
                    tint = JunnzGreen,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Search reminders",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Tune,
            contentDescription = "Filters",
            tint = JunnzGreen,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    action: String?,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
        )
        if (action != null) {
            Text(
                text = action,
                fontSize = 13.sp,
                color = JunnzGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction,
                ),
            )
        }
    }
}

// ── Reminder card ─────────────────────────────────────────────────────────────

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emoji = remember(reminder) { reminderEmoji(reminder) }
    val emojiBg = remember(reminder) { reminder.toCategory().bgColor }
    val timeLabel = remember(reminder) { triggerTimeLabel(reminder) }
    val subtitle = remember(reminder) { reminderSubtitle(reminder) }
    val apps = remember(reminder) { reminderApps(reminder) }
    val geofence = remember(reminder) {
        reminder.triggers.filterIsInstance<Trigger.GeofenceTrigger>().firstOrNull()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.20f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Emoji tile
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(emojiBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Badges row
                if (apps.isNotEmpty() || geofence != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        apps.forEach { app ->
                            AppBadge(app)
                        }
                        if (geofence != null) {
                            LocationBadge(geofence.label.ifEmpty { "Location" })
                        }
                    }
                }
            }

            // Right column — time + status, or chevron
            Column(horizontalAlignment = Alignment.End) {
                if (timeLabel.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeLabel,
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusOnline),
                        )
                    }
                } else {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = JunnzGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBadge(app: AppChip) {
    Surface(shape = RoundedCornerShape(8.dp), color = app.bg) {
        Text(
            text = app.name,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = app.fg,
        )
    }
}

@Composable
private fun LocationBadge(label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = BadgeBlueBg) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Place, null, Modifier.size(13.dp), BadgeBlueFg)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BadgeBlueFg,
            )
        }
    }
}

// ── Empty today card ──────────────────────────────────────────────────────────

@Composable
private fun EmptyTodayCard(onCaptureTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.20f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCaptureTap),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(JunnzGreen.copy(alpha = 0.20f), JunnzGreen.copy(alpha = 0.04f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🌿", fontSize = 30.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "All clear for today",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap the + or press your watch button\nto capture your first reminder.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(JunnzGreen)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCaptureTap,
                    )
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), Color.White)
                Text("New reminder", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ── Smart Context card ────────────────────────────────────────────────────────

@Composable
private fun ContextCard(
    emoji: String,
    tint: Brush,
    blobColor: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(tint)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Decorative blob
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 60.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(blobColor)
                .align(Alignment.TopEnd),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceContainer.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = OnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ── Data helpers ──────────────────────────────────────────────────────────────

private data class AppChip(val name: String, val bg: Color, val fg: Color)

private fun reminderApps(reminder: Reminder): List<AppChip> {
    val appTrigger = reminder.triggers.filterIsInstance<Trigger.AppContextTrigger>().firstOrNull()
        ?: return emptyList()
    val label = appTrigger.label.ifEmpty { return emptyList() }
    // Split on common separators ("Blinkit, Zepto" / "Blinkit or Zepto")
    return label
        .split(",", " or ", "/", "&")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(3)
        .map { name -> appChipFor(name) }
}

private fun appChipFor(name: String): AppChip {
    val lower = name.lowercase()
    return when {
        lower.contains("blinkit") -> AppChip("blinkit", AppBlinkitBg, AppBlinkitFg)
        lower.contains("zepto") -> AppChip("zepto", AppZeptoBg, AppZeptoFg)
        else -> AppChip(name, ChipNeutralBg, ChipNeutralFg)
    }
}

private fun reminderSubtitle(reminder: Reminder): String {
    val trigger = reminder.triggers.firstOrNull() ?: return "Reminder"
    return when (trigger) {
        is Trigger.TimeTrigger -> "Scheduled for you"
        is Trigger.GeofenceTrigger -> "When you're nearby"
        is Trigger.AppContextTrigger -> "When you open these apps"
        is Trigger.SemanticTrigger -> "Smart context"
    }
}

private fun reminderEmoji(reminder: Reminder): String {
    val t = reminder.text.lowercase()
    return when {
        t.containsAny("milk", "grocer", "cucumber", "vegetable", "fruit", "buy") -> "🛒"
        t.containsAny("ramen", "noodle", "food", "lunch", "dinner", "eat", "restaurant") -> "🍜"
        t.containsAny("coffee", "café", "cafe", "tea") -> "☕"
        t.containsAny("call", "phone", "mom", "dad", "ring") -> "📞"
        t.containsAny("med", "pill", "doctor", "health", "serum", "hair") -> "💊"
        t.containsAny("gym", "run", "workout", "exercise") -> "🏃"
        t.containsAny("pay", "bill", "rent", "money") -> "💳"
        t.containsAny("meet", "meeting", "work", "office") -> "💼"
        t.containsAny("water", "plant") -> "🪴"
        t.containsAny("birthday", "gift", "party") -> "🎁"
        else -> when {
            reminder.triggers.any { it is Trigger.GeofenceTrigger } -> "📍"
            reminder.triggers.any { it is Trigger.TimeTrigger } -> "⏰"
            else -> "📝"
        }
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { this.contains(it) }

private data class ReminderCategory(
    val label: String,
    val icon: ImageVector,
    val bgColor: Color,
    val fgColor: Color,
)

private fun Reminder.toCategory(): ReminderCategory = when {
    status == ReminderStatus.FIRED -> ReminderCategory(
        "Priority", Icons.Rounded.Warning, BadgeRedBg, BadgeRedFg,
    )
    triggers.any { it is Trigger.TimeTrigger } -> ReminderCategory(
        "Scheduled", Icons.Rounded.Schedule, BadgeGreenBg, BadgeGreenFg,
    )
    triggers.any { it is Trigger.GeofenceTrigger } -> ReminderCategory(
        "Location", Icons.Rounded.LocationOn, BadgeBlueBg, BadgeBlueFg,
    )
    triggers.any { it is Trigger.SemanticTrigger } -> ReminderCategory(
        "Context", Icons.Rounded.AutoAwesome, BadgePurpleBg, BadgePurpleFg,
    )
    triggers.any { it is Trigger.AppContextTrigger } -> ReminderCategory(
        "App", Icons.Rounded.PhoneAndroid, BadgeGreenBg, BadgeGreenFg,
    )
    else -> ReminderCategory(
        "Reminder", Icons.Rounded.NotificationsNone, BadgeGreenBg, BadgeGreenFg,
    )
}

private fun triggerTimeLabel(reminder: Reminder): String {
    val trigger = reminder.triggers.filterIsInstance<Trigger.TimeTrigger>().firstOrNull()
        ?: return ""
    val local = trigger.fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val h = local.hour
    val hour12 = if (h == 0) 12 else if (h > 12) h - 12 else h
    val ampm = if (h < 12) "AM" else "PM"
    return "$hour12:${"%02d".format(local.minute)} $ampm"
}
