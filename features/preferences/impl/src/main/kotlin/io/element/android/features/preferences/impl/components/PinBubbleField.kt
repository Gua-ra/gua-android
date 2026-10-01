/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text

private const val MASK_CHARACTER = "•"

@Composable
fun PinBubbleField(
    code: String,
    length: Int,
    hasError: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(length) { index ->
                val digit = code.getOrNull(index)?.toString().orEmpty()
                val borderColor = when {
                    hasError -> ElementTheme.colors.textCriticalPrimary
                    digit.isNotEmpty() -> ElementTheme.colors.iconPrimary
                    else -> ElementTheme.colors.borderInteractivePrimary
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = ElementTheme.colors.bgSubtleSecondary,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            digit.isEmpty() -> ""
                            masked -> MASK_CHARACTER
                            else -> digit
                        },
                        style = ElementTheme.typography.fontHeadingMdBold,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
            }
        }
        BasicTextField(
            value = code,
            onValueChange = { onValueChange(it) },
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f),
        )
    }
}
