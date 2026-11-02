/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.phonenumberentry.Country
import org.junit.Test

class PhoneEntryStateTest {
    private val unitedStates = Country(isoCode = "US", dialCode = "1")
    private val brazil = Country(isoCode = "BR", dialCode = "55")
    private val hongKong = Country(isoCode = "HK", dialCode = "852")

    @Test
    fun `a full-length number that is invalid for the country shows the hint`() {
        assertThat(aPhoneEntryState(selectedCountry = unitedStates, localPhoneNumber = "5551234567").showInvalidNumberHint).isTrue()
    }

    @Test
    fun `a number typed under the wrong country shows the hint once it is long enough`() {
        assertThat(aPhoneEntryState(selectedCountry = hongKong, localPhoneNumber = "1191234").showInvalidNumberHint).isFalse()
        assertThat(aPhoneEntryState(selectedCountry = hongKong, localPhoneNumber = "11912345").showInvalidNumberHint).isTrue()
        assertThat(aPhoneEntryState(selectedCountry = hongKong, localPhoneNumber = "11912345678").showInvalidNumberHint).isTrue()
    }

    @Test
    fun `a number still being typed shows no hint`() {
        assertThat(aPhoneEntryState(selectedCountry = unitedStates, localPhoneNumber = "555123456").showInvalidNumberHint).isFalse()
        assertThat(aPhoneEntryState(selectedCountry = brazil, localPhoneNumber = "1191234567").showInvalidNumberHint).isFalse()
    }

    @Test
    fun `a valid number shows no hint`() {
        assertThat(aPhoneEntryState(selectedCountry = unitedStates, localPhoneNumber = "2015550123").showInvalidNumberHint).isFalse()
        assertThat(aPhoneEntryState(selectedCountry = brazil, localPhoneNumber = "11912345678").showInvalidNumberHint).isFalse()
    }

    @Test
    fun `no hint while submitting`() {
        val state = aPhoneEntryState(
            selectedCountry = unitedStates,
            localPhoneNumber = "5551234567",
            loginMode = AsyncData.Loading(),
        )
        assertThat(state.showInvalidNumberHint).isFalse()
    }
}
