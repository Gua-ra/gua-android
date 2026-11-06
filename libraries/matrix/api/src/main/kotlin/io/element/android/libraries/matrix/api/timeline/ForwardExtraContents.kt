/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline

// Asked once per chat forwarded to: one JSON object per forwarded event, whose fields are added at the top level of its content.
fun interface ForwardExtraContents {
    fun forChat(): List<String>
}
