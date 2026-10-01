/*
 * Copyright 2026 Gua
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

/** Carries the picked country back to the screen that opened the picker. */
@SingleIn(AppScope::class)
@Inject
class SelectedCountryStore {
    private val country = MutableStateFlow<Country?>(null)
    val flow: StateFlow<Country?> = country.asStateFlow()

    fun select(value: Country) {
        country.value = value
    }

    fun consume() {
        country.value = null
    }
}
