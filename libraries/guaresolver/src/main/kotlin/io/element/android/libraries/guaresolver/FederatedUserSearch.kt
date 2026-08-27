/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: how a federation homeserver lets its users be found by bare-handle search from other
 * servers. An absent value means [Global] — the default for roster entries that predate the policy.
 * Values this client doesn't recognize are treated as **not** discoverable, so a stricter policy
 * introduced server-side is never widened by an older client. Android counterpart of iOS
 * `RosterSearchVisibility`.
 */
sealed interface RosterSearchVisibility {
    /** Discoverable from every federation server. */
    data object Global : RosterSearchVisibility

    /** Discoverable only from servers sharing at least one search group. */
    data object Group : RosterSearchVisibility

    /** Discoverable only from the user's own server, i.e. never via federated search. */
    data object Server : RosterSearchVisibility

    data class Unrecognized(val rawValue: String) : RosterSearchVisibility

    companion object {
        /**
         * The resolver serializes the policy like the entry status, i.e. uppercase (`GLOBAL`);
         * match case-insensitively so either casing works.
         */
        fun parse(rawValue: String?): RosterSearchVisibility = when (rawValue?.lowercase()) {
            null, "global" -> Global
            "group" -> Group
            "server" -> Server
            else -> Unrecognized(rawValue)
        }
    }
}

/**
 * GUA FORK: pure logic for Gua's federated bare-username search: when someone types a handle with
 * no homeserver (`ana-souza`), the client fans out an exact-match lookup to the other federation
 * servers from the resolver roster, honouring each server's discoverability policy. Android
 * counterpart of iOS `FederatedUserSearch`.
 */
object FederatedUserSearch {
    private val BARE_HANDLE_REGEX = Regex("^[a-z0-9._=\\-/]{3,}$")

    /**
     * Normalizes a search query into a bare handle, or `null` when the query isn't one.
     * A bare handle is an optional leading `@` followed by at least 3 localpart characters —
     * and crucially no `:`, otherwise the user is already typing a full address.
     */
    fun bareHandle(query: String): String? {
        var handle = query.trim().lowercase()
        if (handle.contains(':')) return null
        handle = handle.removePrefix("@")
        return handle.takeIf { BARE_HANDLE_REGEX.matches(it) }
    }

    /**
     * The full user IDs to look up for a bare handle: one per ACTIVE roster server that allows
     * discovery from the searcher's own homeserver, in roster order. The searcher's own server
     * is skipped — local search already covers it.
     */
    fun candidates(handle: String, roster: FederationRoster, ownServerName: String): List<String> {
        val ownGroups = roster.entries
            .firstOrNull { it.homeserver.serverName == ownServerName }
            ?.homeserver
            ?.searchGroups
            .orEmpty()
            .toSet()

        return roster.entries
            .filter { entry ->
                entry.isActive &&
                    entry.homeserver.serverName != ownServerName &&
                    when (RosterSearchVisibility.parse(entry.homeserver.searchVisibility)) {
                        RosterSearchVisibility.Global -> true
                        RosterSearchVisibility.Group -> entry.homeserver.searchGroups.orEmpty().any { it in ownGroups }
                        RosterSearchVisibility.Server,
                        is RosterSearchVisibility.Unrecognized -> false
                    }
            }
            .map { "@$handle:${it.homeserver.serverName}" }
    }
}
