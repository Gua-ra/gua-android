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
 * The reauth token is single-use and the server spends it before weighing the step-up, so any later failure restarts the flow.
 * Only the call that spends the step-up factor texts the new number.
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

        var currentPhone by remember { mutableStateOf("") }
        var reauthToken by remember { mutableStateOf("") }
        var stepUpPin by remember { mutableStateOf("") }
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

        /** Drops every credential and returns to the start. Never re-sends automatically. */
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

        /** A refusal shows the server's neutral reason only: saying whose number it is would be an ownership oracle. */
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
                                errorMessage = CommonStrings.error_unknown
                                phase = ChangePhoneNumberPhase.Intro
                            }
                        }
                    }
            }
        }

        /** A failed read changes nothing. */
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
        /** Steers the UI only. Never sent to the identity service. */
        val PRODUCIBLE_STEP_UP_FACTORS = setOf(AuthFactor.PIN)

        fun producibleStepUpFactors(status: AccountFactorStatus): List<AuthFactor> =
            status.phoneChangeStepUpOptions.filter { it in PRODUCIBLE_STEP_UP_FACTORS }
    }
}
