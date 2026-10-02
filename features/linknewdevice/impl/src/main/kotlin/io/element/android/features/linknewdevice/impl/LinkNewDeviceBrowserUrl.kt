/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl

import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.libraries.androidutils.browser.withMxidLoginHint
import io.element.android.libraries.matrix.api.core.SessionId

/** Names this account so the approval page refuses a browser session that belongs to someone else. */
internal suspend fun SessionEnterpriseService.linkNewDeviceBrowserUrl(verificationUri: String, sessionId: SessionId): String =
    tweakMasUrl(verificationUri).withMxidLoginHint(sessionId.value)
