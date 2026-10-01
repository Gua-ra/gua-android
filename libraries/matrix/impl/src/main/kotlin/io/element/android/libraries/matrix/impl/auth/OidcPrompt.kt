/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import io.element.android.libraries.matrix.api.auth.OAuthPrompt
import org.matrix.rustcomponents.sdk.OAuthPrompt as RustOAuthPrompt

internal fun OAuthPrompt.toRustPrompt(): RustOAuthPrompt {
    return when (this) {
        // GUA FORK: prompt=login, so a lingering Custom Tab session is never resumed after sign-out. Keep in sync with the server-side prompt handling.
        OAuthPrompt.Login -> RustOAuthPrompt.Login
        OAuthPrompt.Create -> RustOAuthPrompt.Create
        is OAuthPrompt.Unknown -> RustOAuthPrompt.Unknown(value)
    }
}
