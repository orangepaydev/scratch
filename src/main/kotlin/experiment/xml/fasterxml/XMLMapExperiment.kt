package experiment.xml.fasterxml

import experiment.xml.fasterxml.XMLMap.Companion.parseXml

fun main() {
//    measureAllocations {
//        repeat(100000) {
//            parseXml(xmlData)
//        }
//    }

    val result = parseXml(xmlData)
    result.map().forEach { xpath, value ->
        println("$xpath = $value")
    }
}