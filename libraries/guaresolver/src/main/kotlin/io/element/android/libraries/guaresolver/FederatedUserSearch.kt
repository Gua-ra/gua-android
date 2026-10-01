/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/** Unrecognized values are treated as not discoverable, so an older client never widens a stricter policy. */
sealed interface RosterSearchVisibility {
    data object Global : RosterSearchVisibility

    data object Group : RosterSearchVisibility

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

object FederatedUserSearch {
    private val BARE_HANDLE_REGEX = Regex("^[a-z0-9._=\\-/]{3,}$")

    /** A bare handle is an optional `@`, at least 3 localpart characters and no `:`. */
    fun bareHandle(query: String): String? {
        var handle = query.trim().lowercase()
        if (handle.contains(':')) return null
        handle = handle.removePrefix("@")
        return handle.takeIf { BARE_HANDLE_REGEX.matches(it) }
    }

    /** The searcher's own server first, then each ACTIVE roster server that allows discovery from it, in roster order. */
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
