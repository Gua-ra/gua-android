/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

/**
 * GUA FORK: a [DeviceCountryProvider] with no Android context behind it, for tests and previews.
 * Defaults to the same fallback the real one uses when a device tells it nothing.
 */
class FakeDeviceCountryProvider(
    private val country: Country = Country.fallback,
) : DeviceCountryProvider {
    override fun current(): Country = country

    override fun parse(initialPhoneNumber: String?): Pair<Country, String> =
        Country.parse(initialPhoneNumber.orEmpty(), country)
}
