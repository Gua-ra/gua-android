/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GUA FORK: bridges the country picked in [CountryPickerNode] back to the screen that opened it
 * (the welcome phone-entry screen or the change-phone-number screen) across the Appyx pop. Scoped to
 * the app so the picker and its caller share one instance regardless of which flow hosts them.
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
