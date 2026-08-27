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

/** Filled dot used to mask each entered PIN digit (U+2022 BULLET). */
private const val MASK_CHARACTER = "•"

/**
 * GUA FORK: the shared bubble code field used by both the two-step-verification and the change-phone
 * PIN/OTP steps, so they render to the same polished standard (matching iOS' `PinBubbleField`).
 *
 * [length] bubbles are drawn over a single hidden [BasicTextField]; tapping anywhere focuses the
 * field and each typed digit fills the next bubble. Filled bubbles get an emphasised border, and the
 * whole row turns critical when [hasError] is set.
 *
 * Set [masked] to render each filled position as a password-style dot instead of the digit. This is
 * used for the secret PIN steps (change-phone PIN, 2SV PIN) and left `false` for the OTP step, where
 * the user needs to read back the code they were sent.
 */
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
                    // GUA FORK: when [masked], render a filled dot (password-style) instead of the
                    // digit, matching iOS `PinBubbleField`, so a PIN is never shown in clear. An
                    // empty position renders nothing; a filled one renders the digit (OTP) or a
                    // single bullet (PIN).
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
        // Invisible input layered over the bubbles to capture the keyboard.
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
