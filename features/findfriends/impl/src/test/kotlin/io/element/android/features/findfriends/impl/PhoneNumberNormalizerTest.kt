/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberNormalizerTest {
    @Test
    fun `already-international numbers are kept`() {
        assertThat(PhoneNumberNormalizer.normalize("+55 11 91234-5678", defaultDialCode = "1"))
            .isEqualTo("+5511912345678")
    }

    @Test
    fun `00 prefix becomes a plus`() {
        assertThat(PhoneNumberNormalizer.normalize("0055 11 91234 5678", defaultDialCode = "1"))
            .isEqualTo("+5511912345678")
    }

    @Test
    fun `national number gets the device dial code and drops the trunk zero`() {
        // Brazilian national format with a trunk 0.
        assertThat(PhoneNumberNormalizer.normalize("011 91234-5678", defaultDialCode = "55"))
            .isEqualTo("+5511912345678")
    }

    @Test
    fun `us national 10-digit number gets +1`() {
        assertThat(PhoneNumberNormalizer.normalize("(555) 123-4567", defaultDialCode = "1"))
            .isEqualTo("+15551234567")
    }

    @Test
    fun `us number already prefixed with 1 is treated as international`() {
        assertThat(PhoneNumberNormalizer.normalize("15551234567", defaultDialCode = "1"))
            .isEqualTo("+15551234567")
    }

    @Test
    fun `blank or junk input returns null`() {
        assertThat(PhoneNumberNormalizer.normalize("", defaultDialCode = "1")).isNull()
        assertThat(PhoneNumberNormalizer.normalize("12", defaultDialCode = "1")).isNull()
    }
}
