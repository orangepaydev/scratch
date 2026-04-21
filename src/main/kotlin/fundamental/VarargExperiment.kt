package fundamental

fun testFn(input1: Int, vararg propertyList: Pair<String, String>) {
    println("input1: $input1")
    propertyList.forEach { println("property: ${it.first} = ${it.second}") }
}

fun main() {
    val fixedArray = arrayOf(Pair("key1", "value1"), Pair("key2", "value2"))
    testFn(2, Pair("hello", "world"), Pair("hello", "world"), *fixedArray)
}
