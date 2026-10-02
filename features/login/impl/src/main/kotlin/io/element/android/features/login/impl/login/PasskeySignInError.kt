/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

/** Errors specific to signing in with a passkey. `ChangeServerError.from` surfaces them as a plain error dialog. */
sealed class PasskeySignInError(message: String) : Exception(message) {
    /** The deployment has no default account provider configured (dev deployment keys absent). */
    data object NotConfigured : PasskeySignInError("The default account provider is not configured.")
}
