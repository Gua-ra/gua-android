/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.linknewdevice.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.DeviceGrantCandidate
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the grant offer that follows a link in the one permitted direction (ADM-009
 * decision 5).
 *
 * The grant is deliberately a separate decision from the link, taken after it. Linking gives the new device
 * account access; this gives it the power to approve other devices and to approve what a browser cannot.
 * Declining is a real answer and leaves a working, signed-in device behind, which is why the screen offers
 * it as plainly as it offers the grant.
 *
 * WHAT THE CHECK CODE ACTUALLY PROVED. The 2-digit code the user typed is the only anti-tamper check on the
 * linking channel, and on Android `RustCheckCodeSender.validate()` returns true without consulting the SDK,
 * so a wrong code is only caught by the far end. That is the ground this grant stands on, and it is why the
 * grantee is quarantined for a full window afterwards: a device granted in error can do nothing during it,
 * counts for nothing, and can be opposed.
 */
@AssistedInject
class GrantAuthorityPresenter(
    @Assisted private val candidate: DeviceGrantCandidate,
    @Assisted private val onDone: () -> Unit,
    private val sessionId: SessionId,
    private val sessionStore: SessionStore,
    private val authorityManager: AccountAuthorityManager,
) : Presenter<GrantAuthorityState> {
    @AssistedFactory
    interface Factory {
        fun create(candidate: DeviceGrantCandidate, onDone: () -> Unit): GrantAuthorityPresenter
    }

    @Composable
    override fun present(): GrantAuthorityState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(GrantAuthorityPhase.Prompt) }
        var pin by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }

        fun handleEvent(event: GrantAuthorityEvent) {
            when (event) {
                GrantAuthorityEvent.Grant -> {
                    errorMessage = null
                    pin = ""
                    phase = GrantAuthorityPhase.StepUp
                }
                is GrantAuthorityEvent.PinChanged -> {
                    pin = event.pin.filter { it.isDigit() }.take(GrantAuthorityState.PIN_LENGTH)
                    errorMessage = null
                }
                GrantAuthorityEvent.Submit -> coroutineScope.launch {
                    val accessToken = sessionStore.getSession(sessionId.value)?.accessToken
                    if (accessToken == null) {
                        errorMessage = R.string.screen_link_grant_authority_error_generic
                        return@launch
                    }
                    phase = GrantAuthorityPhase.Submitting
                    // Read the chain first, every time. The grant appends at the head the server reports,
                    // and a head this screen remembered from before the link would be refused.
                    val chain = authorityManager.state(accessToken).getOrElse { error ->
                        errorMessage = error.toMessageRes()
                        phase = GrantAuthorityPhase.StepUp
                        return@launch
                    }
                    authorityManager.grantDevice(
                        accessToken = accessToken,
                        chain = chain,
                        granteeDeviceKeyB64Url = candidate.deviceKeyB64Url,
                        label = candidate.label,
                        pin = pin,
                    )
                        .onSuccess {
                            pin = ""
                            phase = GrantAuthorityPhase.Done
                            onDone()
                        }
                        .onFailure { error ->
                            errorMessage = error.toMessageRes()
                            pin = ""
                            phase = GrantAuthorityPhase.StepUp
                        }
                }
                GrantAuthorityEvent.Skip -> {
                    phase = GrantAuthorityPhase.Done
                    onDone()
                }
            }
        }

        return GrantAuthorityState(
            phase = phase,
            deviceLabel = candidate.label,
            pin = pin,
            errorMessage = errorMessage,
            eventSink = ::handleEvent,
        )
    }
}

private fun Throwable.toMessageRes(): Int = when (this) {
    is AuthorityError.StepUpRequired -> R.string.screen_link_grant_authority_error_step_up_required
    is AuthorityError.FactorTooFresh,
    is AuthorityError.RecoveryTooRecent -> R.string.screen_link_grant_authority_error_too_recent
    is AuthorityError.DeviceQuarantined -> R.string.screen_link_grant_authority_error_quarantined
    is AuthorityError.SignerRefused -> R.string.screen_link_grant_authority_error_signer_refused
    is AuthorityError.PendingConflict,
    is AuthorityError.HeadConflict -> R.string.screen_link_grant_authority_error_conflict
    else -> R.string.screen_link_grant_authority_error_generic
}
