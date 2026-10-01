/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline.item.event

import androidx.compose.runtime.Immutable

@Immutable
sealed interface MessageShield {
    /** Not enough information available to check the authenticity. */
    data class AuthenticityNotGuaranteed(val isCritical: Boolean) : MessageShield

    /** The sending device isn't yet known by the Client. */
    data class UnknownDevice(val isCritical: Boolean) : MessageShield

    /** The sending device hasn't been verified by the sender. */
    data class UnsignedDevice(val isCritical: Boolean) : MessageShield

    /** The sender hasn't been verified by the Client's user. */
    data class UnverifiedIdentity(val isCritical: Boolean) : MessageShield

    /** An unencrypted event in an encrypted room. */
    data class SentInClear(val isCritical: Boolean) : MessageShield

    /** The sender was previously verified but is not anymore. */
    data class VerificationViolation(val isCritical: Boolean) : MessageShield

    /** The sender of the event does not match the owner of the device that created the Megolm session. */
    data class MismatchedSender(val isCritical: Boolean) : MessageShield
}

/** GUA FORK: whether this shield warrants the alarm colour, reserved for states that mean "this may not be who you think". */
val MessageShield.isAlarming: Boolean
    get() = when (this) {
        is MessageShield.VerificationViolation,
        is MessageShield.MismatchedSender,
        is MessageShield.SentInClear -> true
        is MessageShield.AuthenticityNotGuaranteed,
        is MessageShield.UnknownDevice,
        is MessageShield.UnsignedDevice,
        is MessageShield.UnverifiedIdentity -> false
    }

/** GUA FORK: shields that describe the sender's own setup. They are not shown on your own messages. */
val MessageShield.describesOwnSetup: Boolean
    get() = when (this) {
        is MessageShield.UnsignedDevice,
        is MessageShield.UnknownDevice,
        is MessageShield.AuthenticityNotGuaranteed -> true
        is MessageShield.UnverifiedIdentity,
        is MessageShield.VerificationViolation,
        is MessageShield.MismatchedSender,
        is MessageShield.SentInClear -> false
    }

val MessageShield.isCritical: Boolean
    get() = when (this) {
        is MessageShield.AuthenticityNotGuaranteed -> isCritical
        is MessageShield.UnknownDevice -> isCritical
        is MessageShield.UnsignedDevice -> isCritical
        is MessageShield.UnverifiedIdentity -> isCritical
        is MessageShield.SentInClear -> isCritical
        is MessageShield.VerificationViolation -> isCritical
        is MessageShield.MismatchedSender -> isCritical
    }
