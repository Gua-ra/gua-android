/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

/** An identity reset was started and has not landed on the server. While set, the repair path must refuse and ask for the reset to be finished. */
interface IdentityResetPendingStore {
    fun isPending(): Boolean
    fun markPending()
    fun clear()
}
