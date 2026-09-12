/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

/**
 * GUA FORK: the signup could not be given the account genesis it meant to register (ADM-008 Phase 3).
 *
 * This is deliberately a hard failure rather than a fallback. A device that meant to register a genesis
 * and quietly continued would create an account with a bootstrap id instead, and a silent downgrade of
 * exactly that shape is the failure mode ADM-008 decision 6 warns about. A deployment that answers that
 * it does not do genesis at all is a different case: that one continues silently, with no error, because
 * a signup which presents no handle takes the bootstrap branch by design.
 */
sealed class AccountGenesisSignupError : Exception() {
    /** The device could not generate, register or sign for its account authority key. */
    data object SetupFailed : AccountGenesisSignupError()
}
