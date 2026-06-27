/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the two-step-verification (account PIN) screen. Mirrors the iOS
 * `TwoStepVerificationScreenViewModel` state machine: it loads the PIN status, then drives the
 * setup flow ([TwoStepVerificationPhase.EnteringNew] -> confirm -> submit) and the OTP-protected
 * change flow (phone -> current -> otp -> new -> confirm -> complete), translating typed
 * [ResolverError]s into per-error phase transitions.
 */
@Inject
class TwoStepVerificationPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
) : Presenter<TwoStepVerificationState> {
    @Composable
    override fun present(): TwoStepVerificationState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(TwoStepVerificationPhase.Loading) }
        var code by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var showSuccess by remember { mutableStateOf(false) }

        // Flow scratch state, mirroring the iOS view-state fields.
        var userHasPin by remember { mutableStateOf(false) }
        var currentPin by remember { mutableStateOf("") }
        var stagedNewPin by remember { mutableStateOf("") }
        var challengeId by remember { mutableStateOf<String?>(null) }
        var otpCode by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            phase = TwoStepVerificationPhase.Loading
            val accessToken = accessToken()
            if (accessToken == null) {
                userHasPin = false
                errorMessage = CommonStrings.error_unknown
                phase = TwoStepVerificationPhase.OverviewNoPin
                return@LaunchedEffect
            }
            identityServiceClient.pinStatus(accessToken, matrixClient.sessionId.value)
                .onSuccess { status ->
                    userHasPin = status.hasPin
                    phase = if (status.hasPin) TwoStepVerificationPhase.OverviewHasPin else TwoStepVerificationPhase.OverviewNoPin
                }
                .onFailure {
                    userHasPin = false
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.OverviewNoPin
                }
        }

        fun resetFlowState() {
            errorMessage = null
            currentPin = ""
            stagedNewPin = ""
            challengeId = null
            otpCode = ""
            phone = ""
            code = ""
        }

        fun verifyCurrentPinAndRequestOtp(enteredPin: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                if (phone.isEmpty()) {
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.EnteringPhone
                    return@launch
                }
                val previousPhase = phase
                phase = TwoStepVerificationPhase.Submitting
                identityServiceClient.startPinChange(accessToken = accessToken, phone = phone, currentPin = enteredPin)
                    .onSuccess { newChallengeId ->
                        currentPin = enteredPin
                        challengeId = newChallengeId
                        code = ""
                        errorMessage = null
                        phase = TwoStepVerificationPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidPin -> {
                                errorMessage = R.string.screen_two_step_verification_current_incorrect
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringCurrent
                            }
                            is ResolverError.PinLocked -> {
                                errorMessage = R.string.screen_two_step_verification_locked
                                phase = TwoStepVerificationPhase.OverviewHasPin
                            }
                            is ResolverError.PinChangeCooldown -> {
                                errorMessage = R.string.screen_two_step_verification_cooldown
                                phase = TwoStepVerificationPhase.OverviewHasPin
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                phase = previousPhase
                            }
                            else -> {
                                errorMessage = CommonStrings.error_unknown
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringCurrent
                            }
                        }
                    }
            }
        }

        fun submitNewPin(newPin: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = TwoStepVerificationPhase.Submitting
                val result = if (userHasPin) {
                    val activeChallengeId = challengeId
                    if (activeChallengeId == null) {
                        errorMessage = CommonStrings.error_unknown
                        phase = TwoStepVerificationPhase.OverviewHasPin
                        return@launch
                    }
                    identityServiceClient.completePinChange(
                        accessToken = accessToken,
                        challengeId = activeChallengeId,
                        otpCode = otpCode,
                        newPin = newPin,
                    )
                } else {
                    identityServiceClient.setInitialPin(
                        accessToken = accessToken,
                        userId = matrixClient.sessionId.value,
                        newPin = newPin,
                    )
                }
                result
                    .onSuccess {
                        userHasPin = true
                        resetFlowState()
                        phase = TwoStepVerificationPhase.OverviewHasPin
                        showSuccess = true
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidOtp -> {
                                errorMessage = R.string.screen_two_step_verification_otp_invalid
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringOtp
                            }
                            is ResolverError.PinChangeChallengeInvalid -> {
                                errorMessage = R.string.screen_two_step_verification_challenge_invalid
                                resetFlowState()
                                phase = TwoStepVerificationPhase.OverviewHasPin
                            }
                            is ResolverError.InvalidPin -> {
                                errorMessage = R.string.screen_two_step_verification_current_incorrect
                                code = ""
                                phase = if (userHasPin) TwoStepVerificationPhase.EnteringCurrent else TwoStepVerificationPhase.EnteringNew
                            }
                            is ResolverError.PinLocked -> {
                                errorMessage = R.string.screen_two_step_verification_locked
                                phase = if (userHasPin) TwoStepVerificationPhase.OverviewHasPin else TwoStepVerificationPhase.OverviewNoPin
                            }
                            is ResolverError.PinChangeCooldown -> {
                                errorMessage = R.string.screen_two_step_verification_cooldown
                                phase = TwoStepVerificationPhase.OverviewHasPin
                            }
                            else -> {
                                errorMessage = CommonStrings.error_unknown
                                code = ""
                                phase = if (userHasPin) TwoStepVerificationPhase.EnteringCurrent else TwoStepVerificationPhase.EnteringNew
                            }
                        }
                    }
            }
        }

        fun handleSubmittedCode(submitted: String) {
            when (phase) {
                TwoStepVerificationPhase.EnteringCurrent -> verifyCurrentPinAndRequestOtp(submitted)
                TwoStepVerificationPhase.EnteringOtp -> {
                    otpCode = submitted
                    code = ""
                    phase = TwoStepVerificationPhase.EnteringNew
                }
                TwoStepVerificationPhase.EnteringNew -> {
                    if (isWeakPin(submitted)) {
                        errorMessage = R.string.screen_two_step_verification_weak_error
                        code = ""
                        return
                    }
                    if (userHasPin && currentPin.isNotEmpty() && submitted == currentPin) {
                        errorMessage = R.string.screen_two_step_verification_same_as_current
                        code = ""
                        return
                    }
                    stagedNewPin = submitted
                    code = ""
                    phase = TwoStepVerificationPhase.ConfirmingNew
                }
                TwoStepVerificationPhase.ConfirmingNew -> {
                    if (submitted != stagedNewPin) {
                        errorMessage = R.string.screen_two_step_verification_mismatch_error
                        code = ""
                        stagedNewPin = ""
                        phase = TwoStepVerificationPhase.EnteringNew
                        return
                    }
                    submitNewPin(submitted)
                }
                else -> Unit
            }
        }

        fun handleEvent(event: TwoStepVerificationEvent) {
            when (event) {
                TwoStepVerificationEvent.StartSetup -> {
                    resetFlowState()
                    phase = TwoStepVerificationPhase.EnteringNew
                }
                TwoStepVerificationEvent.StartChange -> {
                    resetFlowState()
                    phase = TwoStepVerificationPhase.EnteringPhone
                }
                is TwoStepVerificationEvent.CodeChanged -> {
                    val cleaned = event.code.filter { it.isDigit() }.take(TwoStepVerificationState.CODE_LENGTH)
                    code = cleaned
                    if (errorMessage != null) errorMessage = null
                    if (cleaned.length == TwoStepVerificationState.CODE_LENGTH) {
                        handleSubmittedCode(cleaned)
                    }
                }
                is TwoStepVerificationEvent.PhoneChanged -> {
                    phone = event.phone
                    if (errorMessage != null) errorMessage = null
                }
                TwoStepVerificationEvent.Continue -> {
                    if (phase == TwoStepVerificationPhase.EnteringPhone) {
                        val trimmed = phone.trim()
                        if (!TwoStepVerificationState.isValidPhone(trimmed)) {
                            errorMessage = R.string.screen_two_step_verification_phone_invalid
                            return
                        }
                        phone = trimmed
                        code = ""
                        phase = TwoStepVerificationPhase.EnteringCurrent
                    } else if (code.length == TwoStepVerificationState.CODE_LENGTH) {
                        handleSubmittedCode(code)
                    }
                }
                TwoStepVerificationEvent.CancelEntry -> {
                    resetFlowState()
                    phase = if (userHasPin) TwoStepVerificationPhase.OverviewHasPin else TwoStepVerificationPhase.OverviewNoPin
                }
                TwoStepVerificationEvent.ClearSuccess -> {
                    showSuccess = false
                }
            }
        }

        return TwoStepVerificationState(
            phase = phase,
            code = code,
            phone = phone,
            errorMessage = errorMessage,
            showSuccess = showSuccess,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken

    private fun isWeakPin(pin: String): Boolean = pin in WEAK_PINS

    private companion object {
        val WEAK_PINS = setOf(
            "000000", "111111", "222222", "333333", "444444",
            "555555", "666666", "777777", "888888", "999999",
            "123456", "654321", "012345", "543210",
        )
    }
}
