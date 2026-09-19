/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.oauth.api

sealed interface OAuthAction {
    data class GoBack(val toUnblock: Boolean = false) : OAuthAction
    data class Success(val url: String) : OAuthAction

    /**
     * GUA FORK: the identity-reset approval page handed control back to the app after the user
     * approved. Only the reset flow acts on it; login ignores it.
     */
    data object IdentityResetApproved : OAuthAction
}
