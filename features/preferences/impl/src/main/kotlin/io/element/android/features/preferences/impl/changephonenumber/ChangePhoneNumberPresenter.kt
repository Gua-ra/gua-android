/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import io.element.android.libraries.guaresolver.AuthFactor
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
 * Drives the identity-service phone-change contract: `POST /account/reauth/start` and
 * `/account/reauth/verify` for the single-use, phone-change-scoped token, then
 * `POST /account/phone/change/start` and `/complete`.
 *
 * Security shape, in the order it runs:
 *  1. The account's factors are read first. An account that holds no factor a phone change accepts
 *     is blocked outright, and one still inside a cooldown is held.
 *  2. The user says which number is on the account. The server texts it only on a match and refuses
 *     a miss with one wording for "unknown", "someone else's" and "not this one".
 *  3. The OTP that number received buys a token. That proof alone is not enough to re-point the
 *     number, since a SIM-swapper holds that number too.
 *  4. The step-up factor is spent together with the new number. Only that call texts the new number.
 *
 * The token is single-use and the server spends it before it weighs the step-up, so any failure in
 * step 4 restarts the flow, and a `step_up_required` refusal ends the operation.
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
        // Whichever number is being typed, current or new, held as (country, raw national digits). The
        // national mask is applied visually by PhoneNumberEntryField.
        var selectedCountry by remember { mutableStateOf(deviceCountryProvider.current()) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        var cooldownRemainingSeconds by remember { mutableLongStateOf(0L) }
        var stepUpBlock by remember { mutableStateOf<StepUpBlock?>(null) }
        var passkeyEnrollUrl by remember { mutableStateOf<String?>(null) }

        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        // The number the user says is on the account, in E.164. Both reauth calls carry it.
        var currentPhone by remember { mutableStateOf("") }
        // Single-use token from /account/reauth/verify. The server spends it on the first /start attempt
        // whatever the outcome, so it is cleared every time and a retry always mints a fresh one.
        var reauthToken by remember { mutableStateOf("") }
        // The step-up factor, held only between the PIN step and the /start call that spends it.
        var stepUpPin by remember { mutableStateOf("") }
        // Proof that the step-up was accepted and the new-number OTP went out.
        var challengeId by remember { mutableStateOf("") }

        fun resetFlowState() {
            errorMessage = null
            code = ""
            localPhoneNumber = ""
            currentPhone = ""
            reauthToken = ""
            stepUpPin = ""
            challengeId = ""
            cooldownRemainingSeconds = 0L
            stepUpBlock = null
        }

        /**
         * The token is gone and the flow cannot continue: drops every credential and returns to the start,
         * where the factor and cooldown pre-checks run again. Never re-sends automatically: a spent token
         * must cost a deliberate restart, not a silent SMS.
         */
        fun abortSpentReauth(@StringRes errorRes: Int) {
            currentPhone = ""
            reauthToken = ""
            stepUpPin = ""
            challengeId = ""
            code = ""
            errorMessage = errorRes
            phase = ChangePhoneNumberPhase.Intro
        }

        // Hard block: everything the flow was carrying is dropped, and the only ways forward are registering
        // a factor or leaving.
        fun blockOnStepUp(block: StepUpBlock) {
            currentPhone = ""
            reauthToken = ""
            stepUpPin = ""
            challengeId = ""
            code = ""
            errorMessage = null
            stepUpBlock = block
            phase = ChangePhoneNumberPhase.NeedsStepUp
        }

        fun showCooldown(remainingSeconds: Long?) {
            currentPhone = ""
            reauthToken = ""
            stepUpPin = ""
            code = ""
            errorMessage = null
            cooldownRemainingSeconds = remainingSeconds ?: 0L
            phase = ChangePhoneNumberPhase.Cooldown
        }

        /**
         * Sends the reauth OTP, but only if [enteredPhone] is the number the account is bound to. A refusal
         * shows the server's neutral reason only: saying whose number it is would be an ownership oracle.
         */
        fun requestReauthOtp(enteredPhone: String) {
            // Claim the screen before suspending, so a double tap cannot send two reauth starts.
            phase = ChangePhoneNumberPhase.Submitting
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                    return@launch
                }
                identityServiceClient.startPhoneChangeReauth(
                    accessToken = accessToken,
                    phone = enteredPhone,
                    language = Locale.getDefault().toLanguageTag(),
                )
                    .onSuccess {
                        currentPhone = enteredPhone
                        code = ""
                        errorMessage = null
                        // The new-number step reuses the same field, so it starts empty rather than
                        // pre-filled with the number being replaced.
                        localPhoneNumber = ""
                        phase = ChangePhoneNumberPhase.EnteringReauthOtp
                    }
                    .onFailure { error ->
                        errorMessage = when (error) {
                            is ResolverError.ReauthPhoneMismatch -> R.string.screen_change_phone_current_mismatch
                            is ResolverError.InvalidPhoneNumber -> R.string.screen_two_step_verification_phone_invalid
                            is ResolverError.RateLimited -> R.string.screen_two_step_verification_rate_limited
                            else -> CommonStrings.error_unknown
                        }
                        phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                    }
            }
        }

        /** Branches over the factors a phone change accepts, never over a lone `hasPin`: a passkey-only account already has two-step verification. */
        fun applyFactorStatus(status: AccountFactorStatus) {
            when {
                status.phoneChangeStepUpOptions.isEmpty() ->
                    blockOnStepUp(StepUpBlock.NoFactorRegistered)
                status.changePhoneCooldownRemainingSeconds > 0 ->
                    showCooldown(status.changePhoneCooldownRemainingSeconds)
                producibleStepUpFactors(status).isEmpty() ->
                    blockOnStepUp(StepUpBlock.PasskeyNotUsableHere)
                else -> {
                    code = ""
                    localPhoneNumber = ""
                    errorMessage = null
                    stepUpBlock = null
                    cooldownRemainingSeconds = 0L
                    phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                }
            }
        }

        /** Gate on the server's factor signal BEFORE anything is sent. */
        fun checkFactorsAndProceed() {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.accountFactorStatus(
                    accessToken = accessToken,
                    userId = matrixClient.sessionId.value,
                )
                    .onSuccess { status -> applyFactorStatus(status) }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.StepUpRequired,
                            is ResolverError.PinSetupRequired -> blockOnStepUp(StepUpBlock.NoFactorRegistered)
                            is ResolverError.TwoFactorCooldown -> showCooldown(error.retryAfterSeconds)
                            is ResolverError.PhoneChangeCooldown -> showCooldown(error.retryAfterSeconds)
                            else -> {
                                // The factors are unknown, not absent. Stop on the intro with an error
                                // rather than guessing "no factor".
                                errorMessage = CommonStrings.error_unknown
                                phase = ChangePhoneNumberPhase.Intro
                            }
                        }
                    }
            }
        }

        /**
         * Re-decides the interstitial the user is looking at from a fresh read. A failed read changes
         * nothing: the interstitial on screen is still the last thing the server said.
         */
        suspend fun refreshBlockingPhase() {
            val accessToken = accessToken() ?: return
            identityServiceClient.accountFactorStatus(
                accessToken = accessToken,
                userId = matrixClient.sessionId.value,
            )
                .onSuccess { status -> applyFactorStatus(status) }
        }

        // Factors are registered away from this screen, so the block is re-read on resume.
        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose { isResumed = false }
        }
        // Keyed on the value this composition saw: the resume callback can flip the state before the effect starts.
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed) return@LaunchedEffect
            // Only the two interstitials: a read landing on any other phase would drop the user out of their step.
            val isBlocked = phase == ChangePhoneNumberPhase.NeedsStepUp || phase == ChangePhoneNumberPhase.Cooldown
            if (!isBlocked) return@LaunchedEffect
            refreshBlockingPhase()
        }

        /** Exchanges the reauth OTP for the single-use, phone-change-scoped token. No SMS here. */
        fun verifyReauthOtp(enteredOtp: String) {
            phase = ChangePhoneNumberPhase.Submitting
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    phase = ChangePhoneNumberPhase.EnteringReauthOtp
                    return@launch
                }
                identityServiceClient.verifyPhoneChangeReauth(
                    accessToken = accessToken,
                    phone = currentPhone,
                    code = enteredOtp,
                )
                    .onSuccess { token ->
                        reauthToken = token
                        code = ""
                        errorMessage = null
                        phase = ChangePhoneNumberPhase.EnteringPin
                    }
                    .onFailure { error ->
                        code = ""
                        when (error) {
                            // The number stopped matching between the two calls, so there is no code
                            // left to retry: the current-number step is where this can be fixed.
                            is ResolverError.ReauthPhoneMismatch -> {
                                errorMessage = R.string.screen_change_phone_current_mismatch
                                currentPhone = ""
                                phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                            }
                            is ResolverError.InvalidPhoneNumber -> {
                                errorMessage = R.string.screen_two_step_verification_phone_invalid
                                currentPhone = ""
                                phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                phase = ChangePhoneNumberPhase.EnteringReauthOtp
                            }
                            else -> {
                                errorMessage = R.string.screen_change_phone_reauth_invalid
                                phase = ChangePhoneNumberPhase.EnteringReauthOtp
                            }
                        }
                    }
            }
        }

        /**
         * Spends the reauth token and the step-up factor, and only on success does an OTP reach the
         * new number. Every failure leaves the token spent, so each one restarts the flow.
         */
        fun startPhoneChange(enteredPhone: String) {
            phase = ChangePhoneNumberPhase.Submitting
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    phase = ChangePhoneNumberPhase.EnteringNewPhone
                    return@launch
                }
                val token = reauthToken
                if (token.isEmpty()) {
                    // Should never happen: the token is minted before this step.
                    abortSpentReauth(CommonStrings.error_unknown)
                    return@launch
                }
                val result = identityServiceClient.startPhoneChange(
                    accessToken = accessToken,
                    reauthToken = token,
                    newPhone = enteredPhone,
                    pin = stepUpPin,
                    // Android produces no passkey assertion yet, so the PIN is this client's step-up factor.
                    passkeyStepUpId = null,
                    passkeyCredentialJson = null,
                    language = Locale.getDefault().toLanguageTag(),
                )
                // Spent by the server before it weighed the step-up, so it is gone either way.
                reauthToken = ""
                stepUpPin = ""
                result
                    .onSuccess { challenge ->
                        challengeId = challenge.challengeId
                        code = ""
                        errorMessage = null
                        phase = ChangePhoneNumberPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.StepUpRequired,
                            is ResolverError.PinSetupRequired -> blockOnStepUp(StepUpBlock.NoFactorRegistered)
                            // A cooldown that started after the pre-check.
                            is ResolverError.TwoFactorCooldown -> showCooldown(error.retryAfterSeconds)
                            is ResolverError.PhoneChangeCooldown -> showCooldown(error.retryAfterSeconds)
                            is ResolverError.InvalidPin -> abortSpentReauth(R.string.screen_change_phone_pin_incorrect)
                            is ResolverError.PinLocked -> abortSpentReauth(R.string.screen_two_step_verification_locked)
                            is ResolverError.PhoneAlreadyLinked -> abortSpentReauth(R.string.screen_change_phone_already_linked)
                            is ResolverError.InvalidReauthToken -> abortSpentReauth(R.string.screen_change_phone_reauth_expired)
                            is ResolverError.RateLimited -> abortSpentReauth(R.string.screen_two_step_verification_rate_limited)
                            else -> abortSpentReauth(R.string.screen_change_phone_new_invalid)
                        }
                    }
            }
        }

        fun completePhoneChange(enteredOtp: String) {
            phase = ChangePhoneNumberPhase.Submitting
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    phase = ChangePhoneNumberPhase.EnteringOtp
                    return@launch
                }
                identityServiceClient.completePhoneChange(
                    accessToken = accessToken,
                    challengeId = challengeId,
                    code = enteredOtp,
                )
                    .onSuccess {
                        errorMessage = null
                        code = ""
                        challengeId = ""
                        phase = ChangePhoneNumberPhase.Done
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.InvalidOtp -> {
                                errorMessage = R.string.screen_change_phone_otp_invalid
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                            // The challenge is gone, or the number was taken in the meantime. Either
                            // way there is nothing left to redeem, so the whole flow restarts.
                            is ResolverError.PhoneChangeChallengeInvalid ->
                                abortSpentReauth(R.string.screen_change_phone_challenge_invalid)
                            is ResolverError.PhoneAlreadyLinked ->
                                abortSpentReauth(R.string.screen_change_phone_already_linked)
                            else -> {
                                errorMessage = CommonStrings.error_unknown
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringOtp
                            }
                        }
                    }
            }
        }

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
                    .onFailure { error ->
                        errorMessage = when (error) {
                            is ResolverError.PasskeyAlreadyRegistered ->
                                R.string.screen_two_step_verification_passkey_already_registered
                            is ResolverError.StepUpUnavailable ->
                                R.string.screen_two_step_verification_step_up_unavailable
                            else -> CommonStrings.error_unknown
                        }
                    }
            }
        }

        fun handleSubmittedCode(submitted: String) {
            when (phase) {
                ChangePhoneNumberPhase.EnteringReauthOtp -> verifyReauthOtp(submitted)
                ChangePhoneNumberPhase.EnteringPin -> {
                    // Captured, not verified: the server weighs it as the step-up factor on
                    // /account/phone/change/start, in the same call that texts the new number.
                    stepUpPin = submitted
                    code = ""
                    errorMessage = null
                    phase = ChangePhoneNumberPhase.EnteringNewPhone
                }
                ChangePhoneNumberPhase.EnteringOtp -> completePhoneChange(submitted)
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
                    // Normalise (strip a redundant country code from a paste or autofill, switch country
                    // if unambiguously international), then auto-detect the country. Only raw digits
                    // are stored.
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
                ChangePhoneNumberEvents.SetUpPasskey -> startPasskeyEnrollment()
                ChangePhoneNumberEvents.ClearPasskeyEnrollUrl -> {
                    passkeyEnrollUrl = null
                }
                ChangePhoneNumberEvents.Continue -> {
                    when (phase) {
                        ChangePhoneNumberPhase.Intro -> {
                            errorMessage = null
                            code = ""
                            stepUpBlock = null
                            checkFactorsAndProceed()
                        }
                        ChangePhoneNumberPhase.EnteringCurrentPhone -> {
                            val digits = localPhoneNumber.filter { it.isDigit() }
                            if (!ChangePhoneNumberState.isValidNumber(localDigits = digits, dialCode = selectedCountry.dialCode)) {
                                errorMessage = R.string.screen_two_step_verification_phone_invalid
                                return
                            }
                            errorMessage = null
                            requestReauthOtp("+" + selectedCountry.dialCode + digits)
                        }
                        ChangePhoneNumberPhase.EnteringNewPhone -> {
                            val digits = localPhoneNumber.filter { it.isDigit() }
                            if (!ChangePhoneNumberState.isValidNumber(localDigits = digits, dialCode = selectedCountry.dialCode)) {
                                errorMessage = R.string.screen_change_phone_new_invalid
                                return
                            }
                            val e164 = "+" + selectedCountry.dialCode + digits
                            errorMessage = null
                            startPhoneChange(e164)
                        }
                        ChangePhoneNumberPhase.EnteringReauthOtp,
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
            stepUpBlock = stepUpBlock,
            passkeyEnrollUrl = passkeyEnrollUrl,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken

    private companion object {
        /**
         * The step-up factors this client can produce. Asserting a passkey needs a WebAuthn ceremony this
         * Android build does not have yet, so the PIN is the only one it can offer. Steers the UI only and
         * is never sent to the identity service.
         */
        val PRODUCIBLE_STEP_UP_FACTORS = setOf(AuthFactor.PIN)

        /** The accepted step-up factors this account holds AND this client can produce, strongest first. */
        fun producibleStepUpFactors(status: AccountFactorStatus): List<AuthFactor> =
            status.phoneChangeStepUpOptions.filter { it in PRODUCIBLE_STEP_UP_FACTORS }
    }
}
