/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * GUA FORK: resolves the device's default E.164 dial code so address-book national numbers can be
 * upgraded to E.164 for contact discovery. Android counterpart of iOS `Country.deviceDefault.dialCode`.
 *
 * Prefers the SIM/network region, then the device locale, then the US (+1) fallback. Covers the
 * primary Gua markets (Brazil/Canada) plus common regions; an unknown region falls back to +1, which
 * the identity-service then validates and silently skips if the result is not valid E.164.
 */
internal object DeviceDialCode {
    private const val FALLBACK = "1"

    fun resolve(context: Context): String {
        val region = resolveRegion(context)
        return DIAL_CODES[region] ?: FALLBACK
    }

    private fun resolveRegion(context: Context): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simRegion = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        val networkRegion = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        val localeRegion = Locale.getDefault().country.takeIf { it.isNotBlank() }
        return (simRegion ?: networkRegion ?: localeRegion).orEmpty().uppercase(Locale.ROOT)
    }

    private val DIAL_CODES: Map<String, String> = mapOf(
        "US" to "1", "CA" to "1",
        "BR" to "55",
        "GB" to "44", "IE" to "353",
        "PT" to "351", "ES" to "34", "FR" to "33", "DE" to "49", "IT" to "39", "NL" to "31",
        "MX" to "52", "AR" to "54", "CL" to "56", "CO" to "57", "PE" to "51",
        "AU" to "61", "NZ" to "64",
        "IN" to "91", "JP" to "81", "CN" to "86", "KR" to "82",
        "ZA" to "27", "NG" to "234",
    )
}
