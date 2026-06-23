/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry.country

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class CountryPickerStateProvider : PreviewParameterProvider<CountryPickerState> {
    override val values: Sequence<CountryPickerState>
        get() = sequenceOf(
            // Full list.
            aCountryPickerState(),
            // Search-filtered.
            aCountryPickerState(
                query = "Bra",
                countries = persistentListOf(Country(isoCode = "BR", dialCode = "55")),
            ),
            // Empty result.
            aCountryPickerState(
                query = "zzzz",
                countries = persistentListOf(),
            ),
        )
}

internal fun aCountryPickerState(
    query: String = "",
    countries: kotlinx.collections.immutable.ImmutableList<Country> = all.take(12).toImmutableList(),
    eventSink: (CountryPickerEvents) -> Unit = {},
) = CountryPickerState(
    query = query,
    countries = countries,
    eventSink = eventSink,
)
