/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry.country

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import kotlinx.collections.immutable.toImmutableList

@Inject
class CountryPickerPresenter : Presenter<CountryPickerState> {
    @Composable
    override fun present(): CountryPickerState {
        var query by remember { mutableStateOf("") }

        val countries = remember(query) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                all
            } else {
                all.filter { country ->
                    country.name.contains(trimmed, ignoreCase = true) ||
                        country.dialCode.startsWith(trimmed.trimStart('+')) ||
                        country.isoCode.equals(trimmed, ignoreCase = true)
                }
            }.toImmutableList()
        }

        fun handleEvent(event: CountryPickerEvents) {
            when (event) {
                is CountryPickerEvents.QueryChanged -> query = event.query
            }
        }

        return CountryPickerState(
            query = query,
            countries = countries,
            eventSink = ::handleEvent,
        )
    }
}
