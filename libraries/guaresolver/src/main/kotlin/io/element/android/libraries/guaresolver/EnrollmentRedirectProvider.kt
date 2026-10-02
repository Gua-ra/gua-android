/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * Where a factor-enrollment ceremony returns to once the IdP is finished with it.
 *
 * `POST /security/{pin,passkey}/enroll/start` accepts an optional `redirectUri`, checked against the
 * deployment's allowlist. It must be the custom scheme this build's OAuth intent filter claims
 * (`global.gua`, `global.gua.dev` for the QA app, `global.gua.debug` for a debug build). Without one
 * the server uses its single configured default, which is production's scheme.
 */
interface EnrollmentRedirectProvider {
    /** Null when this build cannot say. The server then uses its configured default. */
    fun provide(): String?
}
