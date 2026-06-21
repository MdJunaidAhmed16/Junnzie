package com.junnz.wear.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.*
import com.junnz.wear.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val WEAR_BAR_COUNT = 6

@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {

    private val viewModel: CaptureViewModel by viewModels()

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startCapture() else viewModel.reset()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JunnzWearTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CaptureScreen(
                    state = state,
                    onStopTap = { viewModel.stopCapture() },
                    onConfirmSave = { viewModel.confirmSave() },
                    onCancel = { viewModel.reset(); finish() },
                    onDismiss = { finish() },
                    onRetry = { viewModel.reset(); requestAudio() },
                )
            }
        }
        requestAudio()
    }

    private fun requestAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCapture()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.state.value is CaptureState.Recording) {
            viewModel.stopCapture()
        }
    }
}

@Composable
fun CaptureScreen(
    state: CaptureState,
    onStopTap: () -> Unit,
    onConfirmSave: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is CaptureState.Idle, is CaptureState.Connecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    indicatorColor = JunnzAmber,
                )
            }

            is CaptureState.Recording -> RecordingView(
                rmsLevel = state.rmsLevel,
                onStopTap = onStopTap,
                onCancelTap = onCancel,
            )

            is CaptureState.Processing -> ProcessingView()

            is CaptureState.Preview -> PreviewView(
                transcript = state.transcript,
                onSave = onConfirmSave,
                onEdit = onDismiss,
            )

            is CaptureState.Saved -> SavedView(onViewAll = onDismiss)

            is CaptureState.Error -> ErrorView(
                message = state.message,
                onRetry = onRetry,
                onDismiss = onDismiss,
            )

            is CaptureState.NoPhone -> NoPhoneView(onDismiss = onDismiss)
        }
    }
}

// ── Screen 2: Listening ───────────────────────────────────────────────────────

@Composable
private fun RecordingView(rmsLevel: Float, onStopTap: () -> Unit, onCancelTap: () -> Unit) {
    val glowTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Listening...",
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            color = JunnzAmber,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Speak now",
            style = MaterialTheme.typography.caption2,
            color = WearOnSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WearAudioWaveform(isActive = true, mirrored = true)

            // Mic button — tapping stops recording and sends to processing
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(68.dp).background(JunnzAmber.copy(alpha = glowAlpha * 0.5f), CircleShape))
                Box(Modifier.size(56.dp).background(JunnzAmber.copy(alpha = glowAlpha), CircleShape))
                // Dark center with amber border
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(JunnzAmber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(WearBackground, CircleShape)
                            .clickable(onClick = onStopTap),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Stop recording",
                            tint = JunnzAmber,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            WearAudioWaveform(isActive = true, mirrored = false)
        }

        Spacer(Modifier.height(10.dp))

        Chip(
            onClick = onCancelTap,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
            label = {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

// ── Screen 3: Processing ──────────────────────────────────────────────────────

@Composable
private fun ProcessingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(148.dp),
            indicatorColor = JunnzAmber,
            trackColor = WearSurface,
            strokeWidth = 5.dp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = JunnzAmber,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Processing",
                style = MaterialTheme.typography.body1,
                color = WearOnSurface,
            )
            Text(
                text = "your reminder",
                style = MaterialTheme.typography.caption2,
                color = JunnzAmber,
            )
        }
    }
}

// ── Screen 4: Preview ─────────────────────────────────────────────────────────

@Composable
private fun PreviewView(transcript: String, onSave: () -> Unit, onEdit: () -> Unit) {
    ScalingLazyColumn(
        state = ScalingLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Here's your reminder",
                style = MaterialTheme.typography.caption1,
                color = WearOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(WearSurface)
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = null,
                            tint = JunnzAmber,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        )
                        Text(
                            text = transcript.take(100),
                            style = MaterialTheme.typography.caption1,
                            color = WearOnSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                    icon = {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(ChipDefaults.SmallIconSize),
                        )
                    },
                    label = { Text("Edit", style = MaterialTheme.typography.caption1) },
                )
                Chip(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ChipDefaults.chipColors(
                        backgroundColor = JunnzAmber,
                        contentColor = WearBackground,
                    ),
                    icon = {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ChipDefaults.SmallIconSize),
                            tint = WearBackground,
                        )
                    },
                    label = {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.caption1,
                            color = WearBackground,
                        )
                    },
                )
            }
        }
    }
}

// ── Screen 5: Saved ───────────────────────────────────────────────────────────

@Composable
private fun SavedView(onViewAll: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(120.dp)) {
                drawParticles(this)
            }
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(JunnzGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Saved!",
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            color = WearOnSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "It's added to your app",
            style = MaterialTheme.typography.caption2,
            color = WearOnSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))

        Chip(
            onClick = onViewAll,
            modifier = Modifier.fillMaxWidth(0.72f),
            colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
            icon = {
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(ChipDefaults.SmallIconSize),
                )
            },
            label = { Text("View all", style = MaterialTheme.typography.caption1) },
        )
    }
}

private fun drawParticles(scope: DrawScope) {
    val cx = scope.size.width / 2f
    val cy = scope.size.height / 2f
    val r = scope.size.width * 0.43f
    val particleColors = listOf(
        Color(0xFF4CAF50), Color(0xFFF4A623), Color(0xFF66BB6A),
        Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFF43A047),
        Color(0xFFFFA000), Color(0xFF2E7D32),
    )
    val radii = listOf(4f, 5f, 3f, 4f, 5f, 3f, 4f, 5f)
    for (i in 0..7) {
        val angle = (i * 45.0 - 22.5) * (PI / 180.0)
        scope.drawCircle(
            color = particleColors[i],
            radius = radii[i],
            center = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat()),
        )
    }
}

// ── Error & No-phone ─────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = JunnzCoral,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = message.take(60),
            style = MaterialTheme.typography.caption2,
            color = WearOnSurface,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactChip(
                onClick = onRetry,
                colors = ChipDefaults.chipColors(backgroundColor = JunnzAmber),
                label = { Text("Retry", color = WearBackground) },
            )
            CompactChip(
                onClick = onDismiss,
                colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
                label = { Text("Cancel") },
            )
        }
    }
}

@Composable
private fun NoPhoneView(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = WearOnSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "Phone not connected",
            style = MaterialTheme.typography.body1,
            color = WearOnSurface,
            textAlign = TextAlign.Center,
        )
        CompactChip(
            onClick = onDismiss,
            colors = ChipDefaults.chipColors(backgroundColor = WearSurface),
            label = { Text("OK") },
        )
    }
}

// ── Waveform bars ─────────────────────────────────────────────────────────────

@Composable
private fun WearAudioWaveform(isActive: Boolean, mirrored: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val displayOrder = remember(mirrored) {
        if (mirrored) (WEAR_BAR_COUNT - 1 downTo 0).toList()
        else (0 until WEAR_BAR_COUNT).toList()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayOrder.forEach { index ->
            val profile = index.toFloat() / (WEAR_BAR_COUNT - 1) * 0.8f + 0.2f
            WearWaveformBar(profile = profile, isActive = isActive, index = index, transition = transition)
        }
    }
}

@Composable
private fun WearWaveformBar(profile: Float, isActive: Boolean, index: Int, transition: InfiniteTransition) {
    val rawFraction by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380 + index * 70, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 55, StartOffsetType.FastForward),
        ),
        label = "wBar$index",
    )
    val targetH = if (isActive) (profile * rawFraction * 18f + 3f) else 3f
    val animH by animateFloatAsState(targetValue = targetH, animationSpec = tween(220), label = "wBarH$index")

    Box(
        modifier = Modifier
            .width(2.5.dp)
            .height(animH.coerceAtLeast(3f).dp)
            .clip(RoundedCornerShape(50))
            .background(JunnzAmber),
    )
}
