/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

/** A device that meant to register an account genesis and could not must fail the signup, never continue without one. */
sealed class AccountGenesisSignupError : Exception() {
    data object SetupFailed : AccountGenesisSignupError()
}
