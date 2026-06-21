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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.components.JunnzBottomNav
import com.junnz.phone.ui.components.NavTab
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junnz.phone.ui.theme.*
import com.junnz.phone.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCaptureTap: () -> Unit = {},
    onSelectTab: (NavTab) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openUsageAccess: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            JunnzBottomNav(
                selected = NavTab.Profile,
                onSelect = { tab -> if (tab != NavTab.Profile) onSelectTab(tab) },
                onCapture = onCaptureTap,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                JunnzAmber.copy(alpha = 0.08f),
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
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
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
                                    .background(JunnzAmber)
                                    .border(1.5.dp, Background, CircleShape)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 5.dp),
                            )
                        }
                    }
                }

                // ── Headline ──────────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp,
                        ),
                    ) {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Manage your account and preferences",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // ── Identity card ─────────────────────────────────────────
                item { IdentityCard() }

                // ── Account ───────────────────────────────────────────────
                item {
                    SectionLabel(
                        text = "Account",
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp,
                        ),
                    )
                }
                item {
                    SettingsCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SettingsRow(
                            iconBg = BadgeAmberBg, icon = Icons.Rounded.Person,
                            iconFg = BadgeAmberFg,
                            title = "Personal information",
                            description = "Update your name, email, and more",
                            onClick = {},
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgePurpleBg, icon = Icons.Rounded.Security,
                            iconFg = BadgePurpleFg,
                            title = "Security",
                            description = "Change password and manage sessions",
                            onClick = {},
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgeGreenBg, icon = Icons.Rounded.CreditCard,
                            iconFg = BadgeGreenFg,
                            title = "Subscription",
                            description = "Manage your plan and billing",
                            onClick = {},
                            trailing = {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = BadgeAmberBg,
                                ) {
                                    Text(
                                        text = "Premium",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BadgeAmberFg,
                                    )
                                }
                            },
                        )
                    }
                }

                // ── Preferences ───────────────────────────────────────────
                item {
                    SectionLabel(
                        text = "Preferences",
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp,
                        ),
                    )
                }
                item {
                    SettingsCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SettingsRow(
                            iconBg = BadgeAmberBg, icon = Icons.Rounded.AutoAwesome,
                            iconFg = BadgeAmberFg,
                            title = "Context nudges",
                            description = "Remind me when I open a relevant app",
                            onClick = { viewModel.setContextNudges(!settings.contextNudgesEnabled) },
                            showChevron = false,
                            trailing = {
                                Switch(
                                    checked = settings.contextNudgesEnabled,
                                    onCheckedChange = { viewModel.setContextNudges(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = JunnzGreen,
                                    ),
                                )
                            },
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgePurpleBg, icon = Icons.Rounded.Palette,
                            iconFg = BadgePurpleFg,
                            title = "Appearance",
                            description = "Choose your theme",
                            onClick = { viewModel.cycleTheme() },
                            trailing = {
                                Text(settings.themeMode.label, fontSize = 13.sp, color = OnSurfaceVariant)
                            },
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgeGreenBg, icon = Icons.Rounded.Language,
                            iconFg = BadgeGreenFg,
                            title = "Language",
                            description = "Choose your preferred language",
                            onClick = { viewModel.cycleLanguage() },
                            trailing = {
                                Text(settings.language, fontSize = 13.sp, color = OnSurfaceVariant)
                            },
                        )
                    }
                }

                // ── Data & privacy ────────────────────────────────────────
                item {
                    SectionLabel(
                        text = "Data & privacy",
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp,
                        ),
                    )
                }
                item {
                    SettingsCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SettingsRow(
                            iconBg = BadgeBlueBg, icon = Icons.Rounded.CloudUpload,
                            iconFg = BadgeBlueFg,
                            title = "Export data",
                            description = "Download a copy of your data",
                            onClick = {},
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgeBlueBg, icon = Icons.Rounded.Visibility,
                            iconFg = BadgeBlueFg,
                            title = "App context access",
                            description = "Allow Junnz to nudge you when apps open",
                            onClick = openUsageAccess,
                        )
                        RowDivider()
                        SettingsRow(
                            iconBg = BadgeRedBg, icon = Icons.Rounded.Delete,
                            iconFg = BadgeRedFg,
                            title = "Delete account",
                            description = "Permanently remove your account",
                            onClick = {},
                            titleColor = BadgeRedFg,
                        )
                    }
                }
            }
        }
    }
}

// ── Identity card ─────────────────────────────────────────────────────────────

@Composable
private fun IdentityCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = JunnzAmber.copy(alpha = 0.10f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.30f),
            )
            .clip(RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = SurfaceContainer,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // Avatar + info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Avatar with amber ring
                Box(modifier = Modifier.size(88.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(JunnzAmber.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(JunnzGreenLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = JunnzAmber,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(SurfaceContainer)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = "Change photo",
                            modifier = Modifier.size(13.dp),
                            tint = OnSurfaceVariant,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sarah Johnson",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "sarah.j@example.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BadgeAmberBg,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = BadgeAmberFg,
                            )
                            Text(
                                text = "Premium",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BadgeAmberFg,
                            )
                        }
                    }
                }

                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = OnSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Outline.copy(alpha = 0.6f), thickness = 0.5.dp)
            Spacer(Modifier.height(24.dp))

            // Clean stats — numbers only, no icon boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatColumn(count = "128", label = "Reminders")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Outline),
                )
                StatColumn(count = "24", label = "Completed")
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Outline),
                )
                StatColumn(count = "12", label = "Categories")
            }
        }
    }
}

@Composable
private fun StatColumn(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = OnSurface,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = OnSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Section helpers ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = OnSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.22f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.18f),
            )
            .clip(RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceContainer,
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp, end = 20.dp),
        color = Outline.copy(alpha = 0.5f),
        thickness = 0.5.dp,
    )
}

@Composable
private fun SettingsRow(
    iconBg: Color,
    icon: ImageVector,
    iconFg: Color,
    title: String,
    description: String,
    onClick: () -> Unit,
    titleColor: Color = OnSurface,
    showChevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = iconFg,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(4.dp))
        }

        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

