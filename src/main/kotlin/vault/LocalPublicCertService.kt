package vault

import org.bouncycastle.jce.spec.IESParameterSpec
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.crypto.Cipher
import java.io.ByteArrayInputStream

class LocalPublicCertService(certPem: String) {

    private val publicKey: PublicKey = parsePublicKeyFromCert(certPem)

    /**
     * VERIFY: Checks if the signature matches the payload and this cert
     */
    fun verifySignature(payload: String, signatureBase64: String): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
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
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, params)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    private fun parsePublicKeyFromCert(pem: String): PublicKey {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray()))
        return cert.publicKey
    }
}
