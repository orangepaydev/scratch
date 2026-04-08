package http

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun unescapeHttpHeader(value: String): String {
    return URLDecoder.decode(value, StandardCharsets.UTF_8)
}

/**
 * Handles quoted-string unescaping as defined in RFC 7230:
 * strips surrounding quotes and resolves backslash-escaped characters.
 */
fun unescapeQuotedString(value: String): String {
    val trimmed = value.trim()
    if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) return trimmed
    val inner = trimmed.substring(1, trimmed.length - 1)
    val sb = StringBuilder()
    var i = 0
    while (i < inner.length) {
        if (inner[i] == '\\' && i + 1 < inner.length) {
            sb.append(inner[i + 1])
            i += 2
        } else {
            sb.append(inner[i])
            i++
        }
    }
    return sb.toString()
}

fun main() {
    // --- URL-encoded header value ---
    val encoded = "attachment%3B%20filename%3D%22my%20file.txt%22"
    println("Encoded   : $encoded")
    println("Unescaped : ${unescapeHttpHeader(encoded)}")

    println()

    // --- Quoted-string header value (RFC 7230) ---
    val quoted = "\"Hello, \\\"World\\\"!\""
    println("Quoted    : $quoted")
    println("Unescaped : ${unescapeQuotedString(quoted)}")
}

