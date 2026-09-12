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

/**
 * GUA FORK: base64url without padding, which is how identity-service carries the genesis bytes, the
 * registration proof, the attach challenge and the attach proof.
 *
 * The Kotlin stdlib encoder, not `android.util.Base64` and not `java.util.Base64`: the first is absent
 * from a plain unit-test JVM and the second needs API 26 while this module builds down to 24. The same
 * encoder already backs `EncryptionResult`. Padding is trimmed and restored here rather than through the
 * experimental padding option, so nothing depends on an API that is still moving.
 */
internal object Base64Url {
    fun encode(value: ByteArray): String = Base64.UrlSafe.encode(value).trimEnd('=')

    fun decode(value: String): ByteArray {
        val padding = (4 - value.length % 4) % 4
        return Base64.UrlSafe.decode(value + "=".repeat(padding))
    }
}
