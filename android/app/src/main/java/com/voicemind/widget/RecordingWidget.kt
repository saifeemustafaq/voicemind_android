package com.voicemind.widget

import android.content.Context
import android.os.Build
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.voicemind.R
import com.voicemind.service.RecordingService
import timber.log.Timber

class RecordingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isRecording = prefs[RecordingWidgetStateKeys.IS_RECORDING] ?: false
            val isPaused = prefs[RecordingWidgetStateKeys.IS_PAUSED] ?: false
            val elapsedSeconds = prefs[RecordingWidgetStateKeys.ELAPSED_SECONDS] ?: 0L

            GlanceTheme {
                WidgetRoot(isRecording, isPaused, elapsedSeconds)
            }
        }
    }
}

@Composable
private fun WidgetRoot(isRecording: Boolean, isPaused: Boolean, elapsedSeconds: Long) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(ImageProvider(R.drawable.widget_background))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRecording || isPaused) {
            ActiveContent(isPaused, elapsedSeconds)
        } else {
            IdleContent()
        }
    }
}

@Composable
private fun IdleContent() {
    Text(
        text = "VoiceMind",
        style = TextStyle(
            color = WidgetColors.Label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        ),
    )
    Spacer(modifier = GlanceModifier.height(10.dp))
    Image(
        provider = ImageProvider(R.drawable.ic_mic_widget_btn),
        contentDescription = "Start recording",
        modifier = GlanceModifier
            .size(56.dp)
            .clickable(
                actionRunCallback<RecordingActionCallback>(
                    actionParametersOf(ActionKey to RecordingService.ACTION_START)
                )
            ),
    )
}

@Composable
private fun ActiveContent(isPaused: Boolean, elapsedSeconds: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth(),
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_rec_dot),
            contentDescription = null,
            modifier = GlanceModifier.size(10.dp),
            colorFilter = ColorFilter.tint(
                if (isPaused) WidgetColors.Warning else WidgetColors.Destructive
            ),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
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
        text = formatTime(elapsedSeconds),
        style = TextStyle(
            color = WidgetColors.Label,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        ),
    )

    Spacer(modifier = GlanceModifier.height(10.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Discard
        Image(
            provider = ImageProvider(R.drawable.ic_delete_widget_btn),
            contentDescription = "Discard recording",
            modifier = GlanceModifier
                .size(44.dp)
                .clickable(
                    actionRunCallback<RecordingActionCallback>(
                        actionParametersOf(ActionKey to RecordingService.ACTION_DISCARD)
                    )
                ),
        )

        Spacer(modifier = GlanceModifier.width(20.dp))

        // Pause / Resume
        Image(
            provider = ImageProvider(
                if (isPaused) R.drawable.ic_resume_widget_btn else R.drawable.ic_pause_widget_btn
            ),
            contentDescription = if (isPaused) "Resume" else "Pause",
            modifier = GlanceModifier
                .size(44.dp)
                .clickable(
                    actionRunCallback<RecordingActionCallback>(
                        actionParametersOf(
                            ActionKey to if (isPaused) RecordingService.ACTION_RESUME
                            else RecordingService.ACTION_PAUSE
                        )
                    )
                ),
        )

        Spacer(modifier = GlanceModifier.width(20.dp))

        // Stop and save
        Image(
            provider = ImageProvider(R.drawable.ic_stop_widget_btn),
            contentDescription = "Stop and save",
            modifier = GlanceModifier
                .size(48.dp)
                .clickable(
                    actionRunCallback<RecordingActionCallback>(
                        actionParametersOf(ActionKey to RecordingService.ACTION_STOP_SAVE)
                    )
                ),
        )
    }
}

private fun formatTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

private val ActionKey = ActionParameters.Key<String>("recording_action")

class RecordingActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[ActionKey] ?: return
        Timber.d("Widget action: $action")

        val intent = RecordingService.buildIntent(context, action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

class RecordingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecordingWidget()
}
