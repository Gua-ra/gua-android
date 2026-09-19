/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: where a factor-enrollment ceremony returns to once the IdP is finished with it.
 *
 * `POST /security/{pin,passkey}/enroll/start` accepts an optional `redirectUri`, checked against the
 * deployment's allowlist. Without one the identity service parks every session at its single
 * configured default, which is production's scheme, so a QA or debug build would send the owner to
 * an app they do not have installed and the enrollment would dead-end in the browser.
 *
 * The value has to be the same custom scheme the OAuth intent filter already claims for this build
 * (`global.gua`, `global.gua.dev` for the QA app, `global.gua.debug` for a debug build), which is
 * why it is resolved from the build rather than written down anywhere: the only implementation
 * derives it from the sign-in redirect, and nothing else in the app gets to name one.
 */
interface EnrollmentRedirectProvider {
    /**
     * The redirect to name on an enrollment start, or null when this build cannot say. A null is not
     * an error: the client then names nothing and the server falls back to its own configured
     * default, which is what every build did before the field existed.
     */
    fun provide(): String?
}
