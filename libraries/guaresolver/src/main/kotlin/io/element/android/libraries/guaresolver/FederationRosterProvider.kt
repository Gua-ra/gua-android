/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/** Null when the roster is unavailable. Federated search then degrades to local-only. */
interface FederationRosterProvider {
    suspend fun currentRoster(): FederationRoster?
}
