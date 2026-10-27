/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import android.content.Context
import android.content.SharedPreferences
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import kotlinx.coroutines.withContext

/** The devices of this account whose verification request the user ignored. Persisted per account and cleared on sign-out. */
interface DismissedSignInStore {
    suspend fun dismissedDevices(): Set<DeviceId>

    suspend fun dismiss(deviceId: DeviceId)
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultDismissedSignInStore(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    private val sessionObserver: SessionObserver,
    private val dispatchers: CoroutineDispatchers,
) : DismissedSignInStore {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val key: String get() = KEY_PREFIX + matrixClient.sessionId.value

    private val sessionListener = object : SessionListener {
        override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
            if (userId != matrixClient.sessionId.value) return
            withContext(dispatchers.io) { preferences.edit().remove(key).apply() }
            sessionObserver.removeListener(this)
        }
    }

    init {
        sessionObserver.addListener(sessionListener)
    }

    override suspend fun dismissedDevices(): Set<DeviceId> = withContext(dispatchers.io) {
        preferences.getStringSet(key, null).orEmpty().mapTo(mutableSetOf()) { DeviceId(it) }
    }

    override suspend fun dismiss(deviceId: DeviceId) = withContext(dispatchers.io) {
        // The returned set must not be modified in place.
        val updated = preferences.getStringSet(key, null).orEmpty() + deviceId.value
        preferences.edit().putStringSet(key, updated).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "gua_new_sign_in"
        const val KEY_PREFIX = "dismissed_devices_"
    }
}
