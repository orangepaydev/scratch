package experiment.xml.fasterxml

import com.fasterxml.aalto.stax.InputFactoryImpl
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import javax.xml.stream.XMLStreamConstants
import java.util.ArrayDeque

fun measureAllocations(block: () -> Unit) {
    val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    // Capture bytes allocated before
    val bytesBefore = threadBean.getThreadAllocatedBytes(Thread.currentThread().id)
    val startTime = System.currentTimeMillis()

    block() // Run your XML parsing

    val elapsedMs = System.currentTimeMillis() - startTime

    // Capture bytes allocated after
    val bytesAfter = threadBean.getThreadAllocatedBytes(Thread.currentThread().id)

    val totalAllocated = bytesAfter - bytesBefore
    println("Total bytes allocated: ${totalAllocated / 1024} KB")
    println("Time taken: ${elapsedMs} ms")
}

/**
 * Parses [filterXPath] into its individual tokens for efficient streaming matching.
 *
 * Example: "/library/book" → ["library", "book"]
 */
fun parseXPathFilter(filterXPath: String): List<String> =
    filterXPath.split("/").filter { it.isNotEmpty() }

/**
 * Checks whether a single path token from the filter matches the current indexed segment.
 *
 * - A filter token with an index (e.g. "book[1]") must match exactly.
 * - A filter token without an index (e.g. "book") matches any index of that element ("book[0]", "book[2]", …).
 */
private fun tokenMatches(filterToken: String, indexedSegment: String): Boolean {
    return if ('[' in filterToken) {
        // Exact match including index
        filterToken == indexedSegment
    } else {
        // Match any index: "book" matches "book[0]", "book[1]", …
        indexedSegment.startsWith("$filterToken[")
    }
}

/**
 * Converts an XML string into a map of XPath → value entries.
 *
 * @param xml         The XML content to parse.
 * @param filterXPath Optional XPath prefix filter (e.g. "/library/book" or "/library/book[1]").
 *                    When provided, only nodes whose path starts with (or equals) the filter are collected.
 *                    Matching is done token-by-token during streaming — no full path strings are constructed
 *                    until a node is confirmed to be within the matching subtree.
 */
fun xmlToXPathMap(xml: String, filterXPath: String? = null): Map<String, String> {
    val factory = InputFactoryImpl()
    val reader = factory.createXMLStreamReader(xml.reader())
    val result = mutableMapOf<String, String>()

    // Pre-tokenize the filter once so we never manipulate strings during streaming
    val filterTokens: List<String> = filterXPath?.let { parseXPathFilter(it) } ?: emptyList()
    val filterDepth = filterTokens.size // 0 means "collect everything"

    // Stack of path segments (already includes [N] suffix)
    val pathStack = ArrayDeque<String>()
    // Stack of sibling counters: each entry maps tagName -> number of times seen so far at this level
    val siblingCounterStack = ArrayDeque<MutableMap<String, Int>>()
    siblingCounterStack.push(mutableMapOf())

    // How many consecutive filter tokens have been matched from the root.
    // - matchedDepth == filterDepth  →  we are at or below the filter subtree: collect everything
    // - matchedDepth <  filterDepth  →  still descending toward the filter: collect nothing
    // We track this as a stack so we can restore the previous value on END_ELEMENT.
    val matchedDepthStack = ArrayDeque<Int>()
    matchedDepthStack.push(0)

    try {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val tagName = reader.localName
                    val siblingCounters = siblingCounterStack.peek()
                    val index = siblingCounters.getOrDefault(tagName, 0)
                    siblingCounters[tagName] = index + 1

                    val segment = "$tagName[$index]"
                    pathStack.push(segment)
                    siblingCounterStack.push(mutableMapOf())

                    // Advance the matched depth if the next filter token matches this segment
                    val prevMatched = matchedDepthStack.peek()
                    val newMatched = when {
                        filterDepth == 0 -> 0 // no filter: always "matched"
                        prevMatched < filterDepth && tokenMatches(filterTokens[prevMatched], segment) -> prevMatched + 1
                        prevMatched >= filterDepth -> prevMatched // already inside match zone: stay
                        else -> prevMatched // token didn't match: stay at current depth
                    }
                    matchedDepthStack.push(newMatched)

                    // Only collect attributes when we are inside (or at) the matched subtree
                    val collecting = filterDepth == 0 || newMatched >= filterDepth
                    if (collecting) {
                        val currentPath = pathStack.reversed().joinToString("/", prefix = "/")
                        for (i in 0 until reader.attributeCount) {
                            val attrName = reader.getAttributeLocalName(i)
                            val attrValue = reader.getAttributeValue(i)
                            result["$currentPath[@$attrName]"] = attrValue
                        }
                    }
                }

                XMLStreamConstants.CHARACTERS -> {
                    val text = reader.text.trim()
                    if (text.isNotEmpty()) {
                        val collecting = filterDepth == 0 || matchedDepthStack.peek() >= filterDepth
                        if (collecting) {
                            val currentPath = pathStack.reversed().joinToString("/", prefix = "/")
                            result[currentPath] = text
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    pathStack.pop()
                    siblingCounterStack.pop()
                    matchedDepthStack.pop()
                }
            }
        }
    } finally {
        reader.close()
    }

    // Collect all indexed segments seen across all keys
    val allSegments = result.keys
        .flatMap { it.split("/").filter { s -> s.isNotEmpty() } }
        .toSet()

    // A segment like "book[0]" is recurring if "book[1]" also appears anywhere in the keys
    fun isRecurring(segment: String): Boolean {
        val baseName = segment.substringBefore("[")
        return allSegments.any { it.startsWith("$baseName[") && it != "$baseName[0]" }
    }

    // Strip [0] from segments that are non-recurring (appear only once at their level)
    fun normalizePath(path: String): String =
        path.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/", prefix = "/") { segment ->
                if (segment.endsWith("[0]") && !isRecurring(segment)) segment.dropLast(3)
                else segment
            }

    return result.entries.associate { (k, v) -> normalizePath(k) to v }
}

val xmlData = """
        <library>
            <book id="101">
                <title>Kotlin In Action</title>
                <author>Dmitry Jemerov</author>
            </book>
            <book id="102">
                <title>Atomic Kotlin</title>
                <author>Bruce Eckel</author>
            </book>
        </library>
    """.trimIndent()

fun main() {
    measureAllocations {
        repeat(100000) {
            xmlToXPathMap(xmlData)
        }
    }

//    println("=== No filter ===")
//    xmlToXPathMap(xmlData).forEach { (xpath, value) -> println("$xpath = $value") }
//
//    println("\n=== Filter: /library/book ===")
//    xmlToXPathMap(xmlData, "/library/book").forEach { (xpath, value) -> println("$xpath = $value") }
//
//    println("\n=== Filter: /library/book[1] ===")
//    xmlToXPathMap(xmlData, "/library/book[1]").forEach { (xpath, value) -> println("$xpath = $value") }
}