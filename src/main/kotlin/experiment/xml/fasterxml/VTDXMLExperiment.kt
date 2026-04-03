package experiment.xml.fasterxml
import com.ximpleware.VTDGen
import com.ximpleware.VTDNav

fun xmlToXPathMapVTD(xml: String): Map<String, String> {
    val vg = VTDGen()
    vg.setDoc(xml.toByteArray())
    vg.parse(true) // true enables namespace awareness

    val vn = vg.nav
    val result = mutableMapOf<String, String>()

    // We use a simple list as a stack to keep track of tag names
    val pathStack = mutableListOf<String>()

    fun traverse() {
        val currentIndex = vn.currentIndex
        val tagName = vn.toString(currentIndex)

        pathStack.add(tagName)
        val currentPath = pathStack.joinToString("/", prefix = "/")

        // 1. Process Attributes
        val count = vn.attrCount
        if (count > 0) {
            for (i in 0 until count) {
                // In VTD, attributes are indexed starting from the element index + 1
                val attrName = vn.toString(currentIndex + 1 + i * 2)
                val attrVal = vn.toString(currentIndex + 2 + i * 2)
                result["$currentPath[@$attrName]"] = attrVal
            }
        }

        // 2. Process Text (Leaf nodes)
        val textIndex = vn.getText()
        if (textIndex != -1) {
            val text = vn.toNormalizedString(textIndex).trim()
            if (text.isNotEmpty()) {
                result[currentPath] = text
            }
        }

        // 3. Recurse into children
        if (vn.toElement(VTDNav.FIRST_CHILD)) {
            do {
                traverse()
            } while (vn.toElement(VTDNav.NEXT_SIBLING))
            vn.toElement(VTDNav.PARENT) // Move back up
        }

        pathStack.removeAt(pathStack.size - 1)
    }

    traverse()
    return result
}

fun main() {
    measureAllocations {
        repeat(100000) {
            xmlToXPathMapVTD(xmlData)
        }
    }
}