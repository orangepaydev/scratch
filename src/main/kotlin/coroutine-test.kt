package org.example

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun worker () {
    GlobalScope.launch {
        Thread.sleep(1000)
        println("hello")
    }
}

fun main() {
    worker()
    println("worker completed")
    Thread.sleep(2000)

    val cnRegex = Regex("dr [lm]")

    val result = cnRegex.find("who is dr leo zaman dr mahathir")
    println (result?.groupValues?.joinToString(","))
    val result2 = cnRegex.find("who is dr m")
    println (result2?.groupValues?.joinToString(","))
}