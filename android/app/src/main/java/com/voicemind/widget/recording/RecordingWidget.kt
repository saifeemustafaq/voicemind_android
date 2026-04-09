package com.voicemind.widget.recording

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.voicemind.MainActivity
import com.voicemind.R
import com.voicemind.service.RecordingService
import com.voicemind.util.formatRecordingTime
import com.voicemind.widget.common.PromptContent
import com.voicemind.widget.common.WidgetColors
import com.voicemind.widget.common.WidgetTitle

private sealed interface WidgetSizeClass {
    data object Minimal2x1 : WidgetSizeClass
    data object Compact2x2 : WidgetSizeClass
    data object Row3x1 : WidgetSizeClass
    data object Row4x1 : WidgetSizeClass
    data object Default3x2 : WidgetSizeClass
    data object Wide4x2 : WidgetSizeClass
}

// Minimal2x1 must be checked first: it satisfies both height < 80 AND width < 180.
private fun DpSize.toSizeClass(): WidgetSizeClass = when {
    height < 80.dp && width < 180.dp -> WidgetSizeClass.Minimal2x1
    height < 80.dp && width >= 250.dp -> WidgetSizeClass.Row4x1
    height < 80.dp -> WidgetSizeClass.Row3x1
    width < 180.dp -> WidgetSizeClass.Compact2x2
    width >= 250.dp -> WidgetSizeClass.Wide4x2
    else -> WidgetSizeClass.Default3x2
}

class RecordingWidget : GlanceAppWidget() {

    companion object {
        private val SIZE_2x1 = DpSize(130.dp, 56.dp)
        private val SIZE_2x2 = DpSize(130.dp, 110.dp)
        private val SIZE_3x1 = DpSize(200.dp, 56.dp)
        private val SIZE_3x2 = DpSize(200.dp, 110.dp)
        private val SIZE_4x1 = DpSize(270.dp, 56.dp)
        private val SIZE_4x2 = DpSize(270.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_2x1, SIZE_2x2, SIZE_3x1, SIZE_3x2, SIZE_4x1, SIZE_4x2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isSignedIn = prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] ?: false
            val needsMicPermission = prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] ?: false
            val isRecording = prefs[RecordingWidgetStateKeys.IS_RECORDING] ?: false
            val isPaused = prefs[RecordingWidgetStateKeys.IS_PAUSED] ?: false
            val elapsedSeconds = prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] ?: 0L

            WidgetRoot(isSignedIn, needsMicPermission, isRecording, isPaused, elapsedSeconds)
        }
    }
}

// ── Island button ─────────────────────────────────────────────────────────────

@Composable
private fun IslandButton(
    icon: Int,
    iconTint: ColorProvider,
    background: ColorProvider,
    size: Dp,
    iconSize: Dp,
    contentDescription: String?,
    action: Action,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(background)
            .cornerRadius(size / 2)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}

// ── Status island pill ────────────────────────────────────────────────────────

@Composable
private fun StatusIsland(isPaused: Boolean, fontSizeSp: Float = 11f) {
    val statusColor = if (isPaused) WidgetColors.Warning else WidgetColors.Destructive
    Row(
        modifier = GlanceModifier
            .background(WidgetColors.IslandSurface)
            .cornerRadius(20.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_rec_dot),
            contentDescription = null,
            modifier = GlanceModifier.size(7.dp),
            colorFilter = ColorFilter.tint(statusColor),
        )
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(
            text = if (isPaused) "PAUSED" else "RECORDING",
            style = TextStyle(
                color = statusColor,
                fontWeight = FontWeight.Medium,
                fontSize = fontSizeSp.sp,
            ),
        )
    }
}

// ── Timer island ──────────────────────────────────────────────────────────────
// label: if set, shown above the timer in labelColor (e.g. "RECORDING"/"PAUSED").

@Composable
private fun TimerIsland(
    timerText: String,
    fontSizeSp: Float,
    label: String? = null,
    labelColor: ColorProvider? = null,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.IslandSurface)
            .cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = TextStyle(
                    color = labelColor ?: WidgetColors.SecondaryLabel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
        }
        Text(
            text = timerText,
            style = TextStyle(
                color = WidgetColors.Label,
                fontWeight = FontWeight.Bold,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// ── Root ──────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetRoot(
    isSignedIn: Boolean,
    needsMicPermission: Boolean,
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedSeconds: Long,
) {
    val sizeClass = LocalSize.current.toSizeClass()
    val isRowSize = sizeClass == WidgetSizeClass.Minimal2x1
        || sizeClass == WidgetSizeClass.Row3x1
        || sizeClass == WidgetSizeClass.Row4x1
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(if (isRowSize) 0.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !isSignedIn -> SignedOutContent(sizeClass)
            needsMicPermission -> MicPermissionContent(sizeClass)
            isRecording || isPaused -> ActiveContent(isPaused, elapsedSeconds, sizeClass)
            else -> IdleContent(sizeClass)
        }
    }
}

// ── Prompt states ─────────────────────────────────────────────────────────────

@Composable
private fun SignedOutContent(sizeClass: WidgetSizeClass) {
    val context = LocalContext.current
    PromptContent(
        subtitle = "Sign in to record",
        buttonLabel = "Sign In",
        intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        compact = sizeClass == WidgetSizeClass.Row3x1 || sizeClass == WidgetSizeClass.Row4x1,
        minimal = sizeClass == WidgetSizeClass.Minimal2x1,
    )
}

@Composable
private fun MicPermissionContent(sizeClass: WidgetSizeClass) {
    val context = LocalContext.current
    PromptContent(
        subtitle = "Microphone access needed",
        buttonLabel = "Open App",
        intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_REQUEST_MIC_PERMISSION, true)
        },
        compact = sizeClass == WidgetSizeClass.Row3x1 || sizeClass == WidgetSizeClass.Row4x1,
        minimal = sizeClass == WidgetSizeClass.Minimal2x1,
    )
}

// ── Idle content ──────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(sizeClass: WidgetSizeClass) {
    val context = LocalContext.current
    val micAction = actionStartService(
        RecordingService.buildIntent(context, RecordingService.ACTION_START),
        isForegroundService = true,
    )
    when (sizeClass) {
        // Single mic button island, centered.
        WidgetSizeClass.Minimal2x1 -> IslandButton(
            icon = R.drawable.ic_mic_24,
            iconTint = WidgetColors.White,
            background = WidgetColors.Accent,
            size = 52.dp,
            iconSize = 30.dp,
            contentDescription = "Start recording",
            action = micAction,
        )
        // Title pill + mic button island, side by side.
        WidgetSizeClass.Row3x1, WidgetSizeClass.Row4x1 -> Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTitle()
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            IslandButton(
                icon = R.drawable.ic_mic_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 50.dp,
                iconSize = 29.dp,
                contentDescription = "Start recording",
                action = micAction,
            )
        }
        // Title + mic button stacked, 2x2.
        WidgetSizeClass.Compact2x2 -> {
            Row(
                modifier = GlanceModifier
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTitle()
            }
            Spacer(modifier = GlanceModifier.height(10.dp))
            IslandButton(
                icon = R.drawable.ic_mic_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 48.dp,
                iconSize = 26.dp,
                contentDescription = "Start recording",
                action = micAction,
            )
        }
        // Title + mic button stacked, 3x2/4x2.
        WidgetSizeClass.Default3x2, WidgetSizeClass.Wide4x2 -> {
            Row(
                modifier = GlanceModifier
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetTitle()
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            IslandButton(
                icon = R.drawable.ic_mic_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 56.dp,
                iconSize = 30.dp,
                contentDescription = "Start recording",
                action = micAction,
            )
        }
    }
}

// ── Active content ────────────────────────────────────────────────────────────

@Composable
private fun ActiveContent(isPaused: Boolean, elapsedSeconds: Long, sizeClass: WidgetSizeClass) {
    val context = LocalContext.current
    val timerText = formatRecordingTime(elapsedSeconds)
    val discardAction = actionStartService(
        RecordingService.buildIntent(context, RecordingService.ACTION_DISCARD),
        isForegroundService = true,
    )
    val pauseResumeAction = actionStartService(
        RecordingService.buildIntent(
            context,
            if (isPaused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
        ),
        isForegroundService = true,
    )
    val stopAction = actionStartService(
        RecordingService.buildIntent(context, RecordingService.ACTION_STOP_SAVE),
        isForegroundService = true,
    )

    when (sizeClass) {

        // ── 2x1: 3 bare buttons, no padding, edge-to-edge ────────────────────
        WidgetSizeClass.Minimal2x1 -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = GlanceModifier.defaultWeight())
            IslandButton(
                icon = R.drawable.ic_delete_24,
                iconTint = WidgetColors.Label,
                background = WidgetColors.ButtonNeutral,
                size = 52.dp, iconSize = 30.dp,
                contentDescription = "Discard",
                action = discardAction,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            IslandButton(
                icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 52.dp, iconSize = 30.dp,
                contentDescription = if (isPaused) "Resume" else "Pause",
                action = pauseResumeAction,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            IslandButton(
                icon = R.drawable.ic_stop_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Destructive,
                size = 52.dp, iconSize = 30.dp,
                contentDescription = "Stop and save",
                action = stopAction,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
        }

        // ── 3x1: [dot + label | divider | timer] pill  |  3 buttons ─────────
        WidgetSizeClass.Row3x1 -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusColor = if (isPaused) WidgetColors.Warning else WidgetColors.Destructive
            // Combined pill: dot + label + vertical divider + timer
            Row(
                modifier = GlanceModifier
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_rec_dot),
                    contentDescription = null,
                    modifier = GlanceModifier.size(7.dp),
                    colorFilter = ColorFilter.tint(statusColor),
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = if (isPaused) "PAUSED" else "REC",
                    style = TextStyle(
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                // Vertical divider
                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(WidgetColors.SecondaryLabel),
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = timerText,
                    style = TextStyle(
                        color = WidgetColors.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            IslandButton(
                icon = R.drawable.ic_delete_24,
                iconTint = WidgetColors.Label,
                background = WidgetColors.ButtonNeutral,
                size = 44.dp, iconSize = 26.dp,
                contentDescription = "Discard",
                action = discardAction,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            IslandButton(
                icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 44.dp, iconSize = 26.dp,
                contentDescription = if (isPaused) "Resume" else "Pause",
                action = pauseResumeAction,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            IslandButton(
                icon = R.drawable.ic_stop_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Destructive,
                size = 44.dp, iconSize = 26.dp,
                contentDescription = "Stop and save",
                action = stopAction,
            )
        }

        // ── 4x1: [status pill]  [timer text]  bare buttons ───────────────────
        WidgetSizeClass.Row4x1 -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIsland(isPaused = isPaused, fontSizeSp = 10f)
            Spacer(modifier = GlanceModifier.width(10.dp))
            Text(
                text = timerText,
                style = TextStyle(
                    color = WidgetColors.Label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            IslandButton(
                icon = R.drawable.ic_delete_24,
                iconTint = WidgetColors.Label,
                background = WidgetColors.ButtonNeutral,
                size = 54.dp, iconSize = 31.dp,
                contentDescription = "Discard",
                action = discardAction,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            IslandButton(
                icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Accent,
                size = 54.dp, iconSize = 31.dp,
                contentDescription = if (isPaused) "Resume" else "Pause",
                action = pauseResumeAction,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            IslandButton(
                icon = R.drawable.ic_stop_24,
                iconTint = WidgetColors.White,
                background = WidgetColors.Destructive,
                size = 54.dp, iconSize = 31.dp,
                contentDescription = "Stop and save",
                action = stopAction,
            )
        }

        // ── 2x2: status + timer + bare buttons spread edge-to-edge ───────────
        WidgetSizeClass.Compact2x2 -> Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusIsland(isPaused = isPaused, fontSizeSp = 11f)
            Spacer(modifier = GlanceModifier.height(6.dp))
            TimerIsland(timerText = timerText, fontSizeSp = 24f)
            Spacer(modifier = GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                IslandButton(
                    icon = R.drawable.ic_delete_24,
                    iconTint = WidgetColors.Label,
                    background = WidgetColors.ButtonNeutral,
                    size = 44.dp, iconSize = 24.dp,
                    contentDescription = "Discard",
                    action = discardAction,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                IslandButton(
                    icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Accent,
                    size = 44.dp, iconSize = 24.dp,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    action = pauseResumeAction,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                IslandButton(
                    icon = R.drawable.ic_stop_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Destructive,
                    size = 44.dp, iconSize = 24.dp,
                    contentDescription = "Stop and save",
                    action = stopAction,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }

        // ── 3x2: left = label+timer pill, right = 3 buttons stacked ─────────
        WidgetSizeClass.Default3x2 -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusColor = if (isPaused) WidgetColors.Warning else WidgetColors.Destructive
            // Left pill: label stacked above timer
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPaused) "PAUSED" else "RECORDING",
                    style = TextStyle(
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = timerText,
                    style = TextStyle(
                        color = WidgetColors.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            // Right: 3 buttons stacked vertically
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IslandButton(
                    icon = R.drawable.ic_delete_24,
                    iconTint = WidgetColors.Label,
                    background = WidgetColors.ButtonNeutral,
                    size = 30.dp, iconSize = 18.dp,
                    contentDescription = "Discard",
                    action = discardAction,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                IslandButton(
                    icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Accent,
                    size = 30.dp, iconSize = 18.dp,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    action = pauseResumeAction,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                IslandButton(
                    icon = R.drawable.ic_stop_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Destructive,
                    size = 30.dp, iconSize = 18.dp,
                    contentDescription = "Stop and save",
                    action = stopAction,
                )
            }
        }

        // ── 4x2: [label pill] [timer pill, expanded] [3 buttons stacked] ──────
        WidgetSizeClass.Wide4x2 -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: status label pill
            StatusIsland(isPaused = isPaused)
            Spacer(modifier = GlanceModifier.width(8.dp))
            // Center: timer pill, takes remaining width
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(WidgetColors.IslandSurface)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timerText,
                    style = TextStyle(
                        color = WidgetColors.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            // Right: 3 buttons stacked vertically
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IslandButton(
                    icon = R.drawable.ic_delete_24,
                    iconTint = WidgetColors.Label,
                    background = WidgetColors.ButtonNeutral,
                    size = 34.dp, iconSize = 20.dp,
                    contentDescription = "Discard",
                    action = discardAction,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                IslandButton(
                    icon = if (isPaused) R.drawable.ic_play_24 else R.drawable.ic_pause_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Accent,
                    size = 34.dp, iconSize = 20.dp,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    action = pauseResumeAction,
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                IslandButton(
                    icon = R.drawable.ic_stop_24,
                    iconTint = WidgetColors.White,
                    background = WidgetColors.Destructive,
                    size = 34.dp, iconSize = 20.dp,
                    contentDescription = "Stop and save",
                    action = stopAction,
                )
            }
        }
    }
}
