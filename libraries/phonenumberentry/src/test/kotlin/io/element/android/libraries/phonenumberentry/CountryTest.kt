/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.phonenumberentry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CountryTest {
    @Test
    fun `US mask formats local digits`() {
        val us = Country("US", "1")
        assertThat(us.formatNational("5551234567")).isEqualTo("(555) 123-4567")
    }

    @Test
    fun `BR mask formats local digits`() {
        val br = Country("BR", "55")
        assertThat(br.formatNational("11912345678")).isEqualTo("(11) 91234-5678")
    }

    @Test
    fun `Canadian area code flips US to CA`() {
        assertThat(Country.detect("604", Country("US", "1"))?.isoCode).isEqualTo("CA")
    }

    @Test
    fun `non-Canadian area code keeps US`() {
        assertThat(Country.detect("212", Country("US", "1"))).isNull()
    }

    @Test
    fun `parse splits an E164 number into country and local digits`() {
        val (country, local) = Country.parse("+5511912345678")
        assertThat(country.isoCode).isEqualTo("BR")
        assertThat(local).isEqualTo("11912345678")
    }

    @Test
    fun `nationalDigitLength infers length from the curated example`() {
        assertThat(Country("US", "1").nationalDigitLength).isEqualTo(10)
        assertThat(Country("CA", "1").nationalDigitLength).isEqualTo(10)
        assertThat(Country("BR", "55").nationalDigitLength).isEqualTo(11)
    }

    @Test
    fun `nationalDigitLength is null with no curated example`() {
        // ZW has a dial code but no nationalExamples entry, so it stays conservative.
        assertThat(Country("ZW", "263").nationalDigitLength).isNull()
    }

    @Test
    fun `normalize - explicit plus with country code switches country and strips it`() {
        val (country, local) = Country.normalize("+15551234567", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("US")
        assertThat(local).isEqualTo("5551234567")
    }

    @Test
    fun `normalize - formatted plus number is stripped of formatting and country code`() {
        val (country, local) = Country.normalize("+1 (555) 123-4567", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("US")
        assertThat(local).isEqualTo("5551234567")
    }

    @Test
    fun `normalize - plus Brazil number switches to Brazil`() {
        val (country, local) = Country.normalize("+5511912345678", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("BR")
        assertThat(local).isEqualTo("11912345678")
    }

    @Test
    fun `normalize - redundant dial code without plus is stripped`() {
        val (country, local) = Country.normalize("15551234567", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("US")
        assertThat(local).isEqualTo("5551234567")
    }

    @Test
    fun `normalize - normal local number is left unchanged`() {
        val (country, local) = Country.normalize("5551234567", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("US")
        assertThat(local).isEqualTo("5551234567")
    }

    @Test
    fun `normalize - coincidental dial-code prefix is not stripped`() {
        // "11234567890": leading "1" matches +1, remainder "1234567890" is exactly 10 digits, but it
        // itself starts with the dial code, so the safety guard keeps it intact (no false strip).
        val (country, local) = Country.normalize("11234567890", Country("US", "1"))
        assertThat(country.isoCode).isEqualTo("US")
        assertThat(local).isEqualTo("11234567890")
    }
}
