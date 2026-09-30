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

    // GUA FORK: the marker names the device whose crypto store holds the pending identity. Logout
    // discards that store, and the next login on this phone is a new device, so a marker left by an
    // earlier device of the same account must not send the healthy new login into a reset. The
    // preferences file lives outside the session directory and outlives logout, which is why the
    // device is part of the value rather than the key being cleared on the way out.
    private val key: String
        get() = "pending_device_" + matrixClient.sessionId.value

    /**
     * The earlier, device-less form of the marker. Deliberately never read: it cannot say which
     * device wrote it, and honouring it would send a healthy re-login into a reset, which is the
     * defect the device key fixes. The one thing it could still protect, a reset started on this
     * very device before the upgrade and never approved, is the setup banner's ordinary repair
     * path either way. Removed on every write so it cannot linger.
     */
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
