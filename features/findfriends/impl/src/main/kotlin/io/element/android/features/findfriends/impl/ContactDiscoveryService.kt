/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.PhoneHasher
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.withContext

/**
 * GUA FORK: reads the device address book, protects the numbers, looks them up against Gua, and
 * returns the matching contacts. Android counterpart of iOS `ContactDiscoveryService.discover`.
 *
 * PRIVACY (mirrors iOS):
 * - The address book is read once and never persisted.
 * - Raw phone numbers never leave the device — they are hashed via [PhoneHasher] before the lookup.
 * - The hashes the device produced are mapped back to local names so matches are labelled with how
 *   the user actually knows the person; the server only ever sees the hashes.
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

        // Map hashed digest -> best local name so matches can be labelled locally without the server
        // ever seeing the raw number.
        val nameByHash = nameByNumber.entries.mapNotNull { (e164, name) ->
            PhoneHasher.hash(e164)?.let { it to name }
        }.toMap()
        if (nameByHash.isEmpty()) return ContactDiscoveryResult.NoContactsWithNumbers

        val matches = nameByHash.keys.chunked(maxNumbersPerRequest).flatMap { batch ->
            val result = identityServiceClient.lookupContacts(accessToken = accessToken, hashedPhones = batch)
            result.getOrElse { return ContactDiscoveryResult.Failure }
        }

        val contacts = matches
            .map { match ->
                DiscoveredContact(
                    localName = nameByHash[match.hashedPhone]
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
