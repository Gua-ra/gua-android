/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Applies the national mask visually. The field value stays digits-only. */
data class PhoneNumberVisualTransformation(private val country: Country) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // The buffer can briefly hold non-digits (a pasted "+55..."): pass those frames through untransformed.
        if (raw.isEmpty() || raw.any { !it.isDigit() }) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = country.formatNational(raw)
        return TransformedText(AnnotatedString(formatted), MaskOffsetMapping(formatted))
    }

    private class MaskOffsetMapping(private val formatted: String) : OffsetMapping {
        // digitEnds[i] is the transformed offset just after the i-th digit.
        private val digitEnds: IntArray = run {
            val ends = IntArray(formatted.count { it.isDigit() })
            var digit = 0
            formatted.forEachIndexed { index, char ->
                if (char.isDigit()) {
                    ends[digit] = index + 1
                    digit++
                }
            }
            ends
        }

        override fun originalToTransformed(offset: Int): Int = if (offset == 0) 0 else digitEnds[offset - 1]

        override fun transformedToOriginal(offset: Int): Int = formatted.take(offset).count { it.isDigit() }
    }
}
