/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

/**
 * GUA FORK: errors specific to signing in with a passkey. Same shape as the resolver's
 * `ResolverError`: a typed object with a fixed message, which `ChangeServerError.from` surfaces
 * as a plain error dialog.
 */
sealed class PasskeySignInError(message: String) : Exception(message) {
    /** The deployment has no default account provider configured (dev deployment keys absent). */
    data object NotConfigured : PasskeySignInError("The default account provider is not configured.")
}
