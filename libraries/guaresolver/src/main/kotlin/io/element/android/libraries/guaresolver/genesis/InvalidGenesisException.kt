/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: a value that is not a well-formed account object (ADM-008 decision 1 and 2).
 *
 * [reason] is the machine-readable rule that refused it, and it is the same vocabulary the published
 * golden vectors use in their `rejections` block, so a vector's stated reason and this implementation's
 * reason can be compared directly.
 */
class InvalidGenesisException(
    val reason: String,
    val detail: String,
) : Exception("$reason: $detail")
