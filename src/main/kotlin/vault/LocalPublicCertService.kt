package vault

import org.bouncycastle.jce.spec.IESParameterSpec
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.crypto.Cipher
import java.io.ByteArrayInputStream
import java.security.cert.X509Certificate

fun X509Certificate.getVaultFormattedSerial(): String {
    val serialBytes = this.serialNumber.toByteArray()

    // BigInteger.toByteArray() often prepends a 0x00 byte if the
    // serial number's most significant bit is 1 (to keep it positive).
    // We strip that leading zero to match the standard hex format.
    val startIndex = if (serialBytes.size > 1 && serialBytes[0].toInt() == 0) 1 else 0

    return serialBytes.sliceArray(startIndex until serialBytes.size)
        .joinToString(":") { "%02x".format(it) }
}

class LocalPublicCertService(certPem: String) {

    private val publicKey = parsePublicKeyFromCert(certPem)

    /**
     * VERIFY: Checks if the signature matches the payload and this cert
     */
    fun verifySignature(payload: String, signatureBase64: String): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey.publicKey)
        verifier.update(payload.toByteArray())
        return verifier.verify(Base64.getDecoder().decode(signatureBase64))
    }

    /**
     * ENCRYPT: Encrypts data so only the Private Key holder can read it
     * Note: Requires BouncyCastle provider for ECIES
     */
    fun encryptMessage(plainText: String): String {
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

    private fun parsePublicKeyFromCert(pem: String): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray()))
        return cert as X509Certificate
    }

    // return the public key
    fun getPublicKey() = publicKey
}
