/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

/**
 * GUA FORK: UI actions for the change-phone-number screen.
 */
sealed interface ChangePhoneNumberEvents {
    /** The user edited the 6-digit code field (account PIN or OTP). */
    data class CodeChanged(val code: String) : ChangePhoneNumberEvents

    /** The user edited the new phone field. */
    data class PhoneChanged(val phone: String) : ChangePhoneNumberEvents

    /** The user tapped the primary "Continue" button. */
    data object Continue : ChangePhoneNumberEvents

    /** The user cancelled the in-progress flow. */
    data object CancelEntry : ChangePhoneNumberEvents

    /** The user tapped "Done" on the success screen; finish and pop back to settings. */
    data object Done : ChangePhoneNumberEvents
}
