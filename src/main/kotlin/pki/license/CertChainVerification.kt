package pki.license

import java.io.FileInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

fun verifyChain(serverCert: X509Certificate, intermediateCert: X509Certificate, rootCert: X509Certificate): Boolean {
    return try {
        serverCert.verify(intermediateCert.publicKey)
        intermediateCert.verify(rootCert.publicKey)
        rootCert.verify(rootCert.publicKey)
        true
    } catch (e: Exception) {
        false
    }
}

fun main() {
    val cf = CertificateFactory.getInstance("X.509")
    val basePath = "license_pki/private"

    val rootCert         = FileInputStream("$basePath/ca.crt").use { cf.generateCertificate(it) as X509Certificate }
    val intermediateCert = FileInputStream("$basePath/intermediate.crt").use { cf.generateCertificate(it) as X509Certificate }
    val serverCert       = FileInputStream("$basePath/server.crt").use { cf.generateCertificate(it) as X509Certificate }

    val valid = verifyChain(serverCert, intermediateCert, rootCert)
    println("Certificate chain is ${if (valid) "VALID" else "INVALID"}")
}
