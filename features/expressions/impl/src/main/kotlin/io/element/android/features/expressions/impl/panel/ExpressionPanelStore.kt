/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.impl.ExpressionsPreferences
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.first

interface ExpressionPanelStore {
    suspend fun lastTab(): ExpressionTab?

    suspend fun setLastTab(tab: ExpressionTab)

    suspend fun hasShownEditorNote(): Boolean

    suspend fun setEditorNoteShown()
}

@ContributesBinding(SessionScope::class)
class DataStoreExpressionPanelStore(
    private val preferences: ExpressionsPreferences,
) : ExpressionPanelStore {
    private val lastTabKey = stringPreferencesKey("panel_last_tab")
    private val editorNoteShownKey = booleanPreferencesKey("editor_note_shown")

    override suspend fun lastTab(): ExpressionTab? = runCatchingExceptions {
        preferences.store.data.first()[lastTabKey]?.let { name -> ExpressionTab.entries.firstOrNull { it.name == name } }
    }.getOrNull()

    override suspend fun setLastTab(tab: ExpressionTab) {
        runCatchingExceptions { preferences.store.edit { it[lastTabKey] = tab.name } }
    }

    override suspend fun hasShownEditorNote(): Boolean = runCatchingExceptions {
        preferences.store.data.first()[editorNoteShownKey] == true
    }.getOrDefault(false)

    override suspend fun setEditorNoteShown() {
        runCatchingExceptions { preferences.store.edit { it[editorNoteShownKey] = true } }
    }
}
