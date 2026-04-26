package vault

import com.bettercloud.vault.Vault
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.ECPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.RFC5649WrapEngine
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class InitVault(val vault: Vault) {

    fun fetchIssuerCer(pkiPath: String, dnName: String): Map<String, String>? {
        val issuerResponse = vault.logical()
            .read("$pkiPath/issuer/$dnName")

        if (!issuerResponse.data.contains("issuer_id")) {
            return null
        }
        return issuerResponse.data
    }

    fun fetchIssuerId(pkiPath: String, dnName: String): String? {
        val issuerResponse = vault.logical()
            .read("$pkiPath/issuer/$dnName")

        println(issuerResponse.restResponse.body.toString(Charsets.UTF_8))
//        issuerResponse.data.forEach { (key, value) ->
//            println("$key: $value")
//        }
        if (!issuerResponse.data.contains("issuer_id")) {
            return null
        }

        return issuerResponse.data["issuer_id"] as String
    }

    // create the CA certificate and print the certificate content
    fun createCA(caName: String): String? {
        val pkiPath = "pki" // Adjust if your PKI engine is mounted at a different path
        val caConfig = mapOf(
            "common_name" to "My Corporate Root CA",
            "ttl" to "87600h",
            "key_type" to "ec",
            "key_bits" to 256, // 384, 521 - is not supported by Vault, 256 is the max for EC keys
            "issuer_name" to caName // This sets the reference name for the issuer
        )

        val response = vault.logical()
            .write("$pkiPath/root/generate/internal", caConfig)

        val issuerResponse = vault.logical()
            .read("$pkiPath/issuer/$caName")

        val certificate = issuerResponse.data["certificate"]

        if (certificate != null) {
            println("Successfully fetched certificate for: $caName")
            println("--- BEGIN CERTIFICATE ---")
            println(certificate)
            return certificate
        } else {
            println("Certificate data not found for issuer: $caName")
            return null
        }
        println("CA Created successfully.")
    }

    fun createCAInner(parentPkiPath: String, parentCaName: String, caIntermediateName: String) {
        val caParentIssuerId = fetchIssuerId(parentPkiPath, parentCaName)
        if (caParentIssuerId == null) {
            println("Error: Parent CA issuer ID not found for $parentCaName")
            // throw runtime error
            throw RuntimeException("Parent CA issuer ID not found for $parentCaName")
        }

        // --- STEP 1: Generate CSR on the Intermediate Mount ---
        val csrParams = mapOf(
            "common_name" to "Intermediate CA v1.1",
            "type" to "internal",
            "key_type" to "ec",
            "key_bits" to 256        // 384, 521 - is not supported by Vault, 256 is the max for EC keys
        )
        val intMount = "pki_int"
        val csrResponse = vault.logical()
            .write("$intMount/intermediate/generate/internal", csrParams)

        val csr = csrResponse.data["csr"] ?: throw Exception("Failed to generate CSR")

        // --- STEP 2: Sign the Intermediate CSR using the Parent CA ID ---
        val signParams = mapOf(
            "csr" to csr,
            "common_name" to caIntermediateName,
            "issuer_ref" to caParentIssuerId, // Linking to your specific Root CA ID
            "format" to "pem",
            "ttl" to "43800h" // 5 years
        )

        val rootMount = parentPkiPath
        val signedResponse = vault.logical()
            .write("$rootMount/root/sign-intermediate", signParams)

        val signedCert = signedResponse.data["certificate"] ?: throw Exception("Signing failed")

        // --- STEP 3: Set the signed certificate back to the Intermediate mount ---
        // This activates the intermediate and associates it with its name
        val setCertParams = mapOf(
            "certificate" to signedCert
        )

        val storeResponse = vault.logical().write("$intMount/intermediate/set-signed", setCertParams)

        println(storeResponse.restResponse.body.toString(Charsets.UTF_8))

        // --- STEP 4: (Optional) Rename/Alias the Intermediate for easy fetching ---
        // We use the 'pki/issuer/{id}' endpoint to update the name

        // the result is "[\"2813e36c-e229-522d-889c-0c665cfa62ec\"]", cheap and dirty way to extract the ID from the response
        val intermediateId = (storeResponse.data["imported_issuers"] as String).replace("[\"\\[\\]]".toRegex(), "")

        val renameParams = mapOf("issuer_name" to caIntermediateName)
        vault.logical().write("$intMount/issuer/$intermediateId", renameParams)

        println("Intermediate CA successfully created and named: $caIntermediateName")
    }

    fun issueAppCert(intermediatePkiPath: String, intermediateCaName: String, serverRole: String, appName: String) {
        val intermediateIssuerId = fetchIssuerId(intermediatePkiPath, intermediateCaName)
        if (intermediateIssuerId == null) {
            println("Error: Intermediate CA issuer ID not found for $intermediateCaName")
            throw RuntimeException("Intermediate CA issuer ID not found for $intermediateCaName")
        }

        val certParams = mapOf(
            "common_name" to appName,
            "issuer_ref" to intermediateIssuerId,
            "format" to "pem",
            "key_type" to "ec",
            "key_bits" to 256, // prime256v1 / P-256
            "ttl" to "17520h", // 2 year
        )

        val certResponse = vault.logical()
            .write("$intermediatePkiPath/issue/$serverRole", certParams)

        val certPem = certResponse.data["certificate"] ?: throw Exception("Missing cert")
        val privateKeyPem = certResponse.data["private_key"] ?: throw Exception("Missing key")

        println("Certificate for $appName successfully issued:")
        println(certPem)
        println("Private Key for $appName successfully issued:")
        println(privateKeyPem)

        val certSerialNumber = LocalPublicCertService(certPem).let {
            val publicCert = it.getPublicKey()
            publicCert.getVaultFormattedSerial()
        }

        // store the public and private key in the secret engine for later retrieval, in a real app you would want to store these securely and not print them out
        val storeSecretResponse = vault.logical().write(
            "kv-v1/$certSerialNumber", mapOf(
                "private_key" to privateKeyPem,
                "certificate" to certPem
            )
        )

        println(storeSecretResponse.restResponse.body.toString(Charsets.UTF_8))

        uploadTransient(appName, privateKeyPem, certPem)
    }

    private fun pemToDer(pem: String, beginMarker: String, endMarker: String): ByteArray {
        val base64 = pem
            .replace(beginMarker, "")
            .replace(endMarker, "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(base64)
    }

    fun convertPemToPkcs8Der(privateKeyPem: String): ByteArray {
        return when {
            privateKeyPem.contains("-----BEGIN PRIVATE KEY-----") -> {
                pemToDer(privateKeyPem, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")
            }

            privateKeyPem.contains("-----BEGIN EC PRIVATE KEY-----") -> {
                val sec1Der = pemToDer(privateKeyPem, "-----BEGIN EC PRIVATE KEY-----", "-----END EC PRIVATE KEY-----")
                val ecPrivateKey = ECPrivateKey.getInstance(sec1Der)
                val algorithmIdentifier = AlgorithmIdentifier(
                    X9ObjectIdentifiers.id_ecPublicKey,
                    ecPrivateKey.parameters
                )
                PrivateKeyInfo(algorithmIdentifier, ecPrivateKey).encoded
            }

            else -> throw IllegalArgumentException("Unsupported private key PEM format")
        }
    }

    // Upload the Private Key and Public Cert to the vault using the "transit" secret engine, this is not a common use case but it can be useful for testing and demonstration purposes, in a real app you would want to store these securely and not print them out
    fun uploadTransient(keyName: String, privateKey: String, publicKeyPem: String) {

        // 1. Get Vault's RSA Wrapping Key
        val wrappingKeyResponse = vault.logical().read("transit/wrapping_key")
        println("wrappingKeyResponse ${wrappingKeyResponse.restResponse.body.toString(Charsets.UTF_8)}")
        val wrappingKeyPem = wrappingKeyResponse.data["public_key"] as String

        // 2. Prepare Vault's RSA Public Key for wrapping
        val publicKeyBase64 = wrappingKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val kf = KeyFactory.getInstance("RSA")
        val vaultPublicKey = kf.generatePublic(X509EncodedKeySpec(publicKeyBytes))

        // 3. Build BYOK ciphertext: RSA-wrap ephemeral AES key + AES-KWP-wrap target key bytes
        val ephemeralAesKey = ByteArray(32)
        SecureRandom().nextBytes(ephemeralAesKey)

        val pkcs8PrivateKeyDer = convertPemToPkcs8Der(privateKey)

        val kwp = RFC5649WrapEngine(AESEngine())
        kwp.init(true, KeyParameter(ephemeralAesKey))
        val wrappedTargetKey = kwp.wrap(pkcs8PrivateKeyDer, 0, pkcs8PrivateKeyDer.size)

        // Wrap the ephemeral AES key with the Vault RSA wrapping key using OAEP SHA-256
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val oaepSpec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )

        cipher.init(Cipher.ENCRYPT_MODE, vaultPublicKey, oaepSpec)
        val wrappedAesKey = cipher.doFinal(ephemeralAesKey)

        val wrappedKeyBytes = wrappedAesKey + wrappedTargetKey
        val wrappedKeyBase64 = Base64.getEncoder().encodeToString(wrappedKeyBytes)

        // 4. Execute the Import call with type 'ecdsa-p256'
        val importPayload = mapOf(
            "ciphertext" to wrappedKeyBase64,
            "hash_function" to "SHA256",
            "type" to "ecdsa-p256" // Explicitly set for EC 256
        )

        val response = vault.logical().write("transit/keys/$keyName/import", importPayload)
        println("Response ${response.restResponse.body.toString(Charsets.UTF_8)}")

        // associate the public cert with the key name in the transit engine, this is not a built-in feature of Vault but we can store it as metadata or in a separate secret path for demonstration purposes
        val certPayload = mapOf(
            "certificate_chain" to publicKeyPem
        )
        val certChainingResponse = vault.logical().write("transit/keys/$keyName/config", certPayload)
        println("Response ${certChainingResponse.restResponse.body.toString(Charsets.UTF_8)}")
    }

    fun getIssuer(pkiPath: String, issuerName: String): String? {
        try {
            // Construct the path: e.g., "pki/issuer/my-intermediate-ca"
            val path = "$pkiPath/issuer/$issuerName"

            val response = vault.logical().read(path)

            if (response.restResponse.status == 200) {
                val data = response.data

                println("--- Issuer Info: $issuerName ---")
                println("Issuer ID: ${data["issuer_id"]}")
                println("Common Name: ${data["common_name"]}")
                println("Usage: ${data["usage"]}")

                return data["certificate"] as String
            }
        } catch (e: Exception) {
            println("Could not find issuer '$issuerName': ${e.message}")
        }
        return null
    }

    fun getCertificate(pkiPath: String, targetName: String): Triple<X509Certificate, String, String>? {
        val cf = CertificateFactory.getInstance("X.509")
        vault.logical().list("${pkiPath}/certs")?.apply {
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

                        val retrieveSecretResponse = vault.logical().read("kv-v1/$serial")
                        println(retrieveSecretResponse.restResponse.body.toString(Charsets.UTF_8))
                        println(retrieveSecretResponse.data["private_key"])
                        println(retrieveSecretResponse.data["certificate"])

                        return Triple(x509, retrieveSecretResponse.data["certificate"]!!, retrieveSecretResponse.data["private_key"]!!)
                    }
                }
            }
        }
        return null
    }
}

fun main() {
    SsmService().also { ssm->
        InitVault(createVaultClient()).apply {
            // crate the root CA
            // will need to be generated once every 8 years, though the rootCA validity is 10 years
            // val caCertificate = createCA("rootCA20260426")
            // ssm.updateParameterRotate("/app-workflow-v5/license/CACert", caCertificate!!)

            // println(fetchIssuerId("pki", "rootCA20260426"))

            // create the intermediate CA signed by the root CA
            // will need to be generated once every 3 years, though the intermediateCA validity is 5 years
            // createCAInner("pki", "rootCA20260426", "intermediateCA20260426")
            val intCACer = getIssuer("pki_int", "intermediateCA20260426")!!

            // println(fetchIssuerId("pki_int", "intermediateCA20260426"))

            // these are the application certificates that will be used by the app, they will need to be generated every once every year, though the app cert validity is 2 years
            // issueAppCert("pki_int", "intermediateCA20260426", "server-role", "app-workflow-v5.V5.20260428")
//            val (_, certPEMServer, keyPEMServer) = getCertificate("pki_int", "app-workflow-v5.V5.20260428")!!
//            ssm.updateParameter("/portal-license-v1/license/ServerPublicCert", certPEMServer)
//            ssm.updateParameterRotate("/app-workflow-v5/license/ServerEncKey", keyPEMServer)

            // issueAppCert("pki_int", "intermediateCA20260426", "server-role", "portal-license-v1.V1.20260428")
//            val (_, certPEMPortal, keyPEMPortal) = getCertificate("pki_int", "portal-license-v1.V1.20260428")!!
//            ssm.updateParameter("/portal-license-v1/license/PortalPublicCert", certPEMPortal + "\n" + intCACer)
//            ssm.updateParameter("/portal-license-v1/license/PortalEncKey", keyPEMPortal)
        }
    }
}
