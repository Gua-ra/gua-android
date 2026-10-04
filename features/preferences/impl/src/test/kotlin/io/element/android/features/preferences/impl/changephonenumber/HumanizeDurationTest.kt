/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class HumanizeDurationTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "en")
    fun `keeps the two largest units and drops minutes once there are days`() {
        assertThat(format(SIX_DAYS_THREE_HOURS)).isEqualTo("6 days and 3 hours")
        assertThat(format(3_600 + 60)).isEqualTo("1 hour and 1 minute")
        assertThat(format(86_400 + 120)).isEqualTo("1 day")
        assertThat(format(59)).isEqualTo("a moment")
        assertThat(format(0)).isEqualTo("a moment")
    }

    @Test
    @Config(qualifiers = "pt-rBR")
    fun `Brazilian Portuguese uses Portuguese units`() {
        assertThat(format(SIX_DAYS_THREE_HOURS)).isEqualTo("6 dias e 3 horas")
        assertThat(format(3_600)).isEqualTo("1 hora")
        assertThat(format(120)).isEqualTo("2 minutos")
        assertThat(format(0)).isEqualTo("alguns instantes")
    }

    @Test
    @Config(qualifiers = "es")
    fun `Spanish uses Spanish units`() {
        assertThat(format(86_400 + 5 * 3_600)).isEqualTo("1 día y 5 horas")
        assertThat(format(0)).isEqualTo("unos instantes")
    }

    @Test
    @Config(qualifiers = "fr")
    fun `French uses French units`() {
        assertThat(format(2 * 86_400 + 3_600)).isEqualTo("2 jours et 1 heure")
        assertThat(format(0)).isEqualTo("quelques instants")
    }

    private fun format(seconds: Long) = humanizeDuration(RuntimeEnvironment.getApplication().resources, seconds)

    private companion object {
        const val SIX_DAYS_THREE_HOURS = 6L * 86_400 + 3 * 3_600 + 59
    }
}
