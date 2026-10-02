/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/**
 * The reserved OIDC `login_hint` grammar `gua:phone=<E.164>;genesis=<handle>`.
 *
 * MAS forwards the hint verbatim and the identity service parses it strictly: an unparsable hint, an
 * unknown or duplicated key and a malformed `genesis` value are refused rather than ignored. This
 * builder produces only shapes that parser accepts.
 *
 * The bare value `passkey` is reserved for passkey-first entry. The `gua:` grammar applies only to
 * prefixed hints, so the two never collide.
 */
object GuaLoginHint {
    const val PREFIX = "gua:"

    /**
     * The attach handle alphabet. The identity service issues 32 CSPRNG bytes as base64url without
     * padding and validates the value it receives against the same shape.
     */
    private val attachHandle = Regex("^[A-Za-z0-9_-]{16,128}$")

    /**
     * Builds the hint for a phone-first signup or sign-in.
     *
     * @param e164Phone the number the user entered, in E.164.
     * @param attachHandle the single-use handle `POST /account/genesis` returned, or null to send the
     * bare phone hint. A signup that presents no handle takes the bootstrap branch, which is not a failure.
     */
    fun forPhone(e164Phone: String, attachHandle: String?): String {
        if (attachHandle == null) {
            return e164Phone
        }
        require(isValidAttachHandle(attachHandle)) { "the attach handle is not a well-formed value" }
        require(e164Phone.startsWith("+") && !e164Phone.contains(';') && !e164Phone.contains('=')) {
            "the phone must be a bare E.164 number"
        }
        return "$PREFIX" + "phone=$e164Phone;genesis=$attachHandle"
    }

    fun isValidAttachHandle(value: String): Boolean = attachHandle.matches(value)
}
