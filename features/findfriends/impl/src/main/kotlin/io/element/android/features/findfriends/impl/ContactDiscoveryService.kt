/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.withContext

/**
 * GUA FORK: reads the device address book, looks the numbers up against Gua, and returns the
 * matching contacts. Android counterpart of iOS `ContactDiscoveryService.discover`.
 *
 * PRIVACY (mirrors iOS): the address book is read once and never persisted, and only the normalized
 * numbers are sent. Contact names stay on the device and label the matches locally.
 */
sealed interface ContactDiscoveryResult {
    data class Success(val contacts: List<DiscoveredContact>) : ContactDiscoveryResult
    data object NoContactsWithNumbers : ContactDiscoveryResult
    data object Failure : ContactDiscoveryResult
}

interface ContactDiscoveryService {
    suspend fun discover(): ContactDiscoveryResult
}

@ContributesBinding(SessionScope::class)
class DefaultContactDiscoveryService(
    private val contactsReader: ContactsReader,
    private val identityServiceClient: IdentityServiceClient,
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val dispatchers: CoroutineDispatchers,
) : ContactDiscoveryService {
    /** Identity-service caps the batch; stay under it (mirrors iOS' 1000). */
    private val maxNumbersPerRequest = 1000

    override suspend fun discover(): ContactDiscoveryResult {
        val nameByNumber = withContext(dispatchers.io) { contactsReader.readContacts() }
        if (nameByNumber.isEmpty()) return ContactDiscoveryResult.NoContactsWithNumbers

        val accessToken = sessionStore.getSession(matrixClient.sessionId.value)?.accessToken
            ?: return ContactDiscoveryResult.Failure

        val matches = nameByNumber.keys.sorted().chunked(maxNumbersPerRequest).flatMap { batch ->
            val result = identityServiceClient.lookupContacts(accessToken = accessToken, phones = batch)
            result.getOrElse { return ContactDiscoveryResult.Failure }
        }

        val ownUserId = matrixClient.sessionId.value
        val contacts = matches
            .filterNot { it.userId.equals(ownUserId, ignoreCase = true) }
            .map { match ->
                DiscoveredContact(
                    localName = nameByNumber[match.phoneNumber]
                        ?: match.displayName
                        ?: match.displayHandle,
                    userId = UserId(match.userId),
                    handle = match.displayHandle,
                    avatarUrl = match.avatarUrl,
                )
            }
            .distinctBy { it.userId }
            .sortedBy { it.localName.lowercase() }

        return ContactDiscoveryResult.Success(contacts)
    }
}
