package experiment.xml.fasterxml

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParser

/**
 * Represent XML as a Map.  Allowing much simpler extraction and manipulation of XML values
 */
class XMLMap {
    constructor(inputXmlMap: Map<String, String>) {
        xmlMap = inputXmlMap.toMutableMap()
    }

    constructor() {
        xmlMap = mutableMapOf()
    }

    private val xmlMap:MutableMap<String, String>

    fun addPath(path: String, value: String) {
        xmlMap[path] = value
    }

    private fun generateElement(sb: StringBuilder, input: XMLChainEntry) {

        // check if the tagName ends with an array indexes e.g. myElement[0]
        val elemTagName = if (input.tagName.endsWith("]")) {
            // remove the array indexes
            val pos = input.tagName.indexOf("[")
            input.tagName.substring(0, pos)
        } else {
            input.tagName
        }

        sb.append("<$elemTagName")
        if (input.attributeValue != null) {
            input.attributeValue!!.forEach { (k,v)->
                sb.append(" ${k}=\"${v}\" ")
            }
        }
        sb.append(">")

        if (input.entriesMap.size > 0) {
            input.entriesMap.forEach { (_, entry) ->
                generateElement(sb, entry)
            }
        } else {
            sb.append(input.tagValue)
        }


        sb.append("</$elemTagName>")
    }

    fun generate(): String {
        var parentChain: XMLChainEntry? = null

        // create the chain tree from the xmlMap entriex
        xmlMap.entries.sortedBy { it.key }.forEach { entry ->
            var currentChain: XMLChainEntry? = null

            // indicate if the end value is an attribute
            var isAttribute = false
            var attributeKey: String? = null

            entry.key.split("/").forEach xmlEntryLoop@{ tagName ->
                if (tagName == "") {
                    // skip the next element
                    return@xmlEntryLoop
                }

                if (parentChain == null) {
                    parentChain = XMLChainEntry(tagName)
                    currentChain = parentChain
                } else {
                    currentChain = if (currentChain == null) {
                        parentChain
                    } else {

                        val iteTagName = if (tagName.indexOf(":") != -1) {
                            val tagNameSV = tagName.split(":")

                            isAttribute = true
                            attributeKey = tagNameSV[1]

                            tagNameSV[0]
                        } else {
                            tagName
                        }

                        currentChain!!.entriesMap.computeIfAbsent(iteTagName) {
                            XMLChainEntry(it)
                        }
                    }
                }
            }

            if (!isAttribute) {
                currentChain!!.tagValue = entry.value
            } else {
                currentChain!!.addAttribute(attributeKey!!, entry.value)
            }
        }

        // travel through the chain and generate the xml
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        generateElement(sb, parentChain!!)
        return sb.toString()
    }

    fun map() = xmlMap

    companion object {
        fun parseXml(inputXml: String) = XMLMap(SaxExtractor.parseXml(inputXml))
    }
}

class SaxElem(val parent: SaxElem?, val xmlPath: String) {
    var childCount = 0
        private set

    // store the element attribute, if there are any
    var attributes: Attributes? = null

    fun incrementChildCount() {
        childCount++
    }

    //
    val childElemCounter = mutableMapOf<String, SaxElementWithCounter>()
}

class SaxElementWithCounter(var saxElem: SaxElem) {
    var elemCounter = 0
}


class SaxExtractor : DefaultHandler() {
    private var curElem: SaxElem? = null
    private val xmlMap  = mutableMapOf<String, String>()
    private var buf: StringBuffer? = null
    @Throws(SAXException::class)
    override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes?) {
        curElem = if (curElem == null) {
            SaxElem(null, "/$localName")
        } else {
            if (curElem!!.parent != null) {
                curElem!!.incrementChildCount()
            }
            SaxElem(curElem!!, curElem!!.xmlPath + "/" + localName)
        }
        if (attributes?.length ?: 0 > 0) {
            curElem!!.attributes = attributes
        }
        buf = StringBuffer()
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (buf != null && curElem!!.childCount == 0) {
            // Standalone elem
            xmlMap[curElem!!.xmlPath] = buf.toString()
            if (curElem?.attributes != null) {
                // add the attributes to the xmlPath
                val attributes = curElem!!.attributes!!
                for (i in 0 until attributes.length) {
                    xmlMap[curElem!!.xmlPath + ":" + attributes.getLocalName(i)] = attributes.getValue(i).toString()
                }
            }
            buf = null
        }
        curElem = curElem!!.parent
    }

    @Throws(SAXException::class)
    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (buf != null) {
            buf!!.append(String(ch!!, start, length))
        }
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        buf = null
        curElem = null
    }

    fun getXmlMap(): Map<String, String> {
        return xmlMap
    }

    companion object {
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parseXml(xml: String): Map<String, String> {
            val `is`: InputStream = ByteArrayInputStream(xml.toByteArray())
            val spf = SAXParserFactory.newInstance()
            spf.isNamespaceAware = true
            val saxParser: SAXParser = spf.newSAXParser()
            val xmlReader: XMLReader = saxParser.getXMLReader()
            val saxExtractor = SaxExtractor()
            xmlReader.setContentHandler(saxExtractor)
            xmlReader.parse(InputSource(`is`))
            return saxExtractor.getXmlMap()
        }
    }
}

class XMLChainEntry(val tagName: String) {
    var tagValue: String? = null
    var attributeValue: MutableMap<String, String>? = null

    fun addAttribute(key: String, value: String) {
        if (attributeValue == null) {
            attributeValue = mutableMapOf()
        }
        attributeValue!![key] = value
    }

    val entriesMap = mutableMapOf<String, XMLChainEntry>()
}
