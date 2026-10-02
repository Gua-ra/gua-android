/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * A value that is not a well-formed account object. [reason] is the machine-readable rule that
 * refused it, in the vocabulary of the golden vectors' `rejections` block.
 */
class InvalidGenesisException(
    val reason: String,
    val detail: String,
) : Exception("$reason: $detail")
