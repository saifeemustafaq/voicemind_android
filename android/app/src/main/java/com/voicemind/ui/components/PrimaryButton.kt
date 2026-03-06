package com.voicemind.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosWhite
import com.voicemind.ui.theme.VmDimens

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(VmDimens.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(VmDimens.RadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = IosAccent,
            contentColor = IosWhite,
            disabledContainerColor = IosAccent.copy(alpha = 0.4f),
            disabledContentColor = IosWhite.copy(alpha = 0.6f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = IosWhite
        )
    }
}
