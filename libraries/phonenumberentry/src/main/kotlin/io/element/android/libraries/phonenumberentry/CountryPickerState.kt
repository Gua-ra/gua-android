/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import kotlinx.collections.immutable.ImmutableList

data class CountryPickerState(
    val query: String,
    val countries: ImmutableList<Country>,
    val eventSink: (CountryPickerEvents) -> Unit,
)

sealed interface CountryPickerEvents {
    data class QueryChanged(val query: String) : CountryPickerEvents
}
