/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import org.junit.Test
import java.time.format.DateTimeParseException

class InstantsTest {
    @Test
    fun `only the resolver's timestamp form is read, though Instant parse takes more`() {
        val refused = listOf(
            "2026-10-01t12:00:00Z",
            "2026-10-01T12:00:00z",
            "2026-10-01T24:00:00Z",
            "2026-12-31T23:59:60Z",
            "2026-10-01T12:00Z",
            "2026-10-01T12:00:00+00:00",
        )
        for (value in refused) {
            assertWithMessage(value).that(runCatchingExceptions { value.toInstant() }.exceptionOrNull()).isInstanceOf(DateTimeParseException::class.java)
        }
        assertThat("+10000-01-01T00:00:00.5Z".toInstant().toString()).isEqualTo("+10000-01-01T00:00:00.500Z")
    }
}
