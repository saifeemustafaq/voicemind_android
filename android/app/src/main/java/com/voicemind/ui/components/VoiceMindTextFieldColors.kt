package com.voicemind.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Returns M3-compliant text field colors using the active color scheme.
// focusedBorder   → colorScheme.primary
// unfocusedBorder → colorScheme.outline
// No container fill — keeps the minimalist appearance.
@Composable
fun voiceMindTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor   = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)
