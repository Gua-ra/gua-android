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
 * GUA FORK: resolves the device's default E.164 dial code so address-book national numbers can be
 * upgraded to E.164 for contact discovery.
 *
 * Delegates to [Country.deviceDefault], which reads the SIM first, then the network, then the
 * locale. This used to carry its own 24-entry dial-code table, which silently produced +1 for
 * everyone outside it; the shared list covers every country the picker offers.
 */
internal object DeviceDialCode {
    fun resolve(context: Context): String = Country.deviceDefault(context).dialCode
}
