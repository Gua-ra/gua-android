/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

private val DefaultFieldShape = RoundedCornerShape(12.dp)
private val DefaultFieldHeight = 56.dp

data class PhoneNumberEntryStyle(
    val fieldFill: Color,
    val fieldStroke: Color?,
    val textColor: Color,
    val placeholderColor: Color,
    val dialCodeColor: Color,
    val chevronTint: Color?,
    val fieldShape: RoundedCornerShape = DefaultFieldShape,
    val fieldHeight: Dp = DefaultFieldHeight,
) {
    companion object {
        @Composable
        fun settings(): PhoneNumberEntryStyle = PhoneNumberEntryStyle(
            fieldFill = ElementTheme.colors.bgSubtleSecondary,
            fieldStroke = null,
            textColor = ElementTheme.colors.textPrimary,
            placeholderColor = ElementTheme.colors.textSecondary,
            dialCodeColor = ElementTheme.colors.textPrimary,
            chevronTint = ElementTheme.colors.iconSecondary,
        )
    }
}

/** [localPhoneNumber] is raw national digits. The mask is applied visually by [PhoneNumberVisualTransformation]. */
@Composable
fun PhoneNumberEntryField(
    country: Country,
    localPhoneNumber: String,
    onValueChange: (String) -> Unit,
    onSelectCountry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    style: PhoneNumberEntryStyle = PhoneNumberEntryStyle.settings(),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountrySelectorButton(
            country = country,
            enabled = enabled,
            onClick = onSelectCountry,
            style = style,
        )
        Spacer(modifier = Modifier.width(10.dp))
        PhoneInput(
            value = localPhoneNumber,
            onValueChange = onValueChange,
            placeholder = country.nationalExample,
            country = country,
            enabled = enabled,
            style = style,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountrySelectorButton(
    country: Country,
    enabled: Boolean,
    onClick: () -> Unit,
    style: PhoneNumberEntryStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(style.fieldHeight)
            .clip(style.fieldShape)
            .background(style.fieldFill)
            .then(
                if (style.fieldStroke != null) {
                    Modifier.border(1.dp, style.fieldStroke, style.fieldShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.flag,
            style = ElementTheme.typography.fontHeadingMdBold,
        )
        Text(
            text = "+" + country.dialCode,
            modifier = Modifier.padding(start = 8.dp),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = style.dialCodeColor,
        )
        if (style.chevronTint != null) {
            Icon(
                imageVector = CompoundIcons.ChevronDown(),
                contentDescription = null,
                tint = style.chevronTint,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun PhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    country: Country,
    enabled: Boolean,
    style: PhoneNumberEntryStyle,
    modifier: Modifier = Modifier,
) {
    val textStyle: TextStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = style.textColor)
    // The caret lives here: the String overload of BasicTextField resets it whenever the presenter's value arrives late.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        // The presenter changed the digits (paste normalised or country switched): adopt them with the caret at the end.
        fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(style.textColor),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
        visualTransformation = remember(country) { PhoneNumberVisualTransformation(country) },
        modifier = modifier
            .height(style.fieldHeight)
            .clip(style.fieldShape)
            .background(style.fieldFill)
            .then(
                if (style.fieldStroke != null) {
                    Modifier.border(1.dp, style.fieldStroke, style.fieldShape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = style.placeholderColor,
                    )
                }
                innerTextField()
            }
        },
    )
}
