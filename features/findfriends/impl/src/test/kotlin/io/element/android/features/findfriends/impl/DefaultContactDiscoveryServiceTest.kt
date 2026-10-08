/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.guaresolver.ContactMatch
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultContactDiscoveryServiceTest {
    @Test
    fun `address-book numbers are sent as E164 phones in sorted batches of 1000`() = runTest {
        val numbers = (2_499 downTo 0).map { "+1555" + it.toString().padStart(7, '0') }
        val identityServiceClient = FakeIdentityServiceClient()
        val service = createService(
            contacts = numbers.associateWith { "Contact $it" },
            identityServiceClient = identityServiceClient,
        )

        service.discover()

        assertThat(identityServiceClient.lookupCalls.map { it.phones.size }).containsExactly(1000, 1000, 500).inOrder()
        assertThat(identityServiceClient.lookupCalls.flatMap { it.phones }).isEqualTo(numbers.sorted())
        assertThat(identityServiceClient.lookupCalls.map { it.accessToken }.toSet()).containsExactly(AN_ACCESS_TOKEN)
    }

    @Test
    fun `matches are labelled with the address-book name for the echoed number`() = runTest {
        val identityServiceClient = FakeIdentityServiceClient(
            lookupContactsResult = {
                Result.success(
                    listOf(
                        aContactMatch(phoneNumber = "+15555550102", userId = "@zoe:gua.global", displayHandle = "@zoe"),
                        aContactMatch(phoneNumber = "+15555550101", userId = "@bob:gua.global", displayHandle = "@bob"),
                        aContactMatch(phoneNumber = "+15555550103", userId = "@bob:gua.global", displayHandle = "@bob"),
                    )
                )
            },
        )
        val service = createService(
            contacts = mapOf(
                "+15555550101" to "Bob",
                "+15555550102" to "Aunt Zoe",
                "+15555550103" to "Bob work",
            ),
            identityServiceClient = identityServiceClient,
        )

        val result = service.discover() as ContactDiscoveryResult.Success

        assertThat(result.contacts).containsExactly(
            DiscoveredContact(localName = "Aunt Zoe", userId = UserId("@zoe:gua.global"), handle = "@zoe", avatarUrl = null),
            DiscoveredContact(localName = "Bob", userId = UserId("@bob:gua.global"), handle = "@bob", avatarUrl = null),
        ).inOrder()
    }

    @Test
    fun `a failed batch fails the whole discovery`() = runTest {
        var calls = 0
        val identityServiceClient = FakeIdentityServiceClient(
            lookupContactsResult = {
                calls++
                if (calls == 2) Result.failure(ResolverError.Server(400)) else Result.success(emptyList())
            },
        )
        val service = createService(
            contacts = (0 until 1_500).associate { "+1555" + it.toString().padStart(7, '0') to "Contact $it" },
            identityServiceClient = identityServiceClient,
        )

        assertThat(service.discover()).isEqualTo(ContactDiscoveryResult.Failure)
    }

    @Test
    fun `an address book without numbers makes no lookup`() = runTest {
        val identityServiceClient = FakeIdentityServiceClient()
        val service = createService(contacts = emptyMap(), identityServiceClient = identityServiceClient)

        assertThat(service.discover()).isEqualTo(ContactDiscoveryResult.NoContactsWithNumbers)
        assertThat(identityServiceClient.lookupCalls).isEmpty()
    }

    @Test
    fun `a missing session makes no lookup`() = runTest {
        val identityServiceClient = FakeIdentityServiceClient()
        val service = createService(
            contacts = mapOf("+15555550101" to "Bob"),
            identityServiceClient = identityServiceClient,
            sessions = emptyList(),
        )

        assertThat(service.discover()).isEqualTo(ContactDiscoveryResult.Failure)
        assertThat(identityServiceClient.lookupCalls).isEmpty()
    }

    private fun TestScope.createService(
        contacts: Map<String, String>,
        identityServiceClient: FakeIdentityServiceClient,
        sessions: List<SessionData> = listOf(aSessionData(accessToken = AN_ACCESS_TOKEN)),
    ) = DefaultContactDiscoveryService(
        contactsReader = object : ContactsReader {
            override fun readContacts(): Map<String, String> = contacts
        },
        identityServiceClient = identityServiceClient,
        matrixClient = FakeMatrixClient(),
        sessionStore = InMemorySessionStore(initialList = sessions),
        dispatchers = testCoroutineDispatchers(),
    )

    private fun aContactMatch(
        phoneNumber: String,
        userId: String,
        displayHandle: String,
    ) = ContactMatch(
        phoneNumber = phoneNumber,
        userId = userId,
        displayHandle = displayHandle,
        displayName = null,
        avatarUrl = null,
    )
}

private const val AN_ACCESS_TOKEN = "anAccessToken"
