/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.runtime.Composable
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
import java.util.Locale

/**
 * GUA FORK: presenter for the change-phone-number screen. Drives the real backend single-step
 * change-number flow: the user enters their new number, confirms their account PIN (which triggers an
 * OTP to the NEW number), then enters that OTP to complete the change. Translates typed
 * [ResolverError]s into per-error phase transitions, mirroring the iOS change-number view model.
 */
@Inject
class ChangePhoneNumberPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
) : Presenter<ChangePhoneNumberState> {
    @Composable
    override fun present(): ChangePhoneNumberState {
        val coroutineScope = rememberCoroutineScope()

        var phase by remember { mutableStateOf(ChangePhoneNumberPhase.Intro) }
        var code by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<Int?>(null) }

        // Flow scratch state.
        var newPhone by remember { mutableStateOf("") }
        var storedPin by remember { mutableStateOf("") }

        fun resetFlowState() {
            errorMessage = null
            code = ""
            phone = ""
            newPhone = ""
            storedPin = ""
        }

        fun requestOtp(enteredPin: String) {
            coroutineScope.launch {
                val accessToken = accessToken()
                if (accessToken == null) {
                    errorMessage = CommonStrings.error_unknown
                    return@launch
                }
                phase = ChangePhoneNumberPhase.Submitting
                identityServiceClient.requestPhoneChangeOtp(
                    accessToken = accessToken,
                    newPhone = newPhone,
                    language = Locale.getDefault().toLanguageTag(),
                )
                    .onSuccess {
                        storedPin = enteredPin
                        code = ""
                        errorMessage = null
                        phase = ChangePhoneNumberPhase.EnteringOtp
                    }
                    .onFailure { error ->
                        when (error) {
                            is ResolverError.RateLimited -> {
                                errorMessage = R.string.screen_two_step_verification_rate_limited
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            is ResolverError.InvalidPin -> {
                                errorMessage = R.string.screen_change_phone_pin_incorrect
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            else -> {
                                // 400 / invalid-phone and anything else: send the user back to the
                                // number step with the phone cleared.
                                errorMessage = R.string.screen_change_phone_new_invalid
                                newPhone = ""
                                phone = ""
                                code = ""
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
                    pin = storedPin,
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
                            is ResolverError.InvalidPin -> {
                                // Re-submitting the PIN re-sends a fresh OTP, so clear the stored pin.
                                errorMessage = R.string.screen_change_phone_pin_incorrect
                                storedPin = ""
                                code = ""
                                phase = ChangePhoneNumberPhase.EnteringPin
                            }
                            is ResolverError.PhoneAlreadyLinked -> {
                                errorMessage = R.string.screen_change_phone_already_linked
                                newPhone = ""
                                phone = ""
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
                ChangePhoneNumberPhase.EnteringPin -> requestOtp(submitted)
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
                    phone = event.phone
                    if (errorMessage != null) errorMessage = null
                }
                ChangePhoneNumberEvents.Continue -> {
                    when (phase) {
                        ChangePhoneNumberPhase.Intro -> {
                            errorMessage = null
                            phase = ChangePhoneNumberPhase.EnteringNewPhone
                        }
                        ChangePhoneNumberPhase.EnteringNewPhone -> {
                            val trimmed = phone.trim()
                            if (!ChangePhoneNumberState.isValidPhone(trimmed)) {
                                errorMessage = R.string.screen_change_phone_new_invalid
                                return
                            }
                            newPhone = trimmed
                            phone = trimmed
                            code = ""
                            errorMessage = null
                            phase = ChangePhoneNumberPhase.EnteringPin
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
            phone = phone,
            errorMessage = errorMessage,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? =
        sessionStore.getSession(matrixClient.sessionId.value)?.accessToken
}
