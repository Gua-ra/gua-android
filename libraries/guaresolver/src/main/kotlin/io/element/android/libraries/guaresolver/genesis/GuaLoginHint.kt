/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * GUA FORK: the reserved OIDC `login_hint` grammar ADM-008 decision 6 defines,
 * `gua:phone=<E.164>;genesis=<handle>`.
 *
 * MAS forwards the hint verbatim, and identity-service parses it strictly: an unparsable hint, an
 * unknown or duplicated key and a malformed `genesis` value are refused rather than ignored, because
 * quietly dropping a handle is the silent downgrade the decision forbids. This builder therefore
 * produces only shapes that parser accepts, and refuses to build one it would reject.
 *
 * The bare value `passkey` is reserved and already deployed for passkey-first entry; the `gua:` grammar
 * applies only to prefixed hints, so the two never collide.
 */
object GuaLoginHint {
    const val PREFIX = "gua:"

    /**
     * The attach handle alphabet. identity-service issues 32 CSPRNG bytes as base64url without padding
     * and validates the value it receives against the same shape, so anything else is refused before it
     * reaches a session.
     */
    private val attachHandle = Regex("^[A-Za-z0-9_-]{16,128}$")

    /**
     * Builds the hint for a phone-first signup or sign-in.
     *
     * @param e164Phone the number the user entered, in E.164.
     * @param attachHandle the single-use handle `POST /account/genesis` returned, or null to send
     * today's bare phone hint unchanged. A signup that presents no handle takes the bootstrap branch,
     * which ADM-008 decision 6 states is not a failure.
     */
    fun forPhone(e164Phone: String, attachHandle: String?): String {
        if (attachHandle == null) {
            // Byte-identical to what the client sent before account genesis existed.
            return e164Phone
        }
        require(isValidAttachHandle(attachHandle)) { "the attach handle is not a well-formed value" }
        require(e164Phone.startsWith("+") && !e164Phone.contains(';') && !e164Phone.contains('=')) {
            "the phone must be a bare E.164 number"
        }
        return "$PREFIX" + "phone=$e164Phone;genesis=$attachHandle"
    }

    /** True when [value] is a well-formed attach handle, which is the only thing worth sending. */
    fun isValidAttachHandle(value: String): Boolean = attachHandle.matches(value)
}
