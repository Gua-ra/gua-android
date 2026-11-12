/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

class InMemoryExpressionPanelStore(
    var tab: ExpressionTab? = null,
    var noteShown: Boolean = false,
) : ExpressionPanelStore {
    override suspend fun lastTab(): ExpressionTab? = tab

    override suspend fun setLastTab(tab: ExpressionTab) {
        this.tab = tab
    }

    override suspend fun hasShownEditorNote(): Boolean = noteShown

    override suspend fun setEditorNoteShown() {
        noteShown = true
    }
}
