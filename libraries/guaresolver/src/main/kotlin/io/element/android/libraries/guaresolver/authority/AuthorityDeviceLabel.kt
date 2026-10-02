/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import android.os.Build

object AuthorityDeviceLabel {
    private const val MAX_BYTES = 16
    private const val FALLBACK = "This phone"

    fun current(model: String? = Build.MODEL): String {
        val candidate = model?.trim().orEmpty().ifEmpty { FALLBACK }
        return fit(candidate) ?: FALLBACK
    }

    private fun fit(value: String): String? {
        if (value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) return value
        var end = value.length
        while (end > 0) {
            val candidate = value.substring(0, end).trimEnd()
            if (candidate.isNotEmpty() && candidate.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                return candidate
            }
            end--
        }
        return null
    }
}
