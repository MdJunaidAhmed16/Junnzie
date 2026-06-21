package com.junnz.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.theme.*
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.shared.domain.Trigger
import com.junnz.shared.parsing.ReminderParser
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.hours

private enum class TriggerKind(val label: String, val icon: ImageVector) {
    Time("Time", Icons.Rounded.Schedule),
    Location("Location", Icons.Rounded.Place),
    App("App", Icons.Rounded.Apps),
    Context("Context", Icons.Rounded.AutoAwesome),
}

private enum class TimePreset(val label: String) {
    InOneHour("In 1 hour"),
    ThisEvening("This evening"),
    TomorrowMorning("Tomorrow 9 AM"),
    NextWeek("Next week"),
}

private data class KnownApp(val name: String, val pkg: String)

private val knownApps = listOf(
    KnownApp("Blinkit", "com.grofers.customerapp"),
    KnownApp("Zepto", "com.zeptoconsumerapp"),
    KnownApp("Swiggy", "in.swiggy.android"),
    KnownApp("Zomato", "com.application.zomato"),
    KnownApp("Amazon", "in.amazon.mShop.android.shopping"),
)

private data class Category(val label: String, val emoji: String, val tag: String)

private val categories = listOf(
    Category("Shopping", "🛍️", "shopping"),
    Category("Food", "🍜", "food"),
    Category("Work", "💼", "work"),
    Category("Personal", "🌿", "personal"),
)

@Composable
fun CreateReminderScreen(
    onSave: (Reminder) -> Unit,
    onVoiceTap: () -> Unit,
    onBack: () -> Unit,
) {
    val tz = remember { TimeZone.currentSystemDefault() }

    var sentence by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TriggerKind.Time) }
    var timePreset by remember { mutableStateOf(TimePreset.ThisEvening) }
    var place by remember { mutableStateOf("") }
    var contextText by remember { mutableStateOf("") }
    val selectedApps = remember { mutableStateListOf<KnownApp>() }
    var category by remember { mutableStateOf<Category?>(null) }

    val title = task.ifBlank { sentence }.trim()
    val canSave = title.isNotEmpty() && when (kind) {
        TriggerKind.Location -> place.isNotBlank()
        TriggerKind.App -> selectedApps.isNotEmpty()
        TriggerKind.Context -> contextText.isNotBlank()
        TriggerKind.Time -> true
    }

    fun buildReminder(): Reminder {
        val now = Clock.System.now()
        val trigger: Trigger = when (kind) {
            TriggerKind.Time -> Trigger.TimeTrigger(fireAt = presetInstant(timePreset, tz))
            TriggerKind.Location -> Trigger.GeofenceTrigger(lat = 0.0, lng = 0.0, label = place.trim())
            TriggerKind.App -> Trigger.AppContextTrigger(
                packageNames = selectedApps.map { it.pkg },
                label = selectedApps.joinToString(", ") { it.name },
            )
            TriggerKind.Context -> Trigger.SemanticTrigger(
                anchorText = contextText.trim(),
                threshold = ReminderParser.SEMANTIC_THRESHOLD,
            )
        }
        return Reminder(
            id = UUID.randomUUID().toString(),
            text = title,
            rawTranscript = sentence.ifBlank { title },
            createdAt = now,
            status = ReminderStatus.PENDING,
            triggers = listOf(trigger),
            tags = listOfNotNull(category?.tag),
        )
    }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0),
        topBar = { CreateTopBar(onBack = onBack) },
        bottomBar = {
            SaveBar(enabled = canSave, onSave = { onSave(buildReminder()) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Natural-language input
            item {
                SentenceInput(
                    value = sentence,
                    onValueChange = { sentence = it },
                    onVoiceTap = onVoiceTap,
                )
            }

            // Task
            item {
                FieldLabel("Task")
                Spacer(Modifier.height(8.dp))
                PlainField(
                    value = task,
                    onValueChange = { task = it },
                    placeholder = sentence.ifBlank { "What should this remind you about?" },
                    leading = Icons.Rounded.CheckCircleOutline,
                )
            }

            // Trigger type
            item {
                FieldLabel("Trigger type")
                Spacer(Modifier.height(8.dp))
                TriggerSelector(selected = kind, onSelect = { kind = it })
            }

            // Contextual value
            item {
                when (kind) {
                    TriggerKind.Time -> {
                        FieldLabel("When")
                        Spacer(Modifier.height(8.dp))
                        TimePresetChips(selected = timePreset, onSelect = { timePreset = it })
                    }
                    TriggerKind.Location -> {
                        FieldLabel("Place")
                        Spacer(Modifier.height(8.dp))
                        PlainField(
                            value = place,
                            onValueChange = { place = it },
                            placeholder = "e.g. Anna Nagar",
                            leading = Icons.Rounded.Place,
                        )
                    }
                    TriggerKind.App -> {
                        FieldLabel("Apps")
                        Spacer(Modifier.height(8.dp))
                        AppSelector(selected = selectedApps)
                    }
                    TriggerKind.Context -> {
                        FieldLabel("Context cue")
                        Spacer(Modifier.height(8.dp))
                        PlainField(
                            value = contextText,
                            onValueChange = { contextText = it },
                            placeholder = "e.g. when I'm talking about travel",
                            leading = Icons.Rounded.AutoAwesome,
                        )
                    }
                }
            }

            // Category
            item {
                FieldLabel("Category")
                Spacer(Modifier.height(8.dp))
                CategoryChips(selected = category, onSelect = { category = if (category == it) null else it })
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun CreateTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = OnSurface)
        }
        Text(
            text = "Create Reminder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(JunnzGreenLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, "AI assist", tint = JunnzGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ── Sentence (NL) input ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SentenceInput(
    value: String,
    onValueChange: (String) -> Unit,
    onVoiceTap: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.20f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("✨", fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "Remind me to buy cucumber when I open Blinkit…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 22.sp,
                    )
                }
                BasicTextFieldStyled(value = value, onValueChange = onValueChange)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(JunnzGreenLight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVoiceTap,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, "Voice input", tint = JunnzGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BasicTextFieldStyled(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface, lineHeight = 22.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(JunnzGreen),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Field helpers ─────────────────────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = OnSurfaceVariant,
    )
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, null, Modifier.size(18.dp), JunnzGreen)
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            BasicTextFieldStyledSingle(value, onValueChange)
        }
    }
}

@Composable
private fun BasicTextFieldStyledSingle(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(JunnzGreen),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Trigger selector ──────────────────────────────────────────────────────────

@Composable
private fun TriggerSelector(selected: TriggerKind, onSelect: (TriggerKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TriggerKind.entries.forEach { kind ->
            val isSel = kind == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSel) JunnzGreen else SurfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(kind) },
                    )
                    .padding(vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    kind.icon, null, Modifier.size(20.dp),
                    if (isSel) Color.White else OnSurfaceVariant,
                )
                Text(
                    kind.label,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSel) Color.White else OnSurfaceVariant,
                )
            }
        }
    }
}

// ── Time presets ──────────────────────────────────────────────────────────────

@Composable
private fun TimePresetChips(selected: TimePreset, onSelect: (TimePreset) -> Unit) {
    FlowRowChips {
        TimePreset.entries.forEach { preset ->
            SelectChip(
                label = preset.label,
                selected = preset == selected,
                onClick = { onSelect(preset) },
            )
        }
    }
}

// ── App selector ──────────────────────────────────────────────────────────────

@Composable
private fun AppSelector(selected: MutableList<KnownApp>) {
    FlowRowChips {
        knownApps.forEach { app ->
            val isSel = selected.contains(app)
            SelectChip(
                label = app.name,
                selected = isSel,
                onClick = {
                    if (isSel) selected.remove(app) else selected.add(app)
                },
            )
        }
    }
}

// ── Category chips ────────────────────────────────────────────────────────────

@Composable
private fun CategoryChips(selected: Category?, onSelect: (Category) -> Unit) {
    FlowRowChips {
        categories.forEach { cat ->
            val isSel = cat == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSel) JunnzGreen else SurfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(cat) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(cat.emoji, fontSize = 14.sp)
                Text(
                    cat.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSel) Color.White else OnSurface,
                )
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) JunnzGreen else SurfaceContainerHigh,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else OnSurface,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

// ── Save bar ──────────────────────────────────────────────────────────────────

@Composable
private fun SaveBar(enabled: Boolean, onSave: () -> Unit) {
    Surface(color = Background, shadowElevation = 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (enabled) JunnzGreen else SurfaceContainerHighest)
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSave,
                    )
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Save Reminder",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else OnSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    Modifier.size(18.dp),
                    if (enabled) Color.White else OnSurfaceVariant,
                )
            }
        }
    }
}

// ── Time math ─────────────────────────────────────────────────────────────────

private fun presetInstant(preset: TimePreset, tz: TimeZone): Instant {
    val now = Clock.System.now()
    val today: LocalDate = now.toLocalDateTime(tz).date
    fun atHour(date: LocalDate, hour: Int): Instant =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0).toInstant(tz)
    return when (preset) {
        TimePreset.InOneHour -> now + 1.hours
        TimePreset.ThisEvening -> {
            val evening = atHour(today, 18)
            if (evening > now) evening else atHour(today.plus(1, DateTimeUnit.DAY), 18)
        }
        TimePreset.TomorrowMorning -> atHour(today.plus(1, DateTimeUnit.DAY), 9)
        TimePreset.NextWeek -> atHour(today.plus(7, DateTimeUnit.DAY), 9)
    }
}
