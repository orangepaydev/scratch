package vault

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.IESParameterSpec
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.StringReader
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.util.Base64
import javax.crypto.Cipher

class LocalPrivateKeyService(privateKeyPem: String) {

    private val privateKey: PrivateKey = parsePrivateKey(privateKeyPem)

    /**
     * SIGN: Proves the message came from 'MyApplication'
     */
    fun signPayload(payload: String): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payload.toByteArray())
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    /**
     * DECRYPT: Decrypts data that was encrypted using the Public Cert
     * Note: Requires BouncyCastle provider for ECIES
     */
    fun decryptMessage(encryptedBase64: String): String {
        val params = IESParameterSpec(
            null,
            null,
            128,
            128,
            null
        )

        val cipher = Cipher.getInstance("ECIES", "BC")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, params)

        val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64))
        return String(decryptedBytes)
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val reader = StringReader(pem)
        val pemParser = PEMParser(reader)
        val converter = JcaPEMKeyConverter()

        val `object` = pemParser.readObject()

        return when (`object`) {
            is PEMKeyPair -> {
                // Handles traditional "BEGIN EC PRIVATE KEY" (SEC1)
                converter.getPrivateKey(`object`.privateKeyInfo)
            }
            is PrivateKeyInfo -> {
                // Handles "BEGIN PRIVATE KEY" (PKCS#8)
                converter.getPrivateKey(`object`)
            }
            else -> throw IllegalArgumentException("Invalid Private Key format: ${`object`?.javaClass?.name}")
        }
    }

    companion object {

        fun performBCInit() {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        init {
            performBCInit()
        }
    }
}
