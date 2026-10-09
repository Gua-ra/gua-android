/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.certificates

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.meta.BuildMeta
import timber.log.Timber
import java.security.KeyStore

@ContributesBinding(AppScope::class)
class DefaultUserCertificatesProvider(
    private val buildMeta: BuildMeta,
) : UserCertificatesProvider {
    /**
     * Get additional user-installed certificates from the `AndroidCAStore` `Keystore`, in debuggable builds only.
     *
     * The Rust HTTP client ignores network_security_config.xml, so this mirrors its `debug-overrides`.
     *
     * @return A list of byte arrays where each byte array is a single user-installed certificate
     *         in encoded form.
     */
    override fun provides(): List<ByteArray> = userCertificates(
        isDebuggable = buildMeta.isDebuggable,
        loadCaStore = ::loadAndroidCaStore,
    )
}

internal fun userCertificates(isDebuggable: Boolean, loadCaStore: () -> KeyStore?): List<ByteArray> {
    if (!isDebuggable) return emptyList()
    val keyStore = loadCaStore() ?: return emptyList()
    val aliases = try {
        keyStore.aliases()
    } catch (e: Exception) {
        Timber.w(e, "Failed to get aliases from the AndroidCAStore keystore")
        return emptyList()
    }
    return aliases.toList()
        // Undocumented alias format: `user:<hash>` or `system:<hash>`, where the hash is `openssl x509 -subject_hash_old`.
        .filter { alias -> alias.startsWith("user") }
        .mapNotNull { alias ->
            try {
                keyStore.getEntry(alias, null)
            } catch (e: Exception) {
                Timber.w(e, "Failed to get entry for alias $alias")
                null
            }
        }
        .filterIsInstance<KeyStore.TrustedCertificateEntry>()
        .map { trustedCertificateEntry ->
            trustedCertificateEntry.trustedCertificate.encoded
        }
        .also { Timber.i("Found ${it.size} additional user-provided certificates.") }
}

private fun loadAndroidCaStore(): KeyStore? = try {
    // Undocumented: on API 34 this store also lists user-installed CAs.
    KeyStore.getInstance("AndroidCAStore").apply { load(null) }
} catch (e: Exception) {
    Timber.w(e, "Failed to load the AndroidCAStore keystore")
    null
}
