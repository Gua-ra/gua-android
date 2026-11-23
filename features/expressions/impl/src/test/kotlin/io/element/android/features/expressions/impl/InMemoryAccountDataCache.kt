/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

class InMemoryAccountDataCache(
    initial: Map<String, String> = emptyMap(),
) : AccountDataCache {
    val entries = initial.toMutableMap()

    override suspend fun load(eventType: String): String? = entries[eventType]

    override suspend fun save(eventType: String, content: String) {
        entries[eventType] = content
    }
}
