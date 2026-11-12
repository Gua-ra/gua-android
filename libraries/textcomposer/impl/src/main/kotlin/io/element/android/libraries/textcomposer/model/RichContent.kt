/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.model

import android.net.Uri

/** Content a keyboard inserted or the user pasted into the composer. */
data class RichContent(
    val uri: Uri,
    val mimeType: String?,
    val isFromInputMethod: Boolean,
)
