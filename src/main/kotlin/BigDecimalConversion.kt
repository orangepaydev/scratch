import org.example.rootGson
import java.math.BigDecimal

fun main() {
    val bd = BigDecimal("1000222")
    val result = rootGson.toJson(bd)
    println(result)
}
