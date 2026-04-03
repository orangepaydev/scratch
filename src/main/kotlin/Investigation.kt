import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8

fun String.toRequestBody(contentType: MediaType? = null): RequestBody {
    var charset: Charset = UTF_8
    var finalContentType: MediaType? = contentType
    if (contentType != null) {
        val resolvedCharset = contentType.charset()
        if (resolvedCharset == null) {
            charset = UTF_8
            finalContentType = "$contentType; charset=utf-8".toMediaTypeOrNull()
        } else {
            charset = resolvedCharset
        }
    }
    val bytes = toByteArray(charset)
    return bytes.toRequestBody(finalContentType, 0, bytes.size)
}

fun main() {
    OkHttpClient.Builder()
    val requestBody = "<xml><test>hello</test></xml>"
    val url = "https://httpbin.org/anything"
    val body =
        requestBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    val headers = listOf<String>(
        "accept",
        "application/xml",
        "content-type",
        "application/xml",
        "api-key",
        "bla-bla-bla"
    )
    // Add date header
    val allHeaders = mutableListOf<String>()
    allHeaders.addAll(headers)

    val headersObj = Headers.headersOf(*allHeaders.toTypedArray())
    val request = Request.Builder()
        .headers(headersObj)
        .url(url)
        .post(body)
        .build()

    // print out the headers in the request
    println("Request Headers:")
    request.headers.forEach { header ->
        println("${header.first}: ${header.second}")
    }

    val client = OkHttpClient()
    val response = client.newCall(request).execute()
    println(response.body?.string())

    // print the response headers
    println("Response Headers:")
    response.headers.forEach { header ->
        println("${header.first}: ${header.second}")
    }
}