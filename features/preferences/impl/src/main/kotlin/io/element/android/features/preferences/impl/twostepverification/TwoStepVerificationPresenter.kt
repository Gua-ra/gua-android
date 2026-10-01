/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.DeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/** The change flow verifies the current PIN before any SMS is sent. */
@AssistedInject
class TwoStepVerificationPresenter(
    @Assisted private val navigateToCountryPicker: () -> Unit,
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
    private val selectedCountryStore: SelectedCountryStore,
    private val deviceCountryProvider: DeviceCountryProvider,
) : Presenter<TwoStepVerificationState> {
    @AssistedFactory
    interface Factory {
        fun create(
            navigateToCountryPicker: () -> Unit,
        ): TwoStepVerificationPresenter
    }

    @Composable
    override fun present(): TwoStepVerificationState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(TwoStepVerificationPhase.Loading) }
        var code by remember { mutableStateOf("") }
        var selectedCountry by remember { mutableStateOf(deviceCountryProvider.current()) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var showSuccess by remember { mutableStateOf(false) }
        var factorEnrollUrl by remember { mutableStateOf<String?>(null) }

        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        var factors by remember { mutableStateOf<AccountFactorStatus?>(null) }
        var currentPin by remember { mutableStateOf("") }
        var stagedNewPin by remember { mutableStateOf("") }
        var challengeId by remember { mutableStateOf<String?>(null) }
        var otpCode by remember { mutableStateOf("") }

        val userHasPin: Boolean? = factors?.hasPin

        // Read on every resume: factors are registered in a Custom Tab while this screen is in the background.
        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose { isResumed = false }
        }
        // Keyed on the value this composition saw: the resume callback can flip the state before the effect starts.
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed) return@LaunchedEffect
            // Only an idle screen refreshes: a read landing mid-flow would drop the user out of their step.
            val isFirstRead = phase == TwoStepVerificationPhase.Loading
            if (!isFirstRead && phase != TwoStepVerificationPhase.Overview) return@LaunchedEffect
            val accessToken = accessToken()
            if (accessToken == null) {
                if (isFirstRead) {
                    factors = null
                    errorMessage = CommonStrings.error_unknown
                }
                phase = TwoStepVerificationPhase.Overview
                return@LaunchedEffect
            }
            identityServiceClient.accountFactorStatus(accessToken, matrixClient.sessionId.value)
                .onSuccess { status ->
                    factors = status
                    errorMessage = null
                    phase = TwoStepVerificationPhase.Overview
                }
                .onFailure {
                    // A failed refresh keeps the status the screen already holds.
                    if (isFirstRead) {
                        factors = null
                        errorMessage = CommonStrings.error_unknown
                    }
                    phase = TwoStepVerificationPhase.Overview
                }
        }

        fun resetFlowState() {
            errorMessage = null
            currentPin = ""
            stagedNewPin = ""
            challengeId = null
            otpCode = ""
            selectedCountry = deviceCountryProvider.current()
            localPhoneNumber = ""
            code = ""
        }

        fun confirmNumberAndRequestOtp(e164Phone: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                if (currentPin.isEmpty()) {
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.EnteringCurrent
                    return@launch
                }
                val previousPhase = phase
                phase = TwoStepVerificationPhase.Submitting
                identityServiceClient.startPinChange(accessToken = accessToken, phone = e164Phone, currentPin = currentPin)
                    .onSuccess { newChallengeId ->
                        challengeId = newChallengeId
                        code = ""
                        errorMessage = null
                        phase = TwoStepVerificationPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidPin -> {
                                errorMessage = R.string.screen_two_step_verification_current_incorrect
                                currentPin = ""
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringCurrent
                            }
                            is ResolverError.PinLocked -> {
                                errorMessage = R.string.screen_two_step_verification_locked
                                phase = TwoStepVerificationPhase.Overview
                            }
                            is ResolverError.PinChangeCooldown -> {
                                errorMessage = R.string.screen_two_step_verification_cooldown
                                phase = TwoStepVerificationPhase.Overview
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                phase = previousPhase
                            }
                            else -> {
                                errorMessage = R.string.screen_two_step_verification_phone_invalid
                                phase = TwoStepVerificationPhase.EnteringPhone
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
                val activeChallengeId = challengeId
                if (userHasPin != true || activeChallengeId == null) {
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.Overview
                    return@launch
                }
                phase = TwoStepVerificationPhase.Submitting
                identityServiceClient.completePinChange(
                    accessToken = accessToken,
                    challengeId = activeChallengeId,
                    otpCode = otpCode,
                    newPin = newPin,
                )
                    .onSuccess {
                        factors = factors?.copy(hasPin = true)
                        resetFlowState()
                        phase = TwoStepVerificationPhase.Overview
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
                                phase = TwoStepVerificationPhase.Overview
                            }
                            is ResolverError.InvalidPin -> {
                                errorMessage = R.string.screen_two_step_verification_current_incorrect
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringCurrent
                            }
                            is ResolverError.PinLocked -> {
                                errorMessage = R.string.screen_two_step_verification_locked
                                phase = TwoStepVerificationPhase.Overview
                            }
                            is ResolverError.PinChangeCooldown -> {
                                errorMessage = R.string.screen_two_step_verification_cooldown
                                phase = TwoStepVerificationPhase.Overview
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

        fun startFactorEnrollment(start: suspend (String) -> Result<String>) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                start(accessToken)
                    .onSuccess { enrollUrl ->
                        errorMessage = null
                        factorEnrollUrl = enrollUrl
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.PinAlreadySet -> {
                                factors = factors?.copy(hasPin = true)
                                errorMessage = R.string.screen_two_step_verification_pin_already_set
                            }
                            is ResolverError.PasskeyAlreadyRegistered -> {
                                factors = factors?.copy(passkeyRegistered = true)
                                errorMessage = R.string.screen_two_step_verification_passkey_already_registered
                            }
                            is ResolverError.StepUpUnavailable ->
                                errorMessage = R.string.screen_two_step_verification_step_up_unavailable
                            else -> errorMessage = CommonStrings.error_unknown
                        }
                    }
            }
        }

        fun handleSubmittedCode(submitted: String) {
            when (phase) {
                TwoStepVerificationPhase.EnteringCurrent -> {
                    currentPin = submitted
                    code = ""
                    errorMessage = null
                    phase = TwoStepVerificationPhase.EnteringPhone
                }
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
                    if (userHasPin == true && currentPin.isNotEmpty() && submitted == currentPin) {
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
                    if (userHasPin == false) {
                        startFactorEnrollment(identityServiceClient::startPinEnrollment)
                    }
                }
                TwoStepVerificationEvent.StartChange -> {
                    if (userHasPin == true) {
                        resetFlowState()
                        phase = TwoStepVerificationPhase.EnteringCurrent
                    }
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
                    val (normalizedCountry, normalizedDigits) = Country.normalize(
                        rawInput = event.value,
                        current = selectedCountry,
                    )
                    val country = Country.detect(localDigits = normalizedDigits, current = normalizedCountry) ?: normalizedCountry
                    selectedCountry = country
                    localPhoneNumber = normalizedDigits
                    if (errorMessage != null) errorMessage = null
                }
                TwoStepVerificationEvent.SelectCountry -> navigateToCountryPicker()
                TwoStepVerificationEvent.Continue -> {
                    when (phase) {
                        TwoStepVerificationPhase.EnteringPhone -> {
                            val digits = localPhoneNumber.filter { it.isDigit() }
                            if (!TwoStepVerificationState.isValidNumber(localDigits = digits, dialCode = selectedCountry.dialCode)) {
                                errorMessage = R.string.screen_two_step_verification_phone_invalid
                                return
                            }
                            val e164 = "+" + selectedCountry.dialCode + digits
                            errorMessage = null
                            confirmNumberAndRequestOtp(e164)
                        }
                        TwoStepVerificationPhase.EnteringCurrent,
                        TwoStepVerificationPhase.EnteringOtp,
                        TwoStepVerificationPhase.EnteringNew,
                        TwoStepVerificationPhase.ConfirmingNew -> {
                            if (code.length == TwoStepVerificationState.CODE_LENGTH) {
                                handleSubmittedCode(code)
                            }
                        }
                        else -> Unit
                    }
                }
                TwoStepVerificationEvent.CancelEntry -> {
                    resetFlowState()
                    phase = TwoStepVerificationPhase.Overview
                }
                TwoStepVerificationEvent.ClearSuccess -> {
                    showSuccess = false
                }
                TwoStepVerificationEvent.SetUpPasskey -> if (factors?.passkeyRegistered == false) {
                    startFactorEnrollment(identityServiceClient::startPasskeyEnrollment)
                }
                TwoStepVerificationEvent.ClearFactorEnrollUrl -> {
                    factorEnrollUrl = null
                }
            }
        }

        return TwoStepVerificationState(
            phase = phase,
            factors = factors,
            code = code,
            selectedCountry = selectedCountry,
            localPhoneNumber = localPhoneNumber,
            errorMessage = errorMessage,
            showSuccess = showSuccess,
            factorEnrollUrl = factorEnrollUrl,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken

    private fun isWeakPin(pin: String): Boolean = pin in WEAK_PINS

    private companion object {
        val WEAK_PINS = setOf(
            "000000",
            "111111",
            "222222",
            "333333",
            "444444",
            "555555",
            "666666",
            "777777",
            "888888",
            "999999",
            "123456",
            "654321",
            "012345",
            "543210",
        )
    }
}
