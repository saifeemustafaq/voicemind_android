package com.voicemind.widget.common

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

/**
 * Generic prompt layout: app title + subtitle + a single action button.
 * The caller is responsible for building the [intent] — keeping this composable
 * free of any app-layer (MainActivity) imports.
 */
@Composable
internal fun PromptContent(subtitle: String, buttonLabel: String, intent: Intent) {
    WidgetTitle()
    Spacer(modifier = GlanceModifier.height(4.dp))
    Text(
        text = subtitle,
        style = TextStyle(
            color = WidgetColors.SecondaryLabel,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        ),
    )
    Spacer(modifier = GlanceModifier.height(12.dp))
    Row(
        modifier = GlanceModifier
            .background(WidgetColors.Accent)
            .cornerRadius(16.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buttonLabel,
            style = TextStyle(
                color = WidgetColors.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
internal fun WidgetTitle(text: String = "VoiceMind") {
    Text(
        text = text,
        style = TextStyle(
            color = WidgetColors.Label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        ),
    )
}
