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

    // The value is the device ID. The file outlives logout, and a marker from an earlier device must not count as pending.
    private val key: String
        get() = "pending_device_" + matrixClient.sessionId.value

    /** Device-less legacy marker. Never read; removed on every write. */
    private val legacyKey: String
        get() = "pending_" + matrixClient.sessionId.value

    override fun isPending(): Boolean = preferences.getString(key, null) == matrixClient.deviceId.value

    override fun markPending() {
        preferences.edit().putString(key, matrixClient.deviceId.value).remove(legacyKey).apply()
    }

    override fun clear() {
        preferences.edit().remove(key).remove(legacyKey).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "gua_identity_reset"
    }
}
