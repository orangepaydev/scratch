package pki.license

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

fun signPayload(payload: ByteArray, privateKey: PrivateKey): ByteArray {
    // Note the change to ECDSA
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(privateKey)
    signer.update(payload)
    return signer.sign()
}

fun loadPrivateKey(path: String): PrivateKey {
    Security.addProvider(BouncyCastleProvider())

    // Strip PEM headers and decode Base64 — this is a raw SEC1 EC key
    val pem = File(path).readText()
        .replace("-----BEGIN EC PRIVATE KEY-----", "")
        .replace("-----END EC PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")
    val sec1Bytes = Base64.getDecoder().decode(pem)

    // Parse SEC1 structure and wrap it in a PKCS#8 PrivateKeyInfo so KeyFactory can load it
    val ecPrivateKey = ECPrivateKey.getInstance(sec1Bytes)
    val algorithmIdentifier = AlgorithmIdentifier(
        X9ObjectIdentifiers.id_ecPublicKey,
        SECNamedCurves.getByName("secp256r1").toASN1Primitive()
    )
    val pkcs8Bytes = PrivateKeyInfo(algorithmIdentifier, ecPrivateKey).encoded
    return KeyFactory.getInstance("EC", "BC").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
}

fun verifySignature(payload: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
    // Must match the algorithm used for signing
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(payload)
    return verifier.verify(signature)
}

fun loadPublicKey(path: String): PublicKey {
    val cf = CertificateFactory.getInstance("X.509")
    val cert = FileInputStream(path).use { cf.generateCertificate(it) as X509Certificate }
    return cert.publicKey
}

fun main() {
    val payload = "This is a test payload for signing.".toByteArray()

    val privateKey = loadPrivateKey("license_pki/private/server.key")
    val signature = signPayload(payload, privateKey)
    println("Signature (Base64):\n${Base64.getEncoder().encodeToString(signature)}")

    val publicKey = loadPublicKey("license_pki/private/server.crt")
    val isValid = verifySignature(payload, signature, publicKey)
    println("Signature valid: $isValid")
}