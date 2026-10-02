/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

sealed interface AccountAuthorityEvent {
    data object Refresh : AccountAuthorityEvent

    data object StartAdoption : AccountAuthorityEvent

    data class ConfirmArtifactStored(val confirmed: Boolean) : AccountAuthorityEvent

    data object ContinueFromArtifact : AccountAuthorityEvent

    data object StartRecovery : AccountAuthorityEvent

    data class RecoveryArtifactChanged(val artifact: String) : AccountAuthorityEvent

    data object ContinueFromRecoveryEntry : AccountAuthorityEvent

    data object StartAccountRecovery : AccountAuthorityEvent

    data class AcknowledgeAccountRecovery(val acknowledged: Boolean) : AccountAuthorityEvent

    data object ContinueFromAccountRecoveryNotice : AccountAuthorityEvent

    data object OfferThisDevice : AccountAuthorityEvent

    data class SelectCandidate(val deviceKeyB64Url: String) : AccountAuthorityEvent

    data class ConfirmFingerprint(val confirmed: Boolean) : AccountAuthorityEvent

    data object ContinueFromCompare : AccountAuthorityEvent

    data class StartRevocation(val deviceKeyB64Url: String) : AccountAuthorityEvent

    data class PinChanged(val pin: String) : AccountAuthorityEvent

    data object Submit : AccountAuthorityEvent

    data object ConfirmInBrowser : AccountAuthorityEvent

    data object ClearWebStepUpUrl : AccountAuthorityEvent

    data object Oppose : AccountAuthorityEvent

    data class Approve(val approvalId: String) : AccountAuthorityEvent

    data class RemoveSecurityNotification(val installationId: String) : AccountAuthorityEvent

    data object Cancel : AccountAuthorityEvent

    data object ClearSuccess : AccountAuthorityEvent
}
