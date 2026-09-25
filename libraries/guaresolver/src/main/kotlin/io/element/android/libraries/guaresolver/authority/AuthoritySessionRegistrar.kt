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

/**
 * GUA FORK: the two things a session start owes the authority chain (ADM-009 gate 2 and decision 5).
 *
 * Both are best effort and neither blocks anything the user is doing. What they are not is optional: a pending
 * transition with no security-notification registration is a window nobody hears about, which is the state
 * gate 2 exists to forbid, and a device with account access and no offered key is a device the account's other
 * phones cannot add.
 *
 * WHY THIS IS NOT THE MATRIX PUSHER. A pusher lives under a session, and completing an account recovery ends
 * every session of the user, so the destination would die with the thing the attacker just destroyed. This
 * registration is keyed on an installation id sealed on the device and stable across sign-out, and it is made
 * whether or not a pusher was registered.
 */
interface AuthoritySessionRegistrar {
    /**
     * @param sessionId the session that just started, whose own token these calls are made under.
     * @param pushToken the current push destination, or null when this install has none yet. Without one there
     * is nothing to register: a row with no destination is not a channel.
     * @param platform APNS or FCM, as [SecurityNotificationRegistration] names them. A destination this server
     * cannot deliver to is not registered at all rather than registered and silent.
     * @param appId the same app id the Matrix pusher sends, so the topic or project comes from one constant.
     * @param deviceLabel at most the 16 bytes of UTF-8 a record's label may carry, because that is all a
     * notification is allowed to name.
     */
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
        // The flag first, and nothing else while it is off: a deployment without this feature makes no request
        // here at all, which is what the flags-off test asserts.
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
                // The code only. A body here carries a push token, and this failure is not something the user
                // is waiting on.
                Timber.w("Could not register the security notification channel: %s", error.javaClass.simpleName)
            }
        }

        // The other end of the candidate step. A device that already holds authority has nothing to offer, and
        // an account that holds none has nobody to grant it, so both are left alone.
        if (authorityManager.holdsAuthority()) return
        val chain = authorityManager.state(accessToken).getOrNull() ?: return
        if (chain.state != AuthorityChainState.STATE_ROOTED) return
        authorityManager.offerThisDeviceForGrant(accessToken, deviceLabel).onFailure { error ->
            Timber.w("Could not offer this device for a grant: %s", error.javaClass.simpleName)
        }
    }
}
