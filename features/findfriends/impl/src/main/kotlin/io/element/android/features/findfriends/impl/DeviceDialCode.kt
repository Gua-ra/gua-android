/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import android.content.Context
import io.element.android.libraries.phonenumberentry.Country

/**
 * Resolves the device's default E.164 dial code so address-book national numbers can be upgraded to
 * E.164. Delegates to [Country.deviceDefault], which reads the SIM first, then the network, then the locale.
 */
internal object DeviceDialCode {
    fun resolve(context: Context): String = Country.deviceDefault(context).dialCode
}
