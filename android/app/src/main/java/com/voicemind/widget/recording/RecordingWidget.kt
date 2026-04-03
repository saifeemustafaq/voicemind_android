package com.voicemind.widget.recording

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.voicemind.R
import com.voicemind.service.RecordingService
import com.voicemind.util.formatRecordingTime
import com.voicemind.widget.common.MicPermissionContent
import com.voicemind.widget.common.SignedOutContent
import com.voicemind.widget.common.WidgetColors
import com.voicemind.widget.common.WidgetTitle

class RecordingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isSignedIn = prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] ?: false
            val needsMicPermission = prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] ?: false
            val isRecording = prefs[RecordingWidgetStateKeys.IS_RECORDING] ?: false
            val isPaused = prefs[RecordingWidgetStateKeys.IS_PAUSED] ?: false
            val elapsedSeconds = prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] ?: 0L

            GlanceTheme {
                WidgetRoot(isSignedIn, needsMicPermission, isRecording, isPaused, elapsedSeconds)
            }
        }
    }
}

@Composable
private fun WidgetRoot(
    isSignedIn: Boolean,
    needsMicPermission: Boolean,
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedSeconds: Long,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !isSignedIn -> SignedOutContent()
            needsMicPermission -> MicPermissionContent()
            isRecording || isPaused -> ActiveContent(isPaused, elapsedSeconds)
            else -> IdleContent()
        }
    }
}

@Composable
private fun IdleContent() {
    val context = LocalContext.current
    WidgetTitle()
    Spacer(modifier = GlanceModifier.height(12.dp))
    Image(
        provider = ImageProvider(R.drawable.ic_mic_widget_btn),
        contentDescription = "Start recording",
        modifier = GlanceModifier
            .size(56.dp)
            .clickable(
                actionStartService(
                    RecordingService.buildIntent(context, RecordingService.ACTION_START),
                    isForegroundService = true,
                )
            ),
    )
}

@Composable
private fun ActiveContent(isPaused: Boolean, elapsedSeconds: Long) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth(),
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_rec_dot),
            contentDescription = null,
            modifier = GlanceModifier.size(8.dp),
            colorFilter = ColorFilter.tint(
                if (isPaused) WidgetColors.Warning else WidgetColors.Destructive
            ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = if (isPaused) "Paused" else "Recording",
            style = TextStyle(
                color = if (isPaused) WidgetColors.Warning else WidgetColors.Destructive,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
    }

    Spacer(modifier = GlanceModifier.height(4.dp))

    Text(
        text = formatRecordingTime(elapsedSeconds),
        style = TextStyle(
            color = WidgetColors.Label,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        ),
    )

    Spacer(modifier = GlanceModifier.height(12.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_delete_widget_btn),
            contentDescription = "Discard recording",
            modifier = GlanceModifier
                .size(44.dp)
                .clickable(
                    actionStartService(
                        RecordingService.buildIntent(context, RecordingService.ACTION_DISCARD),
                        isForegroundService = true,
                    )
                ),
        )

        Spacer(modifier = GlanceModifier.width(20.dp))

        Image(
            provider = ImageProvider(
                if (isPaused) R.drawable.ic_resume_widget_btn else R.drawable.ic_pause_widget_btn
            ),
            contentDescription = if (isPaused) "Resume" else "Pause",
            modifier = GlanceModifier
                .size(44.dp)
                .clickable(
                    actionStartService(
                        RecordingService.buildIntent(
                            context,
                            if (isPaused) RecordingService.ACTION_RESUME
                            else RecordingService.ACTION_PAUSE,
                        ),
                        isForegroundService = true,
                    )
                ),
        )

        Spacer(modifier = GlanceModifier.width(20.dp))

        Image(
            provider = ImageProvider(R.drawable.ic_stop_widget_btn),
            contentDescription = "Stop and save",
            modifier = GlanceModifier
                .size(48.dp)
                .clickable(
                    actionStartService(
                        RecordingService.buildIntent(context, RecordingService.ACTION_STOP_SAVE),
                        isForegroundService = true,
                    )
                ),
        )
    }
}
