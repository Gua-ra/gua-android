/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.sessionstorage.api.SessionStore
import timber.log.Timber

interface AuthoritySessionRegistrar {
    suspend fun onSessionStarted(
        sessionId: String,
        pushToken: String?,
        platform: String?,
        appId: String,
        deviceLabel: String,
    )
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAuthoritySessionRegistrar(
    private val featureFlagService: FeatureFlagService,
    private val sessionStore: SessionStore,
    private val authorityManager: AccountAuthorityManager,
) : AuthoritySessionRegistrar {
    override suspend fun onSessionStarted(
        sessionId: String,
        pushToken: String?,
        platform: String?,
        appId: String,
        deviceLabel: String,
    ) {
        if (!featureFlagService.isFeatureEnabled(FeatureFlags.AccountAuthority)) return
        val accessToken = sessionStore.getSession(sessionId)?.accessToken ?: return

        if (pushToken != null && platform != null) {
            authorityManager.registerSecurityNotifications(
                accessToken = accessToken,
                pushToken = pushToken,
                platform = platform,
                appId = appId,
                deviceLabel = deviceLabel,
            ).onFailure { error ->
                Timber.w("Could not register the security notification channel: %s", error.javaClass.simpleName)
            }
        }

        if (authorityManager.holdsAuthority()) return
        val chain = authorityManager.state(accessToken).getOrNull() ?: return
        if (chain.state != AuthorityChainState.STATE_ROOTED) return
        authorityManager.offerThisDeviceForGrant(accessToken, deviceLabel).onFailure { error ->
            Timber.w("Could not offer this device for a grant: %s", error.javaClass.simpleName)
        }
    }
}
