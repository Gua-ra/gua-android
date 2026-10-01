/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

/** The reserved OIDC `login_hint` grammar `gua:phone=<E.164>;genesis=<handle>`. The identity service parses it strictly. */
object GuaLoginHint {
    const val PREFIX = "gua:"

    /** 32 bytes as base64url without padding. */
    private val attachHandle = Regex("^[A-Za-z0-9_-]{16,128}$")

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
