package com.junnz.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junnz.phone.ui.theme.*

/** The four primary destinations shown in the bottom dock. */
enum class NavTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Explore("Explore", Icons.Rounded.Explore),
    Activity("Activity", Icons.Rounded.NotificationsNone),
    Profile("Profile", Icons.Rounded.Person),
}

/**
 * Floating pill dock shared by every primary tab. A green gradient capture
 * button floats in the center notch.
 */
@Composable
fun JunnzBottomNav(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    onCapture: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp)
            .height(86.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(32.dp),
            color = SurfaceContainer,
            shadowElevation = 20.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavBarItem(NavTab.Home, selected, onSelect)
                NavBarItem(NavTab.Explore, selected, onSelect)
                Spacer(Modifier.width(56.dp))
                NavBarItem(NavTab.Activity, selected, onSelect)
                NavBarItem(NavTab.Profile, selected, onSelect)
            }
        }

        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.TopCenter)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = JunnzGreen.copy(alpha = 0.45f),
                    spotColor = JunnzGreen.copy(alpha = 0.5f),
                )
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(JunnzGreenBright, JunnzGreen)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCapture,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "New reminder",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun NavBarItem(
    tab: NavTab,
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
) {
    val isSelected = tab == selected
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(tab) },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (isSelected) JunnzGreen else OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = tab.label,
            fontSize = 10.sp,
            color = if (isSelected) JunnzGreen else OnSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
