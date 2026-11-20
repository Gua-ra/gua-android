/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.actions

/**
 * The URL grammar of the message actions spec, checked on the raw string.
 * URL parsers disagree with each other, so no parser is used.
 */
internal object MessageActionUrl {
    private const val MAX_LENGTH = 2048
    private const val SCHEME = "https://"
    private const val MAX_PORT = 65_535
    private const val MAX_PORT_DIGITS = 5
    private const val TAIL_SYMBOLS = "-._~:/?#@!$&'()*+,;="
    private val AUTHORITY_ENDS = charArrayOf('/', '?', '#')

    fun isValid(url: String): Boolean {
        if (url.length > MAX_LENGTH || !url.startsWith(SCHEME) || url.count { it == '#' } > 1) return false
        val authorityEnd = url.indexOfAny(AUTHORITY_ENDS, startIndex = SCHEME.length).takeIf { it >= 0 } ?: url.length
        val authority = url.substring(SCHEME.length, authorityEnd)
        val host = authority.substringBefore(':')
        val hasPort = host.length < authority.length
        return isValidHost(host) &&
            (!hasPort || isValidPort(authority.substring(host.length + 1))) &&
            isValidTail(url.substring(authorityEnd))
    }

    private fun isValidHost(host: String): Boolean = host.split('.').all { label ->
        label.isNotEmpty() &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' } &&
            !label.startsWith('-') &&
            !label.endsWith('-')
    }

    private fun isValidPort(port: String): Boolean {
        return port.length in 1..MAX_PORT_DIGITS &&
            port.all { it in '0'..'9' } &&
            port.first() != '0' &&
            port.toInt() <= MAX_PORT
    }

    // The tail starts with one of AUTHORITY_ENDS, which TAIL_SYMBOLS also allows.
    private fun isValidTail(tail: String): Boolean {
        var index = 0
        while (index < tail.length) {
            val char = tail[index]
            index += when {
                char.isAsciiLetterOrDigit() || char in TAIL_SYMBOLS -> 1
                char == '%' && tail.isHexDigitAt(index + 1) && tail.isHexDigitAt(index + 2) -> 3
                else -> return false
            }
        }
        return true
    }

    private fun String.isHexDigitAt(index: Int): Boolean {
        val char = getOrNull(index) ?: return false
        return char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
