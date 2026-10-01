/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import android.content.Context
import io.element.android.libraries.phonenumberentry.Country

internal object DeviceDialCode {
    fun resolve(context: Context): String = Country.deviceDefault(context).dialCode
}
