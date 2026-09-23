/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.linknewdevice

/**
 * GUA FORK: there is no `validate`, and its absence is the point.
 *
 * This interface used to promise a repeatable local check of the code, and the Rust implementation of that
 * promise returned true without asking the SDK anything. The check code IS enforced, one layer down: the
 * secure channel compares it when the ceremony confirms, and a wrong one fails the whole ceremony with
 * `InvalidCheckCode`. What the pinned SDK has no API for is a non-destructive pre-check, and upstream closed
 * the pull request that would have added one.
 *
 * So the method is gone rather than reworded. A contract that promises a verdict and returns a constant
 * invites the next caller to trust the boolean, and on this fork that caller would be the one deciding whether
 * to offer a device authority grant (ADM-009 decision 5). The cost of having no pre-check is stated where it
 * lands: a typo and an attack are indistinguishable to the user, and both cost a fresh QR.
 */
interface CheckCodeSender {
    /**
     * Sends the given [code] into the secure channel, which is where it is compared.
     * This method can be called only once.
     */
    suspend fun send(code: UByte): Result<Unit>
}
