package vault

import com.bettercloud.vault.Vault
import org.bouncycastle.jce.spec.IESParameterSpec
import vault.LocalPrivateKeyService.Companion.performBCInit
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher

fun String.sha256(): String {
    val bytes = this.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)

    // Convert bytes to Hex string
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

class PortalVaultTest(val vault: Vault) {
    fun listCertificates(pkiPath: String) {
        val response = vault.logical().list("$pkiPath/certs")
        val serialNumbers = response.listData

        if (serialNumbers.isEmpty()) {
            println("No certificates found in $pkiPath.")
            return
        }

        println("Found ${serialNumbers.size} certificates:")
        val cf = CertificateFactory.getInstance("X.509")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")

        serialNumbers.forEach { serial ->
            try {
                val certData = vault.logical().read("$pkiPath/cert/$serial")
                val certPem = certData.data["certificate"] as? String
                if (certPem != null) {
                    val x509 = cf.generateCertificate(
                        ByteArrayInputStream(certPem.toByteArray(Charsets.UTF_8))
                    ) as X509Certificate
                    val cn = x509.subjectX500Principal.name
                        .split(",")
                        .firstOrNull { it.trimStart().startsWith("CN=") }
                        ?.substringAfter("CN=")
                        ?: "(unknown)"
                    val notBefore = x509.notBefore.toInstant().atZone(ZoneId.of("UTC")).format(fmt)
                    val notAfter = x509.notAfter.toInstant().atZone(ZoneId.of("UTC")).format(fmt)
                    println("- Serial : $serial")
                    println("  CN     : $cn")
                    println("  Valid  : $notBefore  ->  $notAfter")
                    println("  Issuer : ${x509.issuerX500Principal.name}")
                    x509.criticalExtensionOIDs?.forEach { oid ->
                        println("  Critical Extension: $oid")
                    }

                    // retrieve the app public and private key
                    val retrieveSecretResponse = vault.logical().read("kv-v1/$serial")
                    println(retrieveSecretResponse.restResponse.body.toString(Charsets.UTF_8))
                    println(retrieveSecretResponse.data["private_key"])
                    println(retrieveSecretResponse.data["certificate"])
                } else {
                    println("- $serial  (certificate PEM not available)")
                }
            } catch (e: Exception) {
                println("- $serial  (failed to parse: ${e.message})")
            }
        }
    }

    fun generatelicense(targetName: String) {
        val cf = CertificateFactory.getInstance("X.509")
        vault.logical().list("pki_int/certs")?.apply {
            listData.forEach { serial ->
                val certData = vault.logical().read("pki_int/cert/$serial")
                val certPem = certData.data["certificate"] as? String
                if (certPem != null) {
                    val x509 = cf.generateCertificate(
                        ByteArrayInputStream(certPem.toByteArray(Charsets.UTF_8))
                    ) as X509Certificate

                    val cn = x509.subjectX500Principal.name
                        .split(",")
                        .firstOrNull { it.trimStart().startsWith("CN=") }
                        ?.substringAfter("CN=")
                        ?: "(unknown)"

                    if (cn == targetName) {
                        println("Found target serial: $serial, generating license...")

                        val payload = "Hello world"
                        println(encryptMessage(x509, payload))
                        println(encryptMessage(x509, payload.sha256()))
                    }
                }

            }
        }
    }

    fun encryptMessage(publicKey: X509Certificate, plainText: String): String {
        // 1. Define IES Parameters
        // derivation, encoding, macKeySize, cipherKeySize, nonce
        val params = IESParameterSpec(
            null,    // derivation string (optional)
            null,    // encoding string (optional)
            128,     // macKeySize
            128,     // cipherKeySize
            null     // nonce (optional)
        )

        // 2. Initialize Cipher with parameters
        val cipher = Cipher.getInstance("ECIES", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey.publicKey, params)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }
}

fun main() {
    performBCInit()

    PortalVaultTest(createVaultPortalClient()).apply {
        listCertificates("pki_int")
        generatelicense("app-workflow-v5.V5.20260427")
    }
}
