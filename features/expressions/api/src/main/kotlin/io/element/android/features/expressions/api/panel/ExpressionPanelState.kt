/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.panel

import androidx.compose.runtime.Stable

@Stable
interface ExpressionPanelState {
    val isEnabled: Boolean
    val isOpen: Boolean
    val eventSink: (ExpressionPanelEvent) -> Unit

    companion object {
        val Disabled: ExpressionPanelState = object : ExpressionPanelState {
            override val isEnabled = false
            override val isOpen = false
            override val eventSink: (ExpressionPanelEvent) -> Unit = {}
        }
    }
}
