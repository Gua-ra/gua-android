/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

/**
 * The signup could not be given the account genesis it meant to register.
 *
 * Deliberately a hard failure rather than a fallback: a device that meant to register a genesis and
 * quietly continued would create an account with a bootstrap id instead. A deployment that answers
 * that it does not do genesis is a different case and continues silently.
 */
sealed class AccountGenesisSignupError : Exception() {
    /** The device could not generate, register or sign for its account authority key. */
    data object SetupFailed : AccountGenesisSignupError()
}
