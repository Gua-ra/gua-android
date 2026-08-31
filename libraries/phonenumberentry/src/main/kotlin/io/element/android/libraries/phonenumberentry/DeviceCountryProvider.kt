/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext

/**
 * GUA FORK: resolves the country to preselect on the phone number field.
 *
 * Injected rather than read from `LocalContext` inside the presenter, because presenter tests run
 * without an Android context and reading the composition local throws there. It also keeps the
 * SIM lookup out of composition.
 */
interface DeviceCountryProvider {
    fun current(): Country

    /** Splits an optional pre-populated E.164 number, falling back to [current] when it cannot. */
    fun parse(initialPhoneNumber: String?): Pair<Country, String>
}

@ContributesBinding(AppScope::class)
class DefaultDeviceCountryProvider(
    @ApplicationContext private val context: Context,
) : DeviceCountryProvider {
    override fun current(): Country = Country.deviceDefault(context)

    override fun parse(initialPhoneNumber: String?): Pair<Country, String> =
        Country.parse(initialPhoneNumber.orEmpty(), context)
}
