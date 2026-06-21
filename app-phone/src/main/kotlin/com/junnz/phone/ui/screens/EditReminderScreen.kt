package com.junnz.phone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.theme.*
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    reminder: Reminder,
    onSave: (Reminder) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var text by remember { mutableStateOf(reminder.text) }
    var notes by remember { mutableStateOf("") }
    var triggers by remember { mutableStateOf(reminder.triggers) }

    Scaffold(
        containerColor = Background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Junnz", color = JunnzAmber, style = MaterialTheme.typography.headlineMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Title area
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunnzAmber,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = JunnzAmber,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                )

                Text(
                    text = triggerCategoryLabel(reminder),
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            HorizontalDivider(color = OutlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // When should this fire?
            SectionHeader(
                title = "When should this fire?",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            // Trigger chips
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                triggers.forEachIndexed { index, trigger ->
                    TriggerChip(
                        label = triggerChipLabel(trigger),
                        onRemove = { triggers = triggers.toMutableList().also { it.removeAt(index) } },
                    )
                }

                // + Add trigger button with dashed border
                val dashColor = Outline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRoundRect(
                                color = dashColor,
                                cornerRadius = CornerRadius(24.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(12f, 8f), 0f,
                                    ),
                                ),
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Add trigger",
                            fontSize = 14.sp,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = OutlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // Notes
            SectionHeader(
                title = "Notes",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Add a note…", color = OnSurfaceVariant.copy(alpha = 0.5f)) },
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JunnzAmber,
                    unfocusedBorderColor = Outline,
                    focusedContainerColor = SurfaceContainer,
                    unfocusedContainerColor = SurfaceContainer,
                    cursorColor = JunnzAmber,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = OutlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // Subtasks
            SectionHeader(
                title = "Subtasks",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No subtasks yet",
                fontSize = 13.sp,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(40.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Button(
                    onClick = {
                        onSave(reminder.copy(text = text.trim(), triggers = triggers))
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JunnzAmber),
                ) {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnSurface,
        modifier = modifier,
    )
}

@Composable
private fun TriggerChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = BadgeAmberBg,
        modifier = Modifier.wrapContentWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = BadgeAmberFg,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = BadgeAmberFg,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun triggerChipLabel(trigger: Trigger): String = when (trigger) {
    is Trigger.TimeTrigger -> {
        val local = trigger.fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "Today, ${local.hour}:${"%02d".format(local.minute)}"
    }
    is Trigger.GeofenceTrigger -> trigger.label.ifEmpty { "Location" }
    is Trigger.SemanticTrigger -> trigger.anchorText.take(24)
    is Trigger.AppContextTrigger -> trigger.label.ifEmpty { "App trigger" }
}

private fun triggerCategoryLabel(reminder: Reminder): String {
    val trigger = reminder.triggers.firstOrNull() ?: return "General"
    return when (trigger) {
        is Trigger.TimeTrigger -> "Scheduled"
        is Trigger.GeofenceTrigger -> trigger.label.ifEmpty { "Location" }
        is Trigger.SemanticTrigger -> "Context trigger"
        is Trigger.AppContextTrigger -> trigger.label.ifEmpty { "App trigger" }
    }
}
