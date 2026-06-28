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
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the two-step-verification (account PIN) screen. Mirrors the iOS
 * `TwoStepVerificationScreenViewModel` state machine: it loads the PIN status, then drives the
 * setup flow ([TwoStepVerificationPhase.EnteringNew] -> confirm -> submit) and the PIN-first change
 * flow, translating typed [ResolverError]s into per-error phase transitions.
 *
 * The change flow is PIN-FIRST so identity is proven before any SMS goes out: the user enters their
 * current PIN, then confirms their on-file number (which is what actually fires the OTP via
 * [IdentityServiceClient.startPinChange]), then enters that OTP and chooses a new PIN. The captured
 * current PIN gates the SMS — `startPinChange` only runs once a PIN has been supplied, and a wrong
 * PIN routes the user back to the PIN step with no further SMS.
 */
@AssistedInject
class TwoStepVerificationPresenter(
    @Assisted private val navigateToCountryPicker: () -> Unit,
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
    private val selectedCountryStore: SelectedCountryStore,
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
        // The on-file number being confirmed, held as (country, national digits) like the welcome
        // PhoneEntry screen and the change-phone screen.
        var selectedCountry by remember { mutableStateOf(Country.deviceDefault) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var showSuccess by remember { mutableStateOf(false) }
        // The authenticated passkey-enrollment URL to hand to the View for the web ceremony.
        var passkeyEnrollUrl by remember { mutableStateOf<String?>(null) }

        // Apply any country picked in the shared CountryPicker child screen, then clear it.
        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                localPhoneNumber = country.formatNational(localPhoneNumber.filter { it.isDigit() })
                selectedCountryStore.consume()
            }
        }

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
            selectedCountry = Country.deviceDefault
            localPhoneNumber = ""
            code = ""
        }

        // GUA FORK: PIN-first gate. Called only once the user has confirmed their number AFTER entering
        // their current PIN. `startPinChange` verifies the PIN and fires the OTP in one call, so the SMS
        // never goes out until a PIN has been supplied; a wrong PIN routes back to the PIN step.
        fun confirmNumberAndRequestOtp(e164Phone: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                if (currentPin.isEmpty()) {
                    // Should never happen: PIN is captured before this step. Belt-and-suspenders.
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
                                // The captured PIN was wrong: clear it and go back to the PIN step. No SMS.
                                errorMessage = R.string.screen_two_step_verification_current_incorrect
                                currentPin = ""
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

        // GUA FORK: passkey enrollment. Mirrors iOS' coordinator action: fetch the authenticated
        // web-ceremony URL from the identity-service and hand it to the View to open in a Chrome
        // Custom Tab (the Android counterpart of iOS' ASWebAuthenticationSession). The URL is
        // self-authenticating, so the user completes WebAuthn registration in-browser at the IdP.
        fun startPasskeyEnrollment() {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                identityServiceClient.startPasskeyEnrollment(accessToken)
                    .onSuccess { enrollUrl ->
                        errorMessage = null
                        passkeyEnrollUrl = enrollUrl
                    }
                    .onFailure {
                        errorMessage = CommonStrings.error_unknown
                    }
            }
        }

        fun handleSubmittedCode(submitted: String) {
            when (phase) {
                TwoStepVerificationPhase.EnteringCurrent -> {
                    // PIN-first: capture the PIN and advance to confirm the number. No SMS yet.
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
                    // PIN-FIRST: verify the current PIN BEFORE confirming the number / firing the SMS.
                    resetFlowState()
                    phase = TwoStepVerificationPhase.EnteringCurrent
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
                    // Mirror the welcome/change-phone pipeline: normalise (strip a redundant country
                    // code from a paste/autofill and switch country if unambiguously international),
                    // then auto-detect the country and reformat with its national mask.
                    val (normalizedCountry, normalizedDigits) = Country.normalize(
                        rawInput = event.value,
                        current = selectedCountry,
                    )
                    val country = Country.detect(localDigits = normalizedDigits, current = normalizedCountry) ?: normalizedCountry
                    selectedCountry = country
                    localPhoneNumber = country.formatNational(normalizedDigits)
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
                    phase = if (userHasPin) TwoStepVerificationPhase.OverviewHasPin else TwoStepVerificationPhase.OverviewNoPin
                }
                TwoStepVerificationEvent.ClearSuccess -> {
                    showSuccess = false
                }
                TwoStepVerificationEvent.SetUpPasskey -> startPasskeyEnrollment()
                TwoStepVerificationEvent.ClearPasskeyEnrollUrl -> {
                    passkeyEnrollUrl = null
                }
            }
        }

        return TwoStepVerificationState(
            phase = phase,
            code = code,
            selectedCountry = selectedCountry,
            localPhoneNumber = localPhoneNumber,
            errorMessage = errorMessage,
            showSuccess = showSuccess,
            passkeyEnrollUrl = passkeyEnrollUrl,
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
