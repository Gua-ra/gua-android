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

/** Injected because presenter tests run without an Android context. */
interface DeviceCountryProvider {
    fun current(): Country

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
