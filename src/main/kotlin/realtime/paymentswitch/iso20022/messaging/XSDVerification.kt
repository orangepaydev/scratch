package realtime.paymentswitch.iso20022.messaging

import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import java.io.*
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource


val factory = javax.xml.validation.SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
val schema = factory.newSchema()

fun validate(xmlFile: String) {
    println("Validating $xmlFile")

    try {
        val validator = schema.newValidator()
        validator.resourceResolver = DynamicSchemaResolver()

        val inputXml = File(xmlFile)

        val xmlFile = StreamSource(inputXml.inputStream())
        validator.validate(xmlFile)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

fun main() {
    validate("xsd/custom-validator/sample-SignOnRequest.xml")
    validate("xsd/custom-validator/sample-SignOnResponse.xml")
    validate("xsd/custom-validator/sample-SignOffRequest.xml")
    validate("xsd/custom-validator/sample-SignOffResponse.xml")
    validate("xsd/custom-validator/sample-EchoRequest.xml")
    validate("xsd/custom-validator/sample-EchoResponse.xml")
    validate("xsd/custom-validator/sample-SystemEventNotification.xml")
    validate("xsd/custom-validator/sample-MessageReject.xml")
}

class DynamicSchemaResolver : LSResourceResolver {
    override fun resolveResource(
        type: String?,
        namespaceURI: String?,
        publicId: String?,
        systemId: String?,
        baseURI: String?
    ): LSInput? {
        // print all the arguments here in a single line
        println("resolveResource called with type: $type, namespaceURI: $namespaceURI, publicId: $publicId, systemId: $systemId, baseURI: $baseURI")

        val bodyFile = when (namespaceURI) {
            "urn:tc:rtp:xsd:rtp.message.01" -> {
                 File("xsd/custom-validator/message/message.xsd")
            }
            "http://www.w3.org/2001/XMLSchema.dtd" -> {
                File("xsd/custom-validator/XMLSchema.dtd")
            }
            "urn:iso:std:iso:20022:tech:xsd:head.001.001.04" -> {
                File("xsd/custom-validator/head/head.001.001.04.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.001.001.01" -> {
                File("xsd/custom-validator/admn/admn.001.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.002.001.01" -> {
                File("xsd/custom-validator/admn/admn.002.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.003.001.01" -> {
                File("xsd/custom-validator/admn/admn.003.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.004.001.01" -> {
                File("xsd/custom-validator/admn/admn.004.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.005.001.01" -> {
                File("xsd/custom-validator/admn/admn.005.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admn.006.001.01" -> {
                File("xsd/custom-validator/admn/admn.006.001.01.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admi.004.001.02" -> {
                File("xsd/custom-validator/admi/admi.004.001.02.xsd")
            }
            "urn:iso:std:iso:20022:tech:xsd:admi.002.001.01" -> {
                File("xsd/custom-validator/admi/admi.002.001.01.xsd")
            }
            else -> null
        }
        if (bodyFile == null) {
            println("No matching resource found for type: $type")
            return null
        }

        return XSDInput(publicId, systemId, bodyFile.inputStream())
    }
}

/**
 * Created by caleblau on 8/2/18.
 */
class XSDInput(
    private var _publicId: String?,
    private var _systemId: String?,
    input: InputStream
) : LSInput {
    var inputStream = BufferedInputStream(input)

    override fun getPublicId(): String? = _publicId
    override fun setPublicId(publicId: String?) {
        this._publicId = publicId
    }

    override fun getSystemId(): String? = _systemId
    override fun setSystemId(systemId: String?) {
        this._systemId = systemId
    }

    override fun getBaseURI(): String? = null
    override fun setBaseURI(baseURI: String?) {}

    override fun getByteStream(): InputStream? = null
    override fun setByteStream(byteStream: InputStream?) {}

    override fun getCertifiedText(): Boolean = false
    override fun setCertifiedText(certifiedText: Boolean) {}

    override fun getCharacterStream(): Reader? = null
    override fun setCharacterStream(characterStream: Reader?) {}

    override fun getEncoding(): String? = null
    override fun setEncoding(encoding: String?) {}

    override fun getStringData(): String? {
        synchronized(inputStream) {
            return try {
                val input = ByteArray(inputStream.available())
                inputStream.read(input)
                String(input)
            } catch (e: IOException) {
                e.printStackTrace()
                println("Exception $e")
                null
            }
        }
    }

    override fun setStringData(stringData: String?) {}
}