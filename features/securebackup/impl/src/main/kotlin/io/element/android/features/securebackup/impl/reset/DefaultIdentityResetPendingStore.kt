/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import android.content.Context
import android.content.SharedPreferences
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.securebackup.api.IdentityResetPendingStore
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultIdentityResetPendingStore(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
) : IdentityResetPendingStore {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val key: String
        get() = "pending_" + matrixClient.sessionId.value

    override fun isPending(): Boolean = preferences.getBoolean(key, false)

    override fun markPending() {
        preferences.edit().putBoolean(key, true).apply()
    }

    override fun clear() {
        preferences.edit().remove(key).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "gua_identity_reset"
    }
}
