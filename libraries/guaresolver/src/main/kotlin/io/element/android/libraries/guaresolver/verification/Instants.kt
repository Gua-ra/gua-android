/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.core.data.tryOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

// Instant.parse alone also takes a signed or longer year, a lowercase t or z, the hour 24 and a leap second.
private val RESOLVER_TIMESTAMP = Regex("""\d{4}-\d{2}-\d{2}T([01]\d|2[0-3]):[0-5]\d:[0-5]\d(\.\d{1,9})?Z""")

/**
 * An instant in the resolver's form: ISO-8601 UTC, four year digits with no sign, an uppercase T and Z, and 0 to 9
 * fractional digits. Every such instant fits in epoch milliseconds.
 *
 * @throws DateTimeParseException when the string is not in that form.
 */
internal fun String.toInstant(): Instant {
    if (!RESOLVER_TIMESTAMP.matches(this)) throw DateTimeParseException("not a resolver timestamp", this, 0)
    return Instant.parse(this)
}

/**
 * Epoch milliseconds, truncated, which is what every canonical form carries.
 *
 * @throws DateTimeParseException when the string is not in the resolver's form.
 */
internal fun String.toEpochMillis(): Long = toInstant().toEpochMilli()

internal fun String.isResolverTimestamp(): Boolean = tryOrNull { toInstant() } != null
