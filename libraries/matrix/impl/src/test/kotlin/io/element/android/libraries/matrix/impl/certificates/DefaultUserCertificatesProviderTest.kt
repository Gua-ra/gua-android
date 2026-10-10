/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.certificates

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.lambda.lambdaRecorder
import org.junit.Test
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class DefaultUserCertificatesProviderTest {
    @Test
    fun `release builds provide no user certificates and never read the CA store`() {
        val loadCaStore = lambdaRecorder<KeyStore?> { aCaStore() }
        assertThat(userCertificates(isDebuggable = false, loadCaStore = loadCaStore)).isEmpty()
        loadCaStore.assertions().isNeverCalled()
    }

    @Test
    fun `debuggable builds provide only the user-installed certificates`() {
        val loadCaStore = lambdaRecorder<KeyStore?> { aCaStore() }
        val certificates = userCertificates(isDebuggable = true, loadCaStore = loadCaStore)

        assertThat(certificates).hasSize(1)
        assertThat(certificates.single()).isEqualTo(aCertificate().encoded)
        loadCaStore.assertions().isCalledOnce()
    }

    @Test
    fun `debuggable builds provide nothing when the CA store is unavailable`() {
        assertThat(userCertificates(isDebuggable = true, loadCaStore = { null })).isEmpty()
    }

    private fun aCaStore(): KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("system:9a5ba575.0", aCertificate())
        setCertificateEntry("user:5ed36f99.0", aCertificate())
    }

    private fun aCertificate(): X509Certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(TEST_CA_PEM.byteInputStream()) as X509Certificate
}

private val TEST_CA_PEM = """
    -----BEGIN CERTIFICATE-----
    MIIBezCCASGgAwIBAgIUM5KA1Zjb9AXfC7YjoNXeX1JdQ1kwCgYIKoZIzj0EAwIw
    EjEQMA4GA1UEAwwHVGVzdCBDQTAgFw0yNjEwMDkyMjExMTRaGA8yMTI2MDkxNTIy
    MTExNFowEjEQMA4GA1UEAwwHVGVzdCBDQTBZMBMGByqGSM49AgEGCCqGSM49AwEH
    A0IABAi5U0TU2cmy2H6EVMaAURZY82uq0lTdKs1MY9QEfwsjVjWuFnMAv0K98+bO
    cpryH+LFC6IRRMpsoGic9b606RGjUzBRMB0GA1UdDgQWBBSYvjrGijOp+AyUKLSo
    85SGhSp8tzAfBgNVHSMEGDAWgBSYvjrGijOp+AyUKLSo85SGhSp8tzAPBgNVHRMB
    Af8EBTADAQH/MAoGCCqGSM49BAMCA0gAMEUCIQD+0bNSSEhxY95SNR5H+h5undYh
    9JShCY+CyIrTRK05agIgYMwqt6VhmvGctAFl1imwJmijX6EVfHWJ26zj9yyyL6o=
    -----END CERTIFICATE-----
""".trimIndent()
