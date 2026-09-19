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

/**
 * GUA FORK: renders raw national digits with the country's national mask (e.g. "5551234567" ->
 * "(555) 123-4567") purely visually. The field value stays digits-only, so ordinary typing never
 * rewrites the text buffer (which is what used to garble the cursor at group boundaries and reorder
 * rapid input); [OffsetMapping] translates cursor positions between the digit string and the mask.
 */
data class PhoneNumberVisualTransformation(private val country: Country) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // The presenters keep the value digits-only, but the field's internal buffer can briefly
        // hold other characters (e.g. a pasted "+55...") before normalisation lands. Formatting
        // would drop them and desync the offsets, so pass those frames through untransformed.
        if (raw.isEmpty() || raw.any { !it.isDigit() }) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = country.formatNational(raw)
        return TransformedText(AnnotatedString(formatted), MaskOffsetMapping(formatted))
    }

    private class MaskOffsetMapping(private val formatted: String) : OffsetMapping {
        // digitEnds[i] = transformed offset just after the i-th digit. formatNational() never emits
        // a trailing literal, so the last entry is always formatted.length.
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

        // A cursor after the n-th digit lands just after that digit in the mask, so a just-typed
        // digit is followed by the cursor even when the mask inserts literals before or after it.
        override fun originalToTransformed(offset: Int): Int = if (offset == 0) 0 else digitEnds[offset - 1]

        // A cursor in the mask maps back to how many digits precede it; deleting backwards over a
        // literal (space/dash/paren) therefore deletes the digit before the literal, never nothing.
        override fun transformedToOriginal(offset: Int): Int = formatted.take(offset).count { it.isDigit() }
    }
}
