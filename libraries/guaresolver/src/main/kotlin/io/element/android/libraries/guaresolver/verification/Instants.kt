/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import java.time.Instant

/**
 * Epoch milliseconds of an ISO-8601 UTC instant with 0 to 9 fractional digits, truncated, which is what
 * every canonical form carries.
 *
 * @throws java.time.format.DateTimeParseException when the string is not such an instant.
 */
internal fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()
