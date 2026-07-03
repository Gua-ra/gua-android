/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry.country

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GUA FORK: bridges the country picked in [CountryPickerNode] back to the [PhoneEntryPresenter]
 * across the Appyx pop, the same way [AccountProviderDataSource] bridges the account provider across
 * the login flow. Scoped to the app so both the picker and the phone-entry screen share one instance.
 */
@SingleIn(AppScope::class)
@Inject
class SelectedCountryStore {
    private val country = MutableStateFlow<Country?>(null)
    val flow: StateFlow<Country?> = country.asStateFlow()

    fun select(value: Country) {
        country.value = value
    }

    /** Clear the pending selection once consumed, so re-opening the picker doesn't re-apply it. */
    fun consume() {
        country.value = null
    }
}
