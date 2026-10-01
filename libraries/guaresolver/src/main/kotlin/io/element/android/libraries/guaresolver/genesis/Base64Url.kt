/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package io.element.android.libraries.guaresolver.genesis

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Base64url without padding. Uses the Kotlin stdlib: `java.util.Base64` needs API 26 and `android.util.Base64` is absent from unit tests. */
internal object Base64Url {
    fun encode(value: ByteArray): String = Base64.UrlSafe.encode(value).trimEnd('=')

    fun decode(value: String): ByteArray {
        val padding = (4 - value.length % 4) % 4
        return Base64.UrlSafe.decode(value + "=".repeat(padding))
    }
}
