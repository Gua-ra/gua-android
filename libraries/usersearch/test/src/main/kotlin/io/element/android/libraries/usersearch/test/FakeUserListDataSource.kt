/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.test

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.usersearch.api.UserListDataSource

class FakeUserListDataSource : UserListDataSource {
    private var searchResult: List<MatrixUser> = emptyList()
    private var profile: MatrixUser? = null
    private var profileLookup: (suspend (UserId) -> MatrixUser?)? = null

    val getProfileCalls = mutableListOf<UserId>()

    override suspend fun search(query: String, count: Long): List<MatrixUser> = searchResult.take(count.toInt())

    override suspend fun getProfile(userId: UserId): MatrixUser? {
        getProfileCalls.add(userId)
        return profileLookup?.invoke(userId) ?: profile
    }

    fun givenSearchResult(users: List<MatrixUser>) {
        this.searchResult = users
    }

    fun givenUserProfile(matrixUser: MatrixUser?) {
        this.profile = matrixUser
    }

    // GUA FORK: per-user profile lookups for the federated fan-out tests.
    fun givenProfileLookup(lookup: suspend (UserId) -> MatrixUser?) {
        this.profileLookup = lookup
    }
}
