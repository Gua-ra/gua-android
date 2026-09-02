/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.securebackup.api.IdentityResetPendingStore

class FakeIdentityResetPendingStore(
    private var pending: Boolean = false,
) : IdentityResetPendingStore {
    override fun isPending(): Boolean = pending

    override fun markPending() {
        pending = true
    }

    override fun clear() {
        pending = false
    }
}
