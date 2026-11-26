/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max

data class PanelHeights(
    val opening: Dp,
    val expanded: Dp,
) {
    /** Where a drag of the grabber settles: back to the opening height, up to the expanded one, or null to close. */
    fun settle(height: Dp): Dp? = when {
        height < opening -> null
        height > (opening + expanded) / 2 -> expanded
        else -> opening
    }

    companion object {
        private const val OPENING_FRACTION = 0.6f
        private const val EXPANDED_FRACTION = 0.85f

        /** The panel opens at 60 % of the screen, or at the keyboard's height when that is taller. */
        fun of(keyboardHeight: Dp, screenHeight: Dp): PanelHeights {
            val opening = max(keyboardHeight, screenHeight * OPENING_FRACTION)
            return PanelHeights(opening = opening, expanded = max(opening, screenHeight * EXPANDED_FRACTION))
        }
    }
}
