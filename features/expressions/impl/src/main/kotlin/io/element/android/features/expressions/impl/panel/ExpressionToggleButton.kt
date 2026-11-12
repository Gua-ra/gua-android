/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.expressions.impl.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton

@Composable
internal fun ExpressionToggleButton(isPanelOpen: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isPanelOpen) CompoundIcons.Keyboard() else CompoundIcons.Reaction(),
            contentDescription = stringResource(if (isPanelOpen) R.string.gua_a11y_expressions_keyboard else R.string.gua_a11y_expressions_open),
            tint = ElementTheme.colors.iconSecondary,
        )
    }
}
