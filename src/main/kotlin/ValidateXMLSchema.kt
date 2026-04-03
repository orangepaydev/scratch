import java.io.File
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult

fun main() {
//    val xsdFile = File("xsd/all_schemas.xsd")
//    val xmlFile = File("reference.xml")
//
//    val minifiedBody = minifyXML(xmlFile.readText(Charsets.UTF_8))
//    println(minifiedBody)
//
//    val result = validateXml(xmlFile, xsdFile)
//
//    if (result.isValid) {
//        println("Success: XML is valid!")
//    } else {
//        println("Validation Failed:")
//        result.errors.forEach { println(" - ${it.message}") }
//    }


    val xsdFile = File("xsd/all_schemas.xsd")
    val xmlFile = File("reference.xml")

    val textBody = xmlFile.readText(Charsets.UTF_8)

    val startTime = System.currentTimeMillis()
    repeat(100000) {
        val minifiedBody = minifyXML(textBody)
    }
    println ("${System.currentTimeMillis() - startTime}")
}

data class ValidationResult(val isValid: Boolean, val errors: List<Exception> = emptyList())

fun validateXml(xmlFile: File, xsdFile: File): ValidationResult {
    val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    return try {
        // 1. Load the schema
        val schema = factory.newSchema(xsdFile)
        val validator = schema.newValidator()

        // 2. Validate
        validator.validate(StreamSource(xmlFile))
        ValidationResult(true)
    } catch (e: Exception) {
        // In a real app, you might use an ErrorHandler for a full list of errors
        ValidationResult(false, listOf(e))
    }
}

val xslt = """
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
      <xsl:output method="xml" omit-xml-declaration="yes"/>
      <xsl:strip-space elements="*"/>
      <xsl:template match="node()|@*">
        <xsl:copy><xsl:apply-templates select="node()|@*"/></xsl:copy>
      </xsl:template>
</xsl:stylesheet>
""".trimIndent()


fun minifyXML(xml: String): String {
    val xmlInput = StreamSource(StringReader(xml))
    val xsltInput = StreamSource(StringReader(xslt))

    val transformer = TransformerFactory.newInstance().newTransformer(xsltInput)

    val writer = StringWriter()
    transformer.transform(xmlInput, StreamResult(writer))

    return writer.toString()
}

