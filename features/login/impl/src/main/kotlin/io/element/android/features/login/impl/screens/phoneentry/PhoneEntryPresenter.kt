/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.login.impl.login.LoginHelper
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.DeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import kotlinx.coroutines.launch

@AssistedInject
class PhoneEntryPresenter(
    @Assisted private val params: PhoneEntryNode.Params,
    private val loginHelper: LoginHelper,
    private val selectedCountryStore: SelectedCountryStore,
    private val deviceCountryProvider: DeviceCountryProvider,
) : Presenter<PhoneEntryState> {
    @AssistedFactory
    interface Factory {
        fun create(params: PhoneEntryNode.Params): PhoneEntryPresenter
    }

    @Composable
    override fun present(): PhoneEntryState {
        val coroutineScope = rememberCoroutineScope()

        // The local number is held as raw digits. The mask is applied visually by the field.
        val initial = remember { deviceCountryProvider.parse(params.initialPhoneNumber) }
        var selectedCountry by rememberSaveable { mutableStateOf(initial.first) }
        var localPhoneNumber by rememberSaveable { mutableStateOf(initial.second) }

        val loginMode by loginHelper.collectLoginMode()

        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                selectedCountryStore.consume()
            }
        }

        fun handleEvent(event: PhoneEntryEvents) {
            when (event) {
                is PhoneEntryEvents.PhoneNumberChanged -> {
                    val (normalizedCountry, normalizedDigits) = Country.normalize(
                        rawInput = event.value,
                        current = selectedCountry,
                    )
                    val country = Country.detect(localDigits = normalizedDigits, current = normalizedCountry) ?: normalizedCountry
                    selectedCountry = country
                    localPhoneNumber = normalizedDigits
                }
                is PhoneEntryEvents.CountrySelected -> {
                    selectedCountry = event.country
                }
                PhoneEntryEvents.Continue -> {
                    val e164 = "+" + selectedCountry.dialCode + localPhoneNumber.filter { it.isDigit() }
                    coroutineScope.launch {
                        loginHelper.submitPhone(e164)
                    }
                }
                PhoneEntryEvents.SignInWithPasskey -> {
                    coroutineScope.launch {
                        loginHelper.submitPasskey()
                    }
                }
                PhoneEntryEvents.ClearError -> loginHelper.clearError()
            }
        }

        return PhoneEntryState(
            selectedCountry = selectedCountry,
            localPhoneNumber = localPhoneNumber,
            loginMode = loginMode,
            eventSink = ::handleEvent,
        )
    }
}
