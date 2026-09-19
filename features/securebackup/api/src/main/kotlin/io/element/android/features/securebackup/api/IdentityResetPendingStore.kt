/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.api

/**
 * GUA FORK: remembers that an identity reset was started for this account and has not landed on
 * the server.
 *
 * Starting a reset is destructive before anything is approved: the SDK deletes the key backup,
 * disables secret storage and mints a brand new local identity, all before the user has seen the
 * approval page. If the approval never happens, that identity exists only on this device. The
 * setup banner's ordinary repair path would then export it into fresh key storage and report
 * success for an identity the server has never accepted. While this is set, that path must refuse
 * and ask for the reset to be finished instead.
 *
 * Persisted per account, so it survives the app being killed between the reset starting and the
 * approval coming back.
 */
interface IdentityResetPendingStore {
    fun isPending(): Boolean
    fun markPending()
    fun clear()
}
