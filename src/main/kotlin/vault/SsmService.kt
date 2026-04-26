package vault

import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import software.amazon.awssdk.services.ssm.model.Parameter
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException
import software.amazon.awssdk.services.ssm.model.ParameterType
import software.amazon.awssdk.services.ssm.model.PutParameterRequest
import software.amazon.awssdk.services.ssm.model.SsmException

class SsmService(region: String = "ap-southeast-1") {
    private val client: SsmClient

    init {
        client = SsmClient.builder().build()
    }

    fun getParameter(name: String): Parameter? {
        // get the SSM parameter to see
        return try {
            val request = GetParameterRequest.builder()
                .name(name)
                .build()
            val response = client.getParameter(request)
            response.parameter()
        } catch (e: ParameterNotFoundException) {
            // Specifically handle the "Not Found" case
            println("Parameter $name does not exist in SSM.")
            null
        }catch (e: SsmException) {
            // Handle other AWS issues (throttling, connection, permissions)
            println("AWS SDK Error: ${e.awsErrorDetails().errorMessage()}")
            throw e
        }
    }

    fun updateParameter(name: String, value: String) {
        // store the value in the older parameter
        val request = PutParameterRequest.builder()
            .name(name)
            .value(value)
            .type(ParameterType.STRING)
            .overwrite(true) // This makes it an "upsert"
            .build()

        client.putParameter(request)
    }
    fun updateParameterRotate(name: String, value: String) {
        // get the SSM parameter to see
        val param1 = getParameter("${name}1")
        val param2 = getParameter("${name}2")

        var pos = 1
        // determine which parameter is older
        if (param1 == null) {
            pos = 1
        } else if (param2 == null) {
            pos = 2
        } else if (param1.lastModifiedDate().isAfter(
                param2.lastModifiedDate()
            )
        ) {
            pos = 2
        } else {
            pos = 1
        }

        println("Updating parameter ${name}${pos}")

        updateParameter("${name}${pos}", value)
    }
}

fun main() {
    // fetch the root CA
    InitVault(createVaultClient()).apply {
        // fetch the root CA cert
        val data = fetchIssuerCer("pki", "rootCA20260426")
        val rootCACert = data!!["certificate"]

        SsmService().updateParameterRotate("/app-workflow-v5/license/CACert", rootCACert!!)

        getCertificate("pki_int", "app-workflow-v5.V5.20260427")?.let { (cert, publicKeyPEM, privateKeyPEM) ->

        }
    }
}