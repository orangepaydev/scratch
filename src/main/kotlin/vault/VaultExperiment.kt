package vault

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.*
import java.security.spec.ECGenParameterSpec

fun createVaultClient() = createVaultClient(
    roleId = "bab45543-0a9c-c59b-b369-ebdfa728da1c",
    secretId = "f5db37c4-11b5-620f-b84d-ce5d2f289f5d"
)

fun createVaultPortalClient() = createVaultClient(
    roleId = "8c2243f2-66c3-9a18-365f-ccb59d5570db",
    secretId = "1bc565f9-9c53-afda-c8d4-a98442f1f4f5"
)

fun createVaultClient(roleId: String, secretId: String): Vault {
    val config = VaultConfig()
        .address("http://127.0.0.1:8200") // Your Vault Address
        .engineVersion(1)
        .build()


    val vaultAuth = Vault(config)

    // Log in using AppRole to get a token
    val token = vaultAuth.auth().loginByAppRole("approle", roleId, secretId).authClientToken
    println("token: $token")

    // Update config with the new token for future calls
    config.token(token)

    return Vault(config)
}

fun debug(vault: Vault) {
    val rootArgs = mapOf(
        "common_name" to "My Root CA",
        "ttl" to "87600h",
        "key_type" to "ec",
        "key_bits" to "256"
    )

    val rootResult = vault.logical().write("pki/root/generate/internal", rootArgs)
    println("HTTP Status: ${rootResult.restResponse.status}")

// 1. Print the entire map to see what keys Vault actually returned
    println("Full Response Data: ${rootResult.data}")

// 2. Safely extract the certificate
// Vault typically returns it as "certificate" for /internal
    val rootCert = rootResult.data["certificate"] ?: rootResult.data["cert"]

    if (rootCert != null) {
        println("Root CA Created: $rootCert")
    } else {
        println("Error: Certificate was not found in the response.")
    }
}

fun createPrivateKeyAndCerts(vault: Vault) {
    // --- STEP 1: Create Root CA (Internal to Vault) ---
    // We tell Vault to generate a self-signed Root ECC CA
    val rootArgs = mapOf(
        "common_name" to "My Root CA",
        "ttl" to "87600h", // 10 years
        "key_type" to "ec",
        "key_bits" to "256"
    )
    val rootResult = vault.logical().write("pki/root/generate/internal", rootArgs)
    val rootCert = rootResult.data["certificate"]
    println("Root CA Created. ${rootCert}")

    val rootIssuerId = rootResult.data["issuer_id"] as String
    println("Root CA Created with Issuer ID: $rootIssuerId")

    // --- STEP 2: Create Intermediate CA (Signed by Root) ---
    // A) Generate CSR for Intermediate
    val interCsrArgs = mapOf(
        "common_name" to "My Intermediate CA",
        "key_type" to "ec",
        "key_bits" to "256",
    )
    val interCsr = vault.logical().write("pki_int/intermediate/generate/internal", interCsrArgs).data["csr"]

    // B) Sign Intermediate with Root
    val signArgs = mapOf(
        "csr" to interCsr,
        "format" to "pem_bundle",
        "ttl" to "43800h"
    )
    // Path update: pki/issuer/<issuer_id>/sign-intermediate
    val signedInterResponse = vault.logical().write("pki/issuer/$rootIssuerId/sign-intermediate", signArgs)
    val signedInter = signedInterResponse.data["certificate"]

    // C) Set the signed certificate back to the Intermediate mount
    vault.logical().write("pki_int/intermediate/set-signed", mapOf("certificate" to signedInter))
    println("Intermediate CA Created and Signed.")

    // --- STEP 3: Create Application Cert (Signed by Intermediate) ---
    // We generate the private key locally and ask Vault to sign it
    val appKeyPair = generateECCKeyPair()
    val appCsr = generateCsr(appKeyPair, "CN=MyApplication")

    val appSignArgs = mapOf(
        "csr" to appCsr,
        "ttl" to "720h"
    )
    // Note: 'server-role' must be configured in Vault's pki_int engine
    val appResult = vault.logical().write("pki_int/sign/server-role", appSignArgs)

    println("--- APPLICATION PRIVATE KEY ---")
    println(toPem("EC PRIVATE KEY", appKeyPair.private.encoded))
    println("\n--- APPLICATION CERTIFICATE ---")
    println(appResult.data["certificate"])
}

fun generateServer(vault: Vault) {
    // --- STEP 3: Create Application Cert (Signed by Intermediate) ---
    // We generate the private key locally and ask Vault to sign it
    val appKeyPair = generateECCKeyPair()
    val appCsr = generateCsr(appKeyPair, "CN=MyApplication2")

    val appSignArgs = mapOf(
        "csr" to appCsr,
        "ttl" to "720h"
    )
    // Note: 'server-role' must be configured in Vault's pki_int engine
    val appResult = vault.logical().write("pki_int/sign/server-role", appSignArgs)

    appResult.restResponse.apply {
        println("HTTP Status: ${status}")
        println("HTTP Status: ${body.toString(Charsets.UTF_8)}")
    }


    println("--- APPLICATION PRIVATE KEY ---")
    println(toPem("EC PRIVATE KEY", appKeyPair.private.encoded))
    println("\n--- APPLICATION CERTIFICATE ---")
    println(appResult.data["certificate"])

}

// Helpers for local ECC and CSR generation
fun generateECCKeyPair(): KeyPair {
    val g = KeyPairGenerator.getInstance("EC")
    g.initialize(ECGenParameterSpec("secp256r1"))
    return g.generateKeyPair()
}

fun generateCsr(keyPair: KeyPair, subject: String): String {
    val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
    val csrBuilder = JcaPKCS10CertificationRequestBuilder(X500Name(subject), keyPair.public)
    val csr = csrBuilder.build(signer)
    return toPem("CERTIFICATE REQUEST", csr.encoded)
}

fun toPem(type: String, encoded: ByteArray): String {
    val sw = StringWriter()
    PemWriter(sw).use { it.writeObject(PemObject(type, encoded)) }
    return sw.toString()
}

fun debug2(vault: Vault) {
// Try reading the existing CA if the generate call returned nothing
    val existingCA = vault.logical().read("pki/cert/ca")

    if (existingCA != null && existingCA.data != null) {
        val rootCert = existingCA.data["certificate"]
        println("Found Existing Root CA: $rootCert")
    } else {
        println("No CA found. The engine might not be initialized.")
    }
}

fun fetchExistingCert(vault: Vault) {
// Fetch from pki_int/issue/server-role or pki_int/cert/xxx
    val response = vault.logical().write(
        "pki_int/issue/server-role", mapOf(
            "common_name" to "MyApplication"
        )
    )

    val certificatePem = response.data["certificate"] // The public cert
    val privateKeyPem = response.data["private_key"]   // The private key

    println("Certificate:\n$certificatePem")
    println("Private Key:\n$privateKeyPem")

    val certService = LocalPublicCertService(certificatePem!!)
    val pkService = LocalPrivateKeyService(privateKeyPem!!)

    val message = "Hello Vault"
    val verificationResult = certService.verifySignature(message, pkService.signPayload(message))
    println("Signature Verification Result: $verificationResult")

    certService.encryptMessage(message).also { encrypted ->
        println("Encrypted Message: $encrypted")
        val decrypted = pkService.decryptMessage(encrypted)
        println("Decrypted Message: $decrypted")
    }
}

fun main() {
    val vault = createVaultClient()
    // createPrivateKeyAndCerts(vault)
    // debug2(vault)
    // generateServer(vault)
    fetchExistingCert(vault)
}