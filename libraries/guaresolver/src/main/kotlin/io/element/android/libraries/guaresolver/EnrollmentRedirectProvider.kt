/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/** Where a factor-enrollment ceremony returns to. Must be the custom scheme this build's OAuth intent filter claims. */
interface EnrollmentRedirectProvider {
    /** Null when this build cannot say. The server then uses its configured default. */
    fun provide(): String?
}
