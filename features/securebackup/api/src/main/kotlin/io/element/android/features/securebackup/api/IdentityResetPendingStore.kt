/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

/**
 * Remembers that an identity reset was started for this account and has not landed on the server.
 *
 * Starting a reset is destructive before anything is approved: the SDK deletes the key backup,
 * disables secret storage and mints a new local identity before the user has seen the approval page.
 * If the approval never happens, that identity exists only on this device. While this is set, the
 * setup banner's repair path must refuse and ask for the reset to be finished instead.
 *
 * Persisted per account, so it survives the app being killed between the reset starting and the
 * approval coming back.
 */
interface IdentityResetPendingStore {
    fun isPending(): Boolean
    fun markPending()
    fun clear()
}
