/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

sealed class PasskeySignInError(message: String) : Exception(message) {
    data object NotConfigured : PasskeySignInError("The default account provider is not configured.")
}
