/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.gif

sealed interface GifProxy {
    data object None : GifProxy

    data class Tunnel(val host: String, val port: Int) : GifProxy

    /** A proxy that is configured but unreadable blocks GIFs rather than letting requests go direct. */
    data object Invalid : GifProxy

    companion object {
        const val DEFAULT_PORT = 443

        // Groups: a bracketed IPv6 address, else a host name or IPv4 address, then an optional port.
        private val hostAndPort = Regex("""^(?:\[([0-9A-Fa-f:.]+)]|([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?))(?::([0-9]{1,5}))?$""")

        fun parse(value: String): GifProxy {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return None
            val (ipv6, host, port) = hostAndPort.matchEntire(trimmed)?.destructured ?: return Invalid
            val portNumber = if (port.isEmpty()) DEFAULT_PORT else port.toInt().takeIf { it in 1..65_535 } ?: return Invalid
            return Tunnel(host = ipv6.ifEmpty { host }, port = portNumber)
        }
    }
}

data class GifConfig(
    val apiKey: String,
    val proxy: GifProxy,
) {
    val isAvailable: Boolean get() = apiKey.isNotBlank() && proxy != GifProxy.Invalid
}
