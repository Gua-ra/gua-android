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
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityFingerprint
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.launch

@AssistedInject
class GrantAuthorityPresenter(
    @Assisted private val candidate: AuthorityCandidate,
    @Assisted private val onDone: () -> Unit,
    private val sessionId: SessionId,
    private val sessionStore: SessionStore,
    private val authorityManager: AccountAuthorityManager,
) : Presenter<GrantAuthorityState> {
    @AssistedFactory
    interface Factory {
        fun create(candidate: AuthorityCandidate, onDone: () -> Unit): GrantAuthorityPresenter
    }

    @Composable
    override fun present(): GrantAuthorityState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(GrantAuthorityPhase.Prompt) }
        var pin by remember { mutableStateOf("") }
        var fingerprintConfirmed by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<Int?>(null) }

        fun handleEvent(event: GrantAuthorityEvent) {
            when (event) {
                GrantAuthorityEvent.Grant -> {
                    errorMessage = null
                    pin = ""
                    fingerprintConfirmed = false
                    phase = GrantAuthorityPhase.Compare
                }
                is GrantAuthorityEvent.ConfirmFingerprint -> {
                    fingerprintConfirmed = event.confirmed
                }
                GrantAuthorityEvent.ContinueFromCompare -> {
                    if (fingerprintConfirmed) {
                        errorMessage = null
                        pin = ""
                        phase = GrantAuthorityPhase.StepUp
                    }
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
                    val chain = authorityManager.state(accessToken).getOrElse { error ->
                        errorMessage = error.toMessageRes()
                        phase = GrantAuthorityPhase.StepUp
                        return@launch
                    }
                    authorityManager.grantDevice(
                        accessToken = accessToken,
                        chain = chain,
                        candidate = candidate,
                        stepUp = AuthorityStepUp.Pin(pin),
                        fingerprintConfirmed = fingerprintConfirmed,
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
            fingerprint = AuthorityFingerprint.grouped(candidate.fingerprint),
            fingerprintConfirmed = fingerprintConfirmed,
            pin = pin,
            errorMessage = errorMessage,
            eventSink = ::handleEvent,
        )
    }
}

private fun Throwable.toMessageRes(): Int = when (this) {
    is AuthorityError.UnknownCandidate -> R.string.screen_link_grant_authority_error_unknown_candidate
    is AuthorityError.NoNotificationChannel -> R.string.screen_link_grant_authority_error_no_channel
    is AuthorityError.StepUpRequired -> R.string.screen_link_grant_authority_error_step_up_required
    is AuthorityError.FactorTooFresh,
    is AuthorityError.RecoveryTooRecent -> R.string.screen_link_grant_authority_error_too_recent
    is AuthorityError.DeviceQuarantined -> R.string.screen_link_grant_authority_error_quarantined
    is AuthorityError.SignerRefused -> R.string.screen_link_grant_authority_error_signer_refused
    is AuthorityError.PendingConflict,
    is AuthorityError.HeadConflict -> R.string.screen_link_grant_authority_error_conflict
    else -> R.string.screen_link_grant_authority_error_generic
}
