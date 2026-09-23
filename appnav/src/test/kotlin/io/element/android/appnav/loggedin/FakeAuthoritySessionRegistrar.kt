/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.loggedin

import io.element.android.libraries.guaresolver.authority.AuthoritySessionRegistrar

/**
 * GUA FORK: records what a session start handed the authority registrar (ADM-009 gate 2).
 *
 * What a test asserts with this is mostly what is absent: the push destination is only offered when the
 * install actually has one, and it is offered without the pusher's registration having to succeed first,
 * because a pusher dies with the session an account recovery revokes.
 */
class FakeAuthoritySessionRegistrar : AuthoritySessionRegistrar {
    data class Call(
        val sessionId: String,
        val pushToken: String?,
        val platform: String?,
        val appId: String,
        val deviceLabel: String,
    )

    val calls: MutableList<Call> = mutableListOf()

    override suspend fun onSessionStarted(
        sessionId: String,
        pushToken: String?,
        platform: String?,
        appId: String,
        deviceLabel: String,
    ) {
        calls += Call(sessionId, pushToken, platform, appId, deviceLabel)
    }
}
