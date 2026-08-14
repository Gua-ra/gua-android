/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TransformedText
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberVisualTransformationTest {
    private val us = Country("US", "1")
    private val br = Country("BR", "55")

    private fun transform(country: Country, digits: String): TransformedText =
        PhoneNumberVisualTransformation(country).filter(AnnotatedString(digits))

    @Test
    fun `empty input stays empty with identity mapping`() {
        val transformed = transform(us, "")
        assertThat(transformed.text.text).isEmpty()
        assertThat(transformed.offsetMapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(transformed.offsetMapping.transformedToOriginal(0)).isEqualTo(0)
    }

    @Test
    fun `single digit maps the cursor after the digit, past any mask prefix`() {
        // "4" renders as "(4": the cursor must land after the digit (offset 2), not inside "(".
        val transformed = transform(us, "4")
        assertThat(transformed.text.text).isEqualTo("(4")
        assertThat(transformed.offsetMapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(transformed.offsetMapping.originalToTransformed(1)).isEqualTo(2)
        assertThat(transformed.offsetMapping.transformedToOriginal(0)).isEqualTo(0)
        assertThat(transformed.offsetMapping.transformedToOriginal(1)).isEqualTo(0)
        assertThat(transformed.offsetMapping.transformedToOriginal(2)).isEqualTo(1)
    }

    @Test
    fun `typing the digit that closes a group jumps the cursor past the inserted separators`() {
        // 3 digits: "(415", cursor after the last digit.
        val three = transform(us, "415")
        assertThat(three.text.text).isEqualTo("(415")
        assertThat(three.offsetMapping.originalToTransformed(3)).isEqualTo(4)
        // 4th digit closes the area-code group: "(415) 2", cursor lands after the "2" (offset 7).
        val four = transform(us, "4152")
        assertThat(four.text.text).isEqualTo("(415) 2")
        assertThat(four.offsetMapping.originalToTransformed(4)).isEqualTo(7)
    }

    @Test
    fun `full number offsets round-trip at every position`() {
        val transformed = transform(us, "4152731234")
        assertThat(transformed.text.text).isEqualTo("(415) 273-1234")
        val mapping = transformed.offsetMapping
        for (offset in 0..10) {
            // Mapping a digit-cursor into the mask and back must always return the same position.
            assertThat(mapping.transformedToOriginal(mapping.originalToTransformed(offset))).isEqualTo(offset)
        }
        assertThat(mapping.originalToTransformed(10)).isEqualTo(14)
        assertThat(mapping.transformedToOriginal(14)).isEqualTo(10)
    }

    @Test
    fun `cursor at a separator maps back to the digit boundary so backspace deletes a digit`() {
        val mapping = transform(us, "4152731234").offsetMapping
        // "(415) 273-1234": placing the cursor after "(415) " (transformed 6) is after 3 digits, so
        // backspace removes the "5"; after the "-" (transformed 10) it is after 6 digits.
        assertThat(mapping.transformedToOriginal(6)).isEqualTo(3)
        assertThat(mapping.transformedToOriginal(10)).isEqualTo(6)
    }

    @Test
    fun `offset mapping is monotonic across the whole mask`() {
        val transformed = transform(br, "11912345678")
        assertThat(transformed.text.text).isEqualTo("(11) 91234-5678")
        val mapping = transformed.offsetMapping
        var previous = 0
        for (offset in 0..11) {
            val mapped = mapping.originalToTransformed(offset)
            assertThat(mapped).isAtLeast(previous)
            previous = mapped
        }
        previous = 0
        for (offset in 0..transformed.text.length) {
            val mapped = mapping.transformedToOriginal(offset)
            assertThat(mapped).isAtLeast(previous)
            previous = mapped
        }
    }

    @Test
    fun `digits past the mask are appended unformatted and stay mapped`() {
        val transformed = transform(us, "415273123499")
        assertThat(transformed.text.text).isEqualTo("(415) 273-123499")
        assertThat(transformed.offsetMapping.originalToTransformed(12)).isEqualTo(16)
        assertThat(transformed.offsetMapping.transformedToOriginal(16)).isEqualTo(12)
    }

    @Test
    fun `non-digit content passes through untransformed`() {
        // A pasted "+..." can sit in the buffer for a frame before the presenter normalises it;
        // formatting would drop the "+" and desync the offsets, so those frames are identity.
        val transformed = transform(us, "+5511912345678")
        assertThat(transformed.text.text).isEqualTo("+5511912345678")
        assertThat(transformed.offsetMapping.originalToTransformed(5)).isEqualTo(5)
        assertThat(transformed.offsetMapping.transformedToOriginal(5)).isEqualTo(5)
    }
}
