package com.junnz.phone.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.components.JunnzBottomNav
import com.junnz.phone.ui.components.NavTab
import com.junnz.phone.ui.theme.*

@Composable
fun ExploreScreen(
    onCaptureTap: () -> Unit,
    onSelectTab: (NavTab) -> Unit,
) {
    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            JunnzBottomNav(
                selected = NavTab.Explore,
                onSelect = { tab -> if (tab != NavTab.Explore) onSelectTab(tab) },
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
            // Stylized map header
            item { MapHeader() }

            // Primary nearby suggestion
            item {
                NearbySuggestionCard(
                    place = "Anna Nagar",
                    emoji = "🍜",
                    title = "You're near Anna Nagar",
                    body = "There's a ramen restaurant you saved nearby. Want to try it now?",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                )
            }

            // Section label
            item {
                Text(
                    text = "Discover nearby",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
                )
            }

            item {
                DiscoverRow(
                    emoji = "☕",
                    title = "Blue Tokai Café",
                    subtitle = "Popular café · around the corner",
                    tag = "New",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item {
                DiscoverRow(
                    emoji = "🛒",
                    title = "More Supermarket",
                    subtitle = "Grocery · 400m away",
                    tag = null,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            item {
                DiscoverRow(
                    emoji = "💊",
                    title = "Apollo Pharmacy",
                    subtitle = "Pharmacy · 650m away",
                    tag = null,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ── Stylized map ──────────────────────────────────────────────────────────────

@Composable
private fun MapHeader() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )

    val mapGradient = if (JunnzIsDark) {
        Brush.verticalGradient(listOf(SurfaceContainerHigh, Background))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEDF3EE), Color(0xFFF4F6F2)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(mapGradient),
    ) {
        // Soft "park" and "block" shapes to suggest a map
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = (-50).dp, y = 40.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(JunnzGreen.copy(alpha = 0.07f)),
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 10.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(JunnzBlue.copy(alpha = 0.06f)),
        )
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = 70.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(JunnzGreen.copy(alpha = 0.05f)),
        )

        // Top bar: title + filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = SurfaceContainer,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.MyLocation, null, Modifier.size(15.dp), JunnzGreen)
                    Text("Anna Nagar West", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                }
            }
            Surface(
                shape = CircleShape,
                color = SurfaceContainer,
                shadowElevation = 3.dp,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, "Filters", Modifier.size(18.dp), OnSurface)
                }
            }
        }

        // Center location puck with pulse
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(JunnzGreen.copy(alpha = ringAlpha)),
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(JunnzGreen)
                    .border(3.dp, Color.White, CircleShape),
            )
        }
    }
}

// ── Nearby suggestion card ────────────────────────────────────────────────────

@Composable
private fun NearbySuggestionCard(
    place: String,
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.22f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.20f),
            )
            .clip(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = SurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(JunnzGreen.copy(alpha = 0.18f), JunnzGreen.copy(alpha = 0.04f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Place, null, Modifier.size(28.dp), JunnzGreen)
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = JunnzGreen,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(emoji, fontSize = 26.sp)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryAction(Icons.Rounded.Storefront, "View place", Modifier.weight(1f))
                SecondaryAction(Icons.Rounded.Schedule, "Remind later", Modifier.weight(1f))
                PrimaryAction(Icons.Rounded.Navigation, "Navigate", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), OnSurface)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OnSurface)
    }
}

@Composable
private fun PrimaryAction(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JunnzGreen)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), Color.White)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ── Discover row ──────────────────────────────────────────────────────────────

@Composable
private fun DiscoverRow(
    emoji: String,
    title: String,
    subtitle: String,
    tag: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0xFFB6CFC0).copy(alpha = 0.18f),
                spotColor = Color(0xFFB6CFC0).copy(alpha = 0.16f),
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(JunnzGreenLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
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
            if (tag != null) {
                Surface(shape = RoundedCornerShape(50), color = JunnzGreenLight) {
                    Text(
                        text = tag,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = JunnzGreen,
                    )
                }
            } else {
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), OnSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
