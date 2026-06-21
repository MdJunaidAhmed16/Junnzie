package com.junnz.phone.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junnz.phone.ui.theme.*
import com.junnz.phone.ui.viewmodel.PhoneCaptureState
import com.junnz.phone.ui.viewmodel.PhoneCaptureViewModel
import kotlinx.coroutines.delay

private const val WAVEFORM_BAR_COUNT = 11

@Composable
fun VoiceCaptureScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PhoneCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening() else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(state) {
        if (state is PhoneCaptureState.Done) {
            viewModel.saveReminder()
            onDone()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.reset() }
    }

    val isListening = state is PhoneCaptureState.Listening || state is PhoneCaptureState.Transcribing
    val isError = state is PhoneCaptureState.Error
    val partialText = (state as? PhoneCaptureState.Transcribing)?.partial.orEmpty()
    val errorMessage = (state as? PhoneCaptureState.Error)?.message.orEmpty()

    var dotCount by remember { mutableStateOf(3) }
    LaunchedEffect(isListening) {
        if (isListening) {
            while (true) {
                delay(500)
                dotCount = if (dotCount == 3) 1 else dotCount + 1
            }
        } else {
            dotCount = 3
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.reset(); onBack() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = OnSurface)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI tips", tint = JunnzAmber)
                }
            }
        },
        bottomBar = { CaptureBottomSection(onHomeTap = { viewModel.reset(); onBack() }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Title
            if (isError) {
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = JunnzCoral,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.reset()
                        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.startListening()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JunnzAmber),
                ) {
                    Text("Try again", fontWeight = FontWeight.Medium)
                }
            } else {
                val dots = ".".repeat(dotCount)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = OnSurface, fontWeight = FontWeight.Bold)) {
                            append("I'm listening")
                        }
                        withStyle(SpanStyle(color = JunnzAmber, fontWeight = FontWeight.Bold)) {
                            append(dots)
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (partialText.isNotEmpty()) "\"$partialText\""
                    else "Speak naturally. I'll capture the details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            MicVisualization(isListening = isListening && !isError)

            Spacer(Modifier.height(24.dp))

            LanguagePill()

            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Try saying something like",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                ExamplesCard()
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = OnSurfaceVariant,
                )
                Text(
                    text = "Your voice is processed securely and never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Mic visualization ──────────────────────────────────────────────────────────

@Composable
private fun MicVisualization(isListening: Boolean) {
    val glowTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by glowTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowScale",
    )
    val scale = if (isListening) glowScale else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((230 * scale).dp).background(JunnzAmber.copy(alpha = 0.05f), CircleShape))
        Box(Modifier.size((175 * scale).dp).background(JunnzAmber.copy(alpha = 0.09f), CircleShape))
        Box(Modifier.size((130 * scale).dp).background(JunnzAmber.copy(alpha = 0.16f), CircleShape))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AudioWaveform(isActive = isListening, mirrored = true)

            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(3.dp, JunnzAmber),
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = "Microphone",
                        tint = JunnzAmber,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            AudioWaveform(isActive = isListening, mirrored = false)
        }
    }
}

// ── Waveform bars ─────────────────────────────────────────────────────────────

@Composable
private fun AudioWaveform(isActive: Boolean, mirrored: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val displayOrder = remember(mirrored) {
        if (mirrored) (WAVEFORM_BAR_COUNT - 1 downTo 0).toList()
        else (0 until WAVEFORM_BAR_COUNT).toList()
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayOrder.forEach { index ->
            val profile = index.toFloat() / (WAVEFORM_BAR_COUNT - 1) * 0.85f + 0.15f
            SingleWaveformBar(profile = profile, isActive = isActive, index = index, transition = transition)
        }
    }
}

@Composable
private fun SingleWaveformBar(
    profile: Float,
    isActive: Boolean,
    index: Int,
    transition: InfiniteTransition,
) {
    val rawFraction by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420 + index * 65, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 50, StartOffsetType.FastForward),
        ),
        label = "bar$index",
    )
    val targetHeight = if (isActive) (profile * rawFraction * 36f + 3f) else 4f
    val animatedHeight by animateFloatAsState(
        targetValue = targetHeight,
        animationSpec = tween(250),
        label = "barH$index",
    )

    Box(
        modifier = Modifier
            .width(3.dp)
            .height(animatedHeight.coerceAtLeast(3f).dp)
            .clip(RoundedCornerShape(50))
            .background(JunnzAmber.copy(alpha = if (index < 2) 0.35f else 1f)),
    )
}

// ── Language pill ─────────────────────────────────────────────────────────────

@Composable
private fun LanguagePill() {
    Surface(
        shape = RoundedCornerShape(50),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurface)
            Text("English (US)", style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceVariant)
        }
    }
}

// ── Examples card ─────────────────────────────────────────────────────────────

private data class VoiceExample(
    val icon: ImageVector,
    val iconBg: Color,
    val iconFg: Color,
    val text: String,
    val trigger: String,
)

@Composable
private fun ExamplesCard() {
    val examples = remember {
        listOf(
            VoiceExample(Icons.Rounded.Event, BadgePurpleBg, BadgePurpleFg,
                "Remind me to apply hair serum", "this Saturday at 9:30 AM"),
            VoiceExample(Icons.Rounded.LocationOn, BadgeGreenBg, BadgeGreenFg,
                "Remind me to buy groceries", "when I reach the supermarket"),
            VoiceExample(Icons.Rounded.Person, BadgeBlueBg, BadgeBlueFg,
                "Remind me to call mom", "tomorrow evening"),
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column {
            examples.forEachIndexed { index, example ->
                ExampleRow(example)
                if (index < examples.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Outline.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(example: VoiceExample) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(example.iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = example.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = example.iconFg,
            )
        }
        Column {
            Text(
                text = example.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = example.trigger,
                style = MaterialTheme.typography.bodySmall,
                color = JunnzAmber,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Bottom section ────────────────────────────────────────────────────────────

@Composable
private fun CaptureBottomSection(onHomeTap: () -> Unit) {
    Column {
        Surface(color = Background) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Outline),
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = "Capture via",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CaptureMethodItem(Icons.Rounded.Mic, "Voice", selected = true, Modifier.weight(1f))
                        CaptureMethodItem(Icons.Rounded.Keyboard, "Text", modifier = Modifier.weight(1f))
                        CaptureMethodItem(Icons.Rounded.CameraAlt, "Camera", modifier = Modifier.weight(1f))
                        CaptureMethodItem(Icons.Rounded.Image, "Photo", modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                        .height(88.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(31.dp),
                        color = SurfaceContainerHigh,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CaptureNavItem(icon = Icons.Rounded.Home, label = "Home", onClick = onHomeTap)
                            Spacer(Modifier.width(64.dp))
                            CaptureNavItem(icon = Icons.Rounded.Person, label = "Profile", onClick = {})
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .align(Alignment.TopCenter)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = JunnzAmber.copy(alpha = 0.45f),
                                spotColor = JunnzAmber.copy(alpha = 0.5f),
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(JunnzGreenBright, JunnzGreen),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureMethodItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) JunnzAmberLight else SurfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) JunnzAmber else OnSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) JunnzAmber else OnSurfaceVariant,
        )
    }
}

@Composable
private fun CaptureNavItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = OnSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Text(text = label, fontSize = 11.sp, color = OnSurfaceVariant)
    }
}
