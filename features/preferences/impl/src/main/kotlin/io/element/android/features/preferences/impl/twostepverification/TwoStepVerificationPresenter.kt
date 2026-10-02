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
import io.element.android.libraries.guaresolver.identityServiceMessage
import io.element.android.libraries.guaresolver.withFreshAccessToken
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.DeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/**
 * GUA FORK: presenter for the two-step-verification (account PIN) screen. Mirrors the iOS
 * `TwoStepVerificationScreenViewModel` state machine: it loads the factor status, then drives the
 * PIN-first change flow, translating typed [ResolverError]s into per-error phase transitions.
 *
 * Setting the FIRST PIN is not one of those flows any more. It leaves the app for the same
 * authenticated web ceremony a passkey uses ([IdentityServiceClient.startPinEnrollment]), because a
 * bearer session alone must never be able to add a durable factor: the ceremony asks for a step-up
 * first, and a passkey assertion only works in the browser. The native path it replaces called an
 * endpoint that now refuses every caller. Because the factor is registered outside the app, the
 * status is read again every time the screen resumes rather than once when it opens.
 *
 * The change flow is PIN-FIRST so identity is proven before any SMS goes out: the user enters their
 * current PIN, then confirms their on-file number (which is what actually fires the OTP via
 * [IdentityServiceClient.startPinChange]), then enters that OTP and chooses a new PIN. The captured
 * current PIN gates the SMS: `startPinChange` only runs once a PIN has been supplied, and a wrong
 * PIN routes the user back to the PIN step with no further SMS.
 */
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
        // The on-file number being confirmed, held as (country, RAW national digits) like the welcome
        // PhoneEntry screen and the change-phone screen. The national mask is visual-only.
        var selectedCountry by remember { mutableStateOf(deviceCountryProvider.current()) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var showSuccess by remember { mutableStateOf(false) }
        // The authenticated enrollment URL to hand to the View for the web ceremony, passkey or PIN.
        var factorEnrollUrl by remember { mutableStateOf<String?>(null) }

        // Apply any country picked in the shared CountryPicker child screen, then clear it.
        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        // Flow scratch state, mirroring the iOS view-state fields.
        // The server's factor signal, or null when it could not be read. Null is UNKNOWN, never
        // "no factors": defaulting a failed read to false is what told a passkey holder their
        // account had no two-step verification and pushed them to create a PIN.
        var factors by remember { mutableStateOf<AccountFactorStatus?>(null) }
        var currentPin by remember { mutableStateOf("") }
        var stagedNewPin by remember { mutableStateOf("") }
        var challengeId by remember { mutableStateOf<String?>(null) }
        var otpCode by remember { mutableStateOf("") }

        // Whether the PIN flows act as "set up" or "change". Nullable on purpose: null is UNKNOWN,
        // and neither flow may run on it. Collapsing unknown to false picked "set up", which for an
        // account that already holds a PIN is a call the server refuses.
        val userHasPin: Boolean? = factors?.hasPin

        // Read on every resume, not once. Both factors are now registered in a Custom Tab, so the
        // account gains one while this screen sits in the background: a screen that only read at
        // creation went on telling someone who had just set a PIN up that they had none. The
        // account-recovery banner reads on resume the same way.
        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose { isResumed = false }
        }
        // Keyed on the value this composition saw, not on a read inside the effect: the resume
        // callback can flip the state before the effect starts.
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed) return@LaunchedEffect
            // Only an idle screen refreshes. A read landing mid-flow would drop the user out of the
            // step they are on, and only the first read has nothing to show while it waits.
            val isFirstRead = phase == TwoStepVerificationPhase.Loading
            if (!isFirstRead && phase != TwoStepVerificationPhase.Overview) return@LaunchedEffect
            identityServiceCall { accessToken ->
                identityServiceClient.accountFactorStatus(accessToken, matrixClient.sessionId.value)
            }
                .onSuccess { status ->
                    factors = status
                    errorMessage = null
                    phase = TwoStepVerificationPhase.Overview
                }
                .onFailure { error ->
                    // A refresh that fails keeps the status the screen already holds: it is still
                    // the last thing the server said, and only the first read has no fallback.
                    if (isFirstRead) {
                        factors = null
                        errorMessage = error.identityServiceMessage()
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

        // GUA FORK: PIN-first gate. Called only once the user has confirmed their number AFTER entering
        // their current PIN. `startPinChange` verifies the PIN and fires the OTP in one call, so the SMS
        // never goes out until a PIN has been supplied; a wrong PIN routes back to the PIN step.
        fun confirmNumberAndRequestOtp(e164Phone: String) {
            coroutineScope.launch {
                if (currentPin.isEmpty()) {
                    // Should never happen: PIN is captured before this step. Belt-and-suspenders.
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.EnteringCurrent
                    return@launch
                }
                val previousPhase = phase
                phase = TwoStepVerificationPhase.Submitting
                identityServiceCall { accessToken ->
                    identityServiceClient.startPinChange(accessToken = accessToken, phone = e164Phone, currentPin = currentPin)
                }
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
                            // The number was never weighed, so calling it invalid would send the user
                            // to correct something that was right.
                            is ResolverError.SessionRefreshNeeded,
                            is ResolverError.NoSession -> {
                                errorMessage = error.identityServiceMessage()
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
                val activeChallengeId = challengeId
                if (userHasPin != true || activeChallengeId == null) {
                    // Belt-and-suspenders: only the change flow reaches this, and it starts from a
                    // known PIN and an OTP challenge. Setting a first PIN never gets here at all now
                    // that it is enrolled in the web ceremony.
                    errorMessage = CommonStrings.error_unknown
                    phase = TwoStepVerificationPhase.Overview
                    return@launch
                }
                phase = TwoStepVerificationPhase.Submitting
                identityServiceCall { accessToken ->
                    identityServiceClient.completePinChange(
                        accessToken = accessToken,
                        challengeId = activeChallengeId,
                        otpCode = otpCode,
                        newPin = newPin,
                    )
                }
                    .onSuccess {
                        // The account still holds a PIN; keep whatever else the server said it holds
                        // rather than dropping back to an unknown status.
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
                                errorMessage = error.identityServiceMessage()
                                code = ""
                                phase = TwoStepVerificationPhase.EnteringCurrent
                            }
                        }
                    }
            }
        }

        // GUA FORK: factor enrollment, passkey or first PIN. Mirrors iOS' coordinator action: fetch
        // the authenticated web-ceremony URL from the identity-service and hand it to the View to
        // open in a Chrome Custom Tab (the Android counterpart of iOS' ASWebAuthenticationSession).
        // The URL is self-authenticating, so the user settles the step-up and registers the factor
        // in-browser at the IdP.
        fun startFactorEnrollment(start: suspend (String) -> Result<String>) {
            coroutineScope.launch {
                identityServiceCall { accessToken -> start(accessToken) }
                    .onSuccess { enrollUrl ->
                        errorMessage = null
                        factorEnrollUrl = enrollUrl
                    }
                    .onFailure { error ->
                        when (error) {
                            // Our view of the account was stale rather than the user being wrong.
                            // Correct the row so it offers the change they actually want.
                            is ResolverError.PinAlreadySet -> {
                                factors = factors?.copy(hasPin = true)
                                errorMessage = R.string.screen_two_step_verification_pin_already_set
                            }
                            // The same staleness on the passkey row. There is no "change passkey"
                            // to offer, so correcting the row is what withdraws the dead button.
                            is ResolverError.PasskeyAlreadyRegistered -> {
                                factors = factors?.copy(passkeyRegistered = true)
                                errorMessage = R.string.screen_two_step_verification_passkey_already_registered
                            }
                            // The account holds only a passkey and no passkey ceremony can run on
                            // this deployment, so no factor can be enrolled here at all. Say that,
                            // and point at the delayed recovery, which is the only way out.
                            is ResolverError.StepUpUnavailable ->
                                errorMessage = R.string.screen_two_step_verification_step_up_unavailable
                            else -> errorMessage = error.identityServiceMessage()
                        }
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
                    // Only on a KNOWN "no PIN". The row that emits this is withheld otherwise, and
                    // an unknown status must not be guessed into an enrollment the server refuses.
                    if (userHasPin == false) {
                        startFactorEnrollment(identityServiceClient::startPinEnrollment)
                    }
                }
                TwoStepVerificationEvent.StartChange -> {
                    // PIN-FIRST: verify the current PIN BEFORE confirming the number / firing the SMS.
                    // Only on a KNOWN "has PIN": there is nothing to verify otherwise.
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
                    // Mirror the welcome/change-phone pipeline: normalise (strip a redundant country
                    // code from a paste/autofill and switch country if unambiguously international),
                    // then auto-detect the country. Only raw digits are stored; the mask is visual-only.
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
                // Only on a KNOWN "no passkey", matching the row: enrollment excludes credentials the
                // account already holds, so an unknown status would send the user to a refusal.
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

    /**
     * Every identity-service call on this screen goes through the shared accessor, so a token that
     * expired while the screen sat in the background costs a retry nobody sees rather than the
     * generic error.
     */
    private suspend fun <T> identityServiceCall(call: suspend (String) -> Result<T>): Result<T> =
        matrixClient.withFreshAccessToken(sessionStore, call)

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
