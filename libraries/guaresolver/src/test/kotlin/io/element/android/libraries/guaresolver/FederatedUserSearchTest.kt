/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FederatedUserSearchTest {
    // region Bare handle detection

    @Test
    fun `bare handle accepts a plain username`() {
        assertThat(FederatedUserSearch.bareHandle("ana-souza")).isEqualTo("ana-souza")
    }

    @Test
    fun `bare handle strips the sigil and lowercases`() {
        assertThat(FederatedUserSearch.bareHandle("@Ana-Souza")).isEqualTo("ana-souza")
    }

    @Test
    fun `bare handle trims whitespace`() {
        assertThat(FederatedUserSearch.bareHandle("  ana.souza_2\n")).isEqualTo("ana.souza_2")
    }

    @Test
    fun `bare handle accepts the full localpart charset`() {
        assertThat(FederatedUserSearch.bareHandle("a-b.c_d=e/f0")).isEqualTo("a-b.c_d=e/f0")
    }

    @Test
    fun `bare handle rejects short queries`() {
        assertThat(FederatedUserSearch.bareHandle("ab")).isNull()
        assertThat(FederatedUserSearch.bareHandle("@ab")).isNull()
        assertThat(FederatedUserSearch.bareHandle("")).isNull()
    }

    @Test
    fun `bare handle rejects full addresses`() {
        assertThat(FederatedUserSearch.bareHandle("@ana-souza:gua.example")).isNull()
        assertThat(FederatedUserSearch.bareHandle("ana-souza:gua.example")).isNull()
        assertThat(FederatedUserSearch.bareHandle("@ana-souza:")).isNull()
    }

    @Test
    fun `bare handle rejects invalid characters`() {
        assertThat(FederatedUserSearch.bareHandle("ana souza")).isNull()
        assertThat(FederatedUserSearch.bareHandle("ana!")).isNull()
        assertThat(FederatedUserSearch.bareHandle("an@a")).isNull()
        assertThat(FederatedUserSearch.bareHandle("año-souza")).isNull()
    }

    // endregion

    // region Search visibility

    @Test
    fun `absent visibility is global`() {
        assertThat(RosterSearchVisibility.parse(null)).isEqualTo(RosterSearchVisibility.Global)
    }

    @Test
    fun `known visibility values are parsed`() {
        assertThat(RosterSearchVisibility.parse("global")).isEqualTo(RosterSearchVisibility.Global)
        assertThat(RosterSearchVisibility.parse("group")).isEqualTo(RosterSearchVisibility.Group)
        assertThat(RosterSearchVisibility.parse("server")).isEqualTo(RosterSearchVisibility.Server)
    }

    @Test
    fun `visibility matching is case insensitive`() {
        // The resolver serializes the policy uppercase, like the entry status.
        assertThat(RosterSearchVisibility.parse("GLOBAL")).isEqualTo(RosterSearchVisibility.Global)
        assertThat(RosterSearchVisibility.parse("GROUP")).isEqualTo(RosterSearchVisibility.Group)
        assertThat(RosterSearchVisibility.parse("SERVER")).isEqualTo(RosterSearchVisibility.Server)
    }

    @Test
    fun `unrecognized visibility is preserved`() {
        assertThat(RosterSearchVisibility.parse("invite")).isEqualTo(RosterSearchVisibility.Unrecognized("invite"))
    }

    // endregion

    // region Candidate construction

    @Test
    fun `candidates lead with the own server and follow roster order`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "br.gua.example"),
                aFederationRosterEntry(serverName = "ca.gua.example", searchVisibility = "global"),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example", "@ana-souza:ca.gua.example")
    }

    @Test
    fun `candidates skip inactive entries`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "ca.gua.example", status = "ACTIVE"),
                aFederationRosterEntry(serverName = "old.gua.example", status = "RETIRED"),
                aFederationRosterEntry(serverName = "new.gua.example", status = "PENDING"),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example", "@ana-souza:ca.gua.example")
    }

    @Test
    fun `candidates skip server visibility`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "ca.gua.example", searchVisibility = "GLOBAL", searchGroups = emptyList()),
                aFederationRosterEntry(serverName = "private.gua.example", searchVisibility = "SERVER"),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example", "@ana-souza:ca.gua.example")
    }

    @Test
    fun `candidates skip unrecognized visibility`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "beta.gua.example", searchVisibility = "invite"),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example")
    }

    @Test
    fun `group visibility requires a shared group`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "br.gua.example", searchGroups = listOf("edu", "partners")),
                aFederationRosterEntry(serverName = "edu.gua.example", searchVisibility = "group", searchGroups = listOf("edu")),
                aFederationRosterEntry(serverName = "gov.gua.example", searchVisibility = "group", searchGroups = listOf("gov")),
                aFederationRosterEntry(serverName = "closed.gua.example", searchVisibility = "group"),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example", "@ana-souza:edu.gua.example")
    }

    @Test
    fun `group visibility excludes a searcher without groups`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "br.gua.example"),
                aFederationRosterEntry(serverName = "edu.gua.example", searchVisibility = "group", searchGroups = listOf("edu")),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "br.gua.example")

        assertThat(candidates).containsExactly("@ana-souza:br.gua.example")
    }

    @Test
    fun `group visibility excludes a searcher absent from the roster`() {
        val roster = aFederationRoster(
            entries = listOf(
                aFederationRosterEntry(serverName = "ca.gua.example"),
                aFederationRosterEntry(serverName = "edu.gua.example", searchVisibility = "group", searchGroups = listOf("edu")),
            )
        )

        val candidates = FederatedUserSearch.candidates(handle = "ana-souza", roster = roster, ownServerName = "nowhere.example")

        assertThat(candidates).containsExactly("@ana-souza:nowhere.example", "@ana-souza:ca.gua.example")
    }

    // endregion
}
