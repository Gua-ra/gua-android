/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: talks to the Gua resolver (`POST /resolve`) — the federation front door that maps a
 * phone number to a homeserver, so the client never hardcodes one. Android counterpart of iOS
 * `ResolverClientProtocol` / `ResolverClient`.
 */
interface ResolverClient {
    /**
     * Resolve a verified E.164 phone number to the homeserver it belongs to (login) or should be
     * created on (register).
     *
     * @param e164Phone the phone number in E.164 form (e.g. `+15551234567`).
     * @return [Result.success] with the [HomeserverResolution], or [Result.failure] with a
     * [ResolverError] (notably [ResolverError.NotConfigured] when no resolver URL is configured).
     */
    suspend fun resolve(e164Phone: String): Result<HomeserverResolution>
}
