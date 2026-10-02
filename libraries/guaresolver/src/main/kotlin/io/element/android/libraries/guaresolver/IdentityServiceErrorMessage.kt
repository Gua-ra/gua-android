/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

import androidx.annotation.StringRes
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * GUA FORK: the message for an identity-service failure a screen has no wording of its own for.
 *
 * Screens branch on the failures they can actually do something about and then fall through to here.
 * It exists so the one failure every identity-service call shares, a session that needed refreshing,
 * is worded the same everywhere and never lands on the generic error again: that message told the
 * user nothing, least of all that repeating the action would work.
 */
@StringRes
fun Throwable.identityServiceMessage(): Int = when (this) {
    is ResolverError.SessionRefreshNeeded -> CommonStrings.gua_error_session_refresh_needed
    else -> CommonStrings.error_unknown
}
