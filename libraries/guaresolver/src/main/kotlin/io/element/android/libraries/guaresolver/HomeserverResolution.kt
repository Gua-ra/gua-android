/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

data class ResolvedHomeserver(
    val serverName: String,
    val baseUrl: String,
    val masIssuer: String?,
    val region: String?,
)

data class HomeserverResolution(
    val exists: Boolean,
    val homeserver: ResolvedHomeserver,
)
