/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.panel

import io.element.android.libraries.architecture.Presenter

fun interface ExpressionPanelPresenter : Presenter<ExpressionPanelState> {
    fun interface Factory {
        fun create(composer: ExpressionComposer): ExpressionPanelPresenter
    }
}
