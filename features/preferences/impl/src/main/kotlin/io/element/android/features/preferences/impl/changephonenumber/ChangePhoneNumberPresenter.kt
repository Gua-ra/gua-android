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
 * GUA FORK: presenter for the change-phone-number screen, driving the real identity-service
 * contract: `POST /account/reauth/start` + `/account/reauth/verify` for the single-use,
 * phone-change-scoped token, then `POST /account/phone/change/start` + `/complete`.
 *
 * Security shape, in the order it runs:
 *  1. The account's FACTORS are read first. An account that holds no factor a phone change accepts
 *     is blocked outright, and one still inside a cooldown is held; neither goes any further.
 *  2. The user says which number is on the account. The server never publishes that number, so this
 *     is the only way to check it: identity-service digests what arrives and compares it against the
 *     account's own binding, and only a match is texted. A miss is refused with one wording for
 *     "unknown", "someone else's" and "not this one", and this screen adds nothing to it.
 *  3. The OTP that number received proves possession of it and buys a token. That proof alone is not
 *     enough to re-point the number, since a SIM-swapper holds that number too.
 *  4. The step-up factor is collected and spent together with the new number. Only that call texts
 *     the NEW number, so no SMS reaches it until the server has accepted the factor.
 *
 * The token is single-use and the server spends it BEFORE it weighs the step-up, so it is gone on
 * every outcome of step 4. Any failure there therefore restarts the flow rather than retrying, and
 * a `step_up_required` refusal terminates the operation instead of falling back to the token alone.
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
        // Whichever number is being typed, current or new, held as (country, RAW national digits)
        // like the welcome PhoneEntry screen. The national mask is applied purely visually by
        // PhoneNumberEntryField.
        var selectedCountry by remember { mutableStateOf(deviceCountryProvider.current()) }
        var localPhoneNumber by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }
        // Remaining cooldown surfaced on the Cooldown interstitial (0 otherwise).
        var cooldownRemainingSeconds by remember { mutableLongStateOf(0L) }
        var stepUpBlock by remember { mutableStateOf<StepUpBlock?>(null) }
        var passkeyEnrollUrl by remember { mutableStateOf<String?>(null) }

        // Apply any country picked in the shared CountryPicker child screen, then clear it.
        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        // Flow scratch state.
        // The number the user says is on the account, in E.164. Both reauth calls carry it: the
        // server stores nothing between them and re-derives the digest from what arrives each time.
        var currentPhone by remember { mutableStateOf("") }
        // Single-use token from /account/reauth/verify, scoped to PHONE_CHANGE. The server spends it
        // on the first /start attempt whether or not the step-up that follows is accepted, so it is
        // cleared on every outcome and a retry always mints a fresh one.
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
         * The token is gone and the flow cannot continue: drop every credential it was holding and
         * send the user back to the start, where the factor and cooldown pre-checks run again before
         * a fresh reauth OTP is sent. Deliberately NOT an automatic re-send: a spent token must cost
         * a deliberate restart, not a silent SMS.
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

        fun blockOnStepUp(block: StepUpBlock) {
            // Hard block. Everything the flow was carrying is dropped, and the only ways forward are
            // registering a factor or leaving. There is no branch from here into the change itself.
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
         * Sends the reauth OTP, but only if [enteredPhone] is the number the account is bound to.
         * Never touches the new number.
         *
         * A refusal keeps the user on the current-number step with the server's own neutral reason.
         * Nothing here distinguishes a number nobody holds from one somebody else holds, because the
         * server does not either, and inventing that distinction on the client would hand a stolen
         * session an oracle over who owns which number.
         */
        fun requestReauthOtp(enteredPhone: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
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

        /**
         * Gate on the server's factor signal BEFORE anything is sent. The branch is over the factors
         * a phone change accepts and the account actually holds, never over a lone `hasPin`: an
         * account with a passkey and no PIN already has two-step verification and must not be sent
         * to set up a PIN as though it had nothing.
         */
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
                    .onSuccess { status ->
                        when {
                            status.phoneChangeStepUpOptions.isEmpty() ->
                                blockOnStepUp(StepUpBlock.NoFactorRegistered)
                            status.changePhoneCooldownRemainingSeconds > 0 ->
                                showCooldown(status.changePhoneCooldownRemainingSeconds)
                            producibleStepUpFactors(status).isEmpty() ->
                                blockOnStepUp(StepUpBlock.PasskeyNotUsableHere)
                            else -> {
                                // Still nothing sent: the user has to say which number is on the
                                // account before anything is texted anywhere.
                                code = ""
                                localPhoneNumber = ""
                                errorMessage = null
                                phase = ChangePhoneNumberPhase.EnteringCurrentPhone
                            }
                        }
                    }
                    .onFailure { error ->
                        when (error) {
                            // The account can settle no step-up: the same hard block, whichever
                            // spelling the identity-service uses for it.
                            is ResolverError.StepUpRequired,
                            is ResolverError.PinSetupRequired -> blockOnStepUp(StepUpBlock.NoFactorRegistered)
                            is ResolverError.TwoFactorCooldown -> showCooldown(error.retryAfterSeconds)
                            is ResolverError.PhoneChangeCooldown -> showCooldown(error.retryAfterSeconds)
                            else -> {
                                // The factors are UNKNOWN, not absent. Stop on the intro with an
                                // error rather than guessing, because guessing "no factor" is how a
                                // passkey holder gets told to create a PIN.
                                errorMessage = CommonStrings.error_unknown
                                phase = ChangePhoneNumberPhase.Intro
                            }
                        }
                    }
            }
        }

        /** Exchanges the reauth OTP for the single-use, phone-change-scoped token. No SMS here. */
        fun verifyReauthOtp(enteredOtp: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.verifyPhoneChangeReauth(
                    accessToken = accessToken,
                    phone = currentPhone,
                    code = enteredOtp,
                )
                    .onSuccess { token ->
                        reauthToken = token
                        code = ""
                        errorMessage = null
                        // Still no SMS to the new number: the step-up comes first.
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
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                val token = reauthToken
                if (token.isEmpty()) {
                    // Should never happen: the token is minted before this step.
                    abortSpentReauth(CommonStrings.error_unknown)
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                val result = identityServiceClient.startPhoneChange(
                    accessToken = accessToken,
                    reauthToken = token,
                    newPhone = enteredPhone,
                    pin = stepUpPin,
                    // No passkey assertion is produced on Android yet; the PIN is this client's
                    // step-up factor. The server ranks the passkey above it and still accepts one
                    // from any client that can assert it.
                    passkeyStepUpId = null,
                    passkeyCredentialJson = null,
                    language = Locale.getDefault().toLanguageTag(),
                )
                // Spent by the server before it weighed the step-up, so it is gone either way.
                reauthToken = ""
                stepUpPin = ""
                result
                    .onSuccess { challenge ->
                        // SMS to the NEW number fired here, and only here.
                        challengeId = challenge.challengeId
                        code = ""
                        errorMessage = null
                        phase = ChangePhoneNumberPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            // Hard block: the account holds no step-up factor. The operation ends,
                            // it is never retried on the reauth token alone.
                            is ResolverError.StepUpRequired,
                            is ResolverError.PinSetupRequired -> blockOnStepUp(StepUpBlock.NoFactorRegistered)
                            // Defense in depth: a cooldown that started after the pre-check.
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
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
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
                    .onFailure {
                        errorMessage = CommonStrings.error_unknown
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
                ChangePhoneNumberEvents.SetUpPasskey -> startPasskeyEnrollment()
                ChangePhoneNumberEvents.ClearPasskeyEnrollUrl -> {
                    passkeyEnrollUrl = null
                }
                ChangePhoneNumberEvents.Continue -> {
                    when (phase) {
                        ChangePhoneNumberPhase.Intro -> {
                            // Gate FIRST on the account's factors; nothing is sent before that.
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
         * The step-up factors this client can actually produce. Asserting a passkey needs a WebAuthn
         * ceremony this Android build does not have yet, so the PIN is the only one it can offer.
         *
         * This steers the UI and nothing else. It is never sent to the identity service, which does
         * not accept "my passkey is unavailable" as an input and weighs only what actually arrives.
         */
        val PRODUCIBLE_STEP_UP_FACTORS = setOf(AuthFactor.PIN)

        /** The accepted step-up factors this account holds AND this client can produce, strongest first. */
        fun producibleStepUpFactors(status: AccountFactorStatus): List<AuthFactor> =
            status.phoneChangeStepUpOptions.filter { it in PRODUCIBLE_STEP_UP_FACTORS }
    }
}
