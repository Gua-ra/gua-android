/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

class FakeContactDiscoveryService(
    private val discoverResult: () -> ContactDiscoveryResult = {
        ContactDiscoveryResult.Success(emptyList())
    },
) : ContactDiscoveryService {
    var discoverCallCount = 0
        private set

    override suspend fun discover(): ContactDiscoveryResult {
        discoverCallCount++
        return discoverResult()
    }
}
