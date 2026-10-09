/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import androidx.annotation.StringRes
import io.element.android.libraries.ui.strings.CommonStrings

/** GUA FORK: the message for an identity-service failure that a screen has no specific wording for. */
@StringRes
fun Throwable.identityServiceMessage(): Int = when (this) {
    is ResolverError.AccessTokenRefused -> CommonStrings.gua_error_sign_in_not_confirmed
    else -> CommonStrings.error_unknown
}
