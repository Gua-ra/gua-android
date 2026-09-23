/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import android.os.Build

/**
 * GUA FORK: the 16-byte label an authority record carries (ADM-009 decision 2).
 *
 * It is what a notification about a pending transition is allowed to name, so it has to say which physical
 * phone this is, and it has to fit: the field is 16 bytes of UTF-8 and a label that overflows is refused
 * rather than truncated, because truncation can cut a character in half and the notification would then name
 * something the owner never saw.
 *
 * It lives beside the record code rather than beside a screen because two callers need the same 16 bytes: the
 * authority screen, which signs records, and the session-start registrar, which tells the notification channel
 * what a notification may name.
 *
 * The model name is used because it is the one string the user already recognises from their own settings,
 * and it is trimmed to whole characters that fit. It is not an identifier: it carries no serial, no
 * advertising id and nothing that distinguishes one handset from another of the same model.
 */
object AuthorityDeviceLabel {
    private const val MAX_BYTES = 16
    private const val FALLBACK = "This phone"

    fun current(model: String? = Build.MODEL): String {
        val candidate = model?.trim().orEmpty().ifEmpty { FALLBACK }
        return fit(candidate) ?: FALLBACK
    }

    /** The longest prefix of whole characters whose UTF-8 encoding fits, or null when even one does not. */
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
