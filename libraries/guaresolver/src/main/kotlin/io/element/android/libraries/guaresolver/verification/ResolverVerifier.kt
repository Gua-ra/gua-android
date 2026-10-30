/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.guaresolver.HomeserverResolution

/** Report-only: the outcome is logged and never changes what the app connects to. */
interface ResolverVerifier {
    /** Null when the deployment has no resolver: then nothing runs and nothing is logged. */
    suspend fun verify(e164Phone: String, resolution: HomeserverResolution): ResolverVerificationReport?
}
