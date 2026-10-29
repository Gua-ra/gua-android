/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.guaresolver.HomeserverResolution

/**
 * Report-only verification of a resolver answer against the signed roster, the transparency log and the
 * routing policy, under the trust roots pinned for the deployment. The outcome never changes what the app
 * connects to: it is logged and kept for inspection.
 */
interface ResolverVerifier {
    /** Null when the deployment has no resolver: then nothing runs and nothing is logged. */
    suspend fun verify(e164Phone: String, resolution: HomeserverResolution): ResolverVerificationReport?
}
