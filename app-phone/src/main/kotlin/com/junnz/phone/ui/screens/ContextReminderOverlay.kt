package com.junnz.phone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.theme.*

/**
 * Full-bleed contextual nudge shown when a saved reminder's app/location
 * context is detected. Rendered as a dialog destination so the screen behind
 * it stays visible through the scrim.
 */
@Composable
fun ContextReminderOverlay(
    reminderText: String,
    appName: String,
    emoji: String = "🥒",
    onPrimary: () -> Unit,
    onSnooze: () -> Unit,
    onMarkDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1C1A).copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Bottom sheet card — swallow clicks so taps inside don't dismiss
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = SurfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 14.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grab handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Outline),
                )

                Spacer(Modifier.height(22.dp))

                // Glow icon
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(JunnzGreen.copy(alpha = 0.20f), JunnzGreen.copy(alpha = 0.04f)),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(JunnzGreenLight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ShoppingBag, null, Modifier.size(26.dp), JunnzGreen)
                    }
                    Text("✨", fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd))
                }

                Spacer(Modifier.height(16.dp))

                // "You opened Blinkit"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = OnSurfaceVariant)) { append("You opened ") }
                        withStyle(SpanStyle(color = JunnzGreen, fontWeight = FontWeight.Bold)) { append(appName) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(8.dp))

                // Headline
                Text(
                    text = "Don't forget to\n$reminderText $emoji",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = OnSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                )

                Spacer(Modifier.height(26.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OverlayAction(
                        icon = Icons.Rounded.AddShoppingCart,
                        label = "Add to cart",
                        primary = true,
                        onClick = onPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    OverlayAction(
                        icon = Icons.Rounded.Snooze,
                        label = "Snooze",
                        sublabel = "30 mins",
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f),
                    )
                    OverlayAction(
                        icon = Icons.Rounded.CheckCircle,
                        label = "Mark done",
                        onClick = onMarkDone,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Triggered by your shopping context",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    primary: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) JunnzGreen else SurfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), if (primary) Color.White else OnSurface)
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) Color.White else OnSurface,
        )
        if (sublabel != null) {
            Text(sublabel, fontSize = 10.sp, color = if (primary) Color.White.copy(alpha = 0.85f) else OnSurfaceVariant)
        }
    }
}
