package com.voicemind.ui.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosOpaqueSeparator

@Composable
fun voiceMindTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = IosAccent,
    unfocusedBorderColor = IosOpaqueSeparator,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)
