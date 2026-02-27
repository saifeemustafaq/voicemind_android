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
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmWhite

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
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VmDeepViolet,
            contentColor = VmWhite,
            disabledContainerColor = VmDeepViolet.copy(alpha = 0.5f),
            disabledContentColor = VmWhite.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = VmWhite
        )
    }
}
