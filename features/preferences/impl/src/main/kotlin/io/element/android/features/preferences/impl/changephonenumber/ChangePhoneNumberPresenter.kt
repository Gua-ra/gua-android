/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import io.element.android.libraries.phonenumberentry.DeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * GUA FORK: presenter for the change-phone-number screen. Drives the real backend PIN-first
 * change-number flow: the user confirms their account PIN up front (yielding a short-lived reauth
 * token, no SMS), then enters their new number (which triggers an OTP to that number), then enters
 * that OTP to complete the change. The reauth token gates both the OTP request and the completion,
 * so the SMS never fires until a valid PIN step-up exists. Translates typed [ResolverError]s into
 * per-error phase transitions, mirroring the iOS change-number view model.
 */
@AssistedInject
class ChangePhoneNumberPresenter(
    @Assisted private val navigateToCountryPicker: () -> Unit,
    @Assisted private val navigateToPinSetup: () -> Unit,
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
    private val selectedCountryStore: SelectedCountryStore,
    private val deviceCountryProvider: DeviceCountryProvider,
) : Presenter<ChangePhoneNumberState> {
    @AssistedFactory
    interface Factory {
        fun create(
            navigateToCountryPicker: () -> Unit,
            navigateToPinSetup: () -> Unit,
        ): ChangePhoneNumberPresenter
    }

    @Composable
    override fun present(): ChangePhoneNumberState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(ChangePhoneNumberPhase.Intro) }
        var code by remember { mutableStateOf("") }
        // The NEW number, held as (country, RAW national digits) like the welcome PhoneEntry screen.
        // The national mask is applied purely visually by PhoneNumberEntryField.
        var selectedCountry by remember { mutableStateOf(deviceCountryProvider.current()) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        // Remaining fresh-2FA cooldown surfaced on the Cooldown interstitial (0 otherwise).
        var cooldownRemainingSeconds by remember { mutableLongStateOf(0L) }

        // Apply any country picked in the shared CountryPicker child screen, then clear it.
        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        // Flow scratch state.
        var newPhone by remember { mutableStateOf("") }
        // Short-lived PIN step-up token from /security/pin/reauth. Gates the OTP request + completion;
        // the SMS never fires until this is set. We deliberately do NOT keep the PIN around.
        var reauthToken by remember { mutableStateOf("") }

        fun resetFlowState() {
            errorMessage = null
            code = ""
            localPhoneNumber = ""
            newPhone = ""
            reauthToken = ""
            cooldownRemainingSeconds = 0L
        }

        // GUA FORK: WhatsApp belt-and-suspenders gate. On Intro Continue we fetch the PIN status FIRST
        // and branch BEFORE touching the PIN/number steps: no PIN -> NeedsPinSetup interstitial; an
        // active fresh-2FA cooldown -> Cooldown interstitial; otherwise proceed to the PIN step-up.
        fun checkPinStatusAndProceed() {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.pinStatus(
                    accessToken = accessToken,
                    userId = matrixClient.sessionId.value,
                )
                    .onSuccess { status ->
                        errorMessage = null
                        when {
                            !status.hasPin -> {
                                phase = ChangePhoneNumberPhase.NeedsPinSetup
                            }
                            status.changePhoneCooldownRemainingSeconds > 0 -> {
                                cooldownRemainingSeconds = status.changePhoneCooldownRemainingSeconds
                                phase = ChangePhoneNumberPhase.Cooldown
                            }
                            else -> {
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                        }
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.PinSetupRequired -> {
                                errorMessage = null
                                phase = ChangePhoneNumberPhase.NeedsPinSetup
                            }
                            is ResolverError.TwoFactorCooldown -> {
                                errorMessage = null
                                cooldownRemainingSeconds = error.retryAfterSeconds ?: 0L
                                phase = ChangePhoneNumberPhase.Cooldown
                            }
                            else -> {
                                errorMessage = CommonStrings.error_unknown
                                phase = ChangePhoneNumberPhase.Intro
                            }
                        }
                    }
            }
        }

        fun verifyPin(enteredPin: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.verifyPinReauth(
                    accessToken = accessToken,
                    userId = matrixClient.sessionId.value,
                    pin = enteredPin,
                )
                    .onSuccess { token ->
                        reauthToken = token
                        code = ""
                        errorMessage = null
                        // No SMS yet — the user picks the new number first.
                        phase = ChangePhoneNumberPhase.EnteringNewPhone
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.PinLocked -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            else -> {
                                // invalid_pin and anything else: stay on the PIN step, no SMS.
                                errorMessage = R.string.screen_change_phone_pin_incorrect
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                        }
                    }
            }
        }

        fun requestOtp(enteredPhone: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.requestPhoneChangeOtp(
                    accessToken = accessToken,
                    userId = matrixClient.sessionId.value,
                    newPhone = enteredPhone,
                    reauthToken = reauthToken,
                    language = Locale.getDefault().toLanguageTag(),
                )
                    .onSuccess {
                        // SMS fired here.
                        code = ""
                        errorMessage = null
                        phase = ChangePhoneNumberPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidReauthToken -> {
                                // The step-up expired: restart from the PIN.
                                errorMessage = R.string.screen_change_phone_pin_incorrect
                                reauthToken = ""
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            is ResolverError.PhoneAlreadyLinked -> {
                                errorMessage = R.string.screen_change_phone_already_linked
                                phase = ChangePhoneNumberPhase.EnteringNewPhone
                            }
                            is ResolverError.TwoFactorCooldown -> {
                                // Defense-in-depth: the cooldown started after the client pre-check.
                                cooldownRemainingSeconds = error.retryAfterSeconds ?: 0L
                                errorMessage = null
                                phase = ChangePhoneNumberPhase.Cooldown
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                phase = ChangePhoneNumberPhase.EnteringNewPhone
                            }
                            else -> {
                                // 400 / invalid-phone and anything else: stay on the number step.
                                errorMessage = R.string.screen_change_phone_new_invalid
                                phase = ChangePhoneNumberPhase.EnteringNewPhone
                            }
                        }
                    }
            }
        }

        fun submitOtp(enteredOtp: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.changePhoneNumber(
                    accessToken = accessToken,
                    userId = matrixClient.sessionId.value,
                    newPhone = newPhone,
                    code = enteredOtp,
                    reauthToken = reauthToken,
                )
                    .onSuccess {
                        errorMessage = null
                        code = ""
                        phase = ChangePhoneNumberPhase.Done
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidOtp -> {
                                errorMessage = R.string.screen_change_phone_otp_invalid
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                            is ResolverError.InvalidReauthToken -> {
                                // The step-up expired: restart from the PIN.
                                errorMessage = R.string.screen_change_phone_pin_incorrect
                                reauthToken = ""
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            is ResolverError.PhoneAlreadyLinked -> {
                                errorMessage = R.string.screen_change_phone_already_linked
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringNewPhone
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                            else -> {
                                errorMessage = CommonStrings.error_unknown
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                        }
                    }
            }
        }

        fun handleSubmittedCode(submitted: String) {
            when (phase) {
                ChangePhoneNumberPhase.EnteringPin -> verifyPin(submitted)
                ChangePhoneNumberPhase.EnteringOtp -> submitOtp(submitted)
                else -> Unit
            }
        }

        fun handleEvent(event: ChangePhoneNumberEvents) {
            when (event) {
                is ChangePhoneNumberEvents.CodeChanged -> {
                    val cleaned = event.code.filter { it.isDigit() }.take(ChangePhoneNumberState.CODE_LENGTH)
                    code = cleaned
                    if (errorMessage != null) errorMessage = null
                    if (cleaned.length == ChangePhoneNumberState.CODE_LENGTH) {
                        handleSubmittedCode(cleaned)
                    }
                }
                is ChangePhoneNumberEvents.PhoneChanged -> {
                    // Mirror the welcome PhoneEntry pipeline: normalise (strip a redundant country
                    // code from a paste/autofill and switch country if unambiguously international),
                    // then auto-detect the country. Only raw digits are stored; the national mask is
                    // visual-only. No-op for ordinary local typing.
                    val (normalizedCountry, normalizedDigits) = Country.normalize(
                        rawInput = event.value,
                        current = selectedCountry,
                    )
                    val country = Country.detect(localDigits = normalizedDigits, current = normalizedCountry) ?: normalizedCountry
                    selectedCountry = country
                    localPhoneNumber = normalizedDigits
                    if (errorMessage != null) errorMessage = null
                }
                is ChangePhoneNumberEvents.CountrySelected -> {
                    selectedCountry = event.country
                    if (errorMessage != null) errorMessage = null
                }
                ChangePhoneNumberEvents.SelectCountry -> navigateToCountryPicker()
                ChangePhoneNumberEvents.SetUpPin -> navigateToPinSetup()
                ChangePhoneNumberEvents.Continue -> {
                    when (phase) {
                        ChangePhoneNumberPhase.Intro -> {
                            // Gate FIRST on PIN status; do not enter the PIN/number steps here.
                            errorMessage = null
                            code = ""
                            checkPinStatusAndProceed()
                        }
                        ChangePhoneNumberPhase.EnteringNewPhone -> {
                            val digits = localPhoneNumber.filter { it.isDigit() }
                            if (!ChangePhoneNumberState.isValidNumber(localDigits = digits, dialCode = selectedCountry.dialCode)) {
                                errorMessage = R.string.screen_change_phone_new_invalid
                                return
                            }
                            val e164 = "+" + selectedCountry.dialCode + digits
                            newPhone = e164
                            errorMessage = null
                            requestOtp(e164)
                        }
                        ChangePhoneNumberPhase.EnteringPin,
                        ChangePhoneNumberPhase.EnteringOtp -> {
                            if (code.length == ChangePhoneNumberState.CODE_LENGTH) {
                                handleSubmittedCode(code)
                            }
                        }
                        else -> Unit
                    }
                }
                ChangePhoneNumberEvents.CancelEntry -> {
                    resetFlowState()
                    phase = ChangePhoneNumberPhase.Intro
                }
                ChangePhoneNumberEvents.Done -> Unit
            }
        }

        return ChangePhoneNumberState(
            phase = phase,
            code = code,
            selectedCountry = selectedCountry,
            localPhoneNumber = localPhoneNumber,
            errorMessage = errorMessage,
            cooldownRemainingSeconds = cooldownRemainingSeconds,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken
}
