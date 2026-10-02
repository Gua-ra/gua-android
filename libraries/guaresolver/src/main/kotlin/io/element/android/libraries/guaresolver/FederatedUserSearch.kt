/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * How a federation homeserver lets its users be found by bare-handle search from other servers.
 * An absent value means [Global]. Unrecognized values are treated as not discoverable, so an older
 * client never widens a stricter policy.
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
        /** The resolver sends the policy uppercase. Match case-insensitively. */
        fun parse(rawValue: String?): RosterSearchVisibility = when (rawValue?.lowercase()) {
            null, "global" -> Global
            "group" -> Group
            "server" -> Server
            else -> Unrecognized(rawValue)
        }
    }
}

/**
 * Pure logic for federated bare-username search: when someone types a handle with no homeserver
 * (`ana-souza`), the client fans out an exact-match lookup to the federation servers from the
 * resolver roster, honouring each server's discoverability policy.
 */
object FederatedUserSearch {
    private val BARE_HANDLE_REGEX = Regex("^[a-z0-9._=\\-/]{3,}$")

    /**
     * Normalizes a search query into a bare handle, or `null` when the query is not one. A bare handle
     * is an optional `@`, at least 3 localpart characters and no `:`.
     */
    fun bareHandle(query: String): String? {
        var handle = query.trim().lowercase()
        if (handle.contains(':')) return null
        handle = handle.removePrefix("@")
        return handle.takeIf { BARE_HANDLE_REGEX.matches(it) }
    }

    /**
     * The full user IDs to look up for a bare handle: the searcher's own server first, then each ACTIVE
     * roster server that allows discovery from it, in roster order.
     *
     * The own server is included because Synapse's user directory only returns people you already share
     * a room with unless `search_all_users` is on. Duplicates are dropped downstream.
     */
    fun candidates(handle: String, roster: FederationRoster, ownServerName: String): List<String> {
        val ownGroups = roster.entries
            .firstOrNull { it.homeserver.serverName == ownServerName }
            ?.homeserver
            ?.searchGroups
            .orEmpty()
            .toSet()

        val federated = roster.entries
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

        return listOf("@$handle:$ownServerName") + federated
    }
}
