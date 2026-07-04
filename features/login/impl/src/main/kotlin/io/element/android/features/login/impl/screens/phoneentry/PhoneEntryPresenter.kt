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
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.launch

@AssistedInject
class PhoneEntryPresenter(
    @Assisted private val params: PhoneEntryNode.Params,
    private val loginHelper: LoginHelper,
    private val selectedCountryStore: SelectedCountryStore,
) : Presenter<PhoneEntryState> {
    @AssistedFactory
    interface Factory {
        fun create(params: PhoneEntryNode.Params): PhoneEntryPresenter
    }

    @Composable
    override fun present(): PhoneEntryState {
        val coroutineScope = rememberCoroutineScope()

        // Seed country + local number from any pre-populated E.164 number (else device locale).
        val initial = remember { Country.parse(params.initialPhoneNumber.orEmpty()) }
        var selectedCountry by rememberSaveable { mutableStateOf(initial.first) }
        var localPhoneNumber by rememberSaveable {
            mutableStateOf(initial.first.formatNational(initial.second))
        }

        val loginMode by loginHelper.collectLoginMode()

        // Apply any country picked in the CountryPicker child screen, then clear it.
        val pickedCountry by selectedCountryStore.flow.collectAsState()
        LaunchedEffect(pickedCountry) {
            pickedCountry?.let { country ->
                selectedCountry = country
                localPhoneNumber = country.formatNational(localPhoneNumber.filter { it.isDigit() })
                selectedCountryStore.consume()
            }
        }

        fun handleEvent(event: PhoneEntryEvents) {
            when (event) {
                is PhoneEntryEvents.PhoneNumberChanged -> {
                    // First normalize: if the user pasted/autofilled a number that includes the country
                    // code (with or without a leading "+"), strip the redundant code and switch the
                    // country when the input is unambiguously international. No-op for ordinary typing.
                    val (normalizedCountry, normalizedDigits) = Country.normalize(
                        rawInput = event.value,
                        current = selectedCountry,
                    )
                    // Then auto-detect (longest-prefix dial code / NANP US<->CA area code) on the
                    // stripped digits, and reformat with the resulting country's national mask.
                    val country = Country.detect(localDigits = normalizedDigits, current = normalizedCountry) ?: normalizedCountry
                    selectedCountry = country
                    localPhoneNumber = country.formatNational(normalizedDigits)
                }
                is PhoneEntryEvents.CountrySelected -> {
                    selectedCountry = event.country
                    localPhoneNumber = event.country.formatNational(localPhoneNumber.filter { it.isDigit() })
                }
                PhoneEntryEvents.Continue -> {
                    val e164 = "+" + selectedCountry.dialCode + localPhoneNumber.filter { it.isDigit() }
                    coroutineScope.launch {
                        loginHelper.submitPhone(e164)
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
