package complex.obj.init

import org.example.rootGson
import java.math.BigDecimal

fun <T> getOrInit(get: () -> T?, set: (T) -> Unit, initObject: () -> T?) =
    get() ?: initObject().also { if (it != null) set(it) }

fun main() {
    val obj = OBJ_1_1()

    getOrInit({ obj.child1 },   { obj.child1 = it },   ::OBJ_2_1)
        .let { getOrInit({ it.block1 },   { c -> it.block1 = c },   ::OBJ_3_1) }
        .let { getOrInit({ it.section1 }, { c -> it.section1 = c }, ::OBJ_4_1) }
        .let { getOrInit({ it.detail1 },  { c -> it.detail1 = c },  ::OBJ_5_1) }
        .amount2 = BigDecimal("200.00")

    obj.let {
        if (it.child1 == null) { it.child1 = OBJ_2_1()}
        it.child1
    }!!.let {
        if (it.block1 == null) { it.block1 = OBJ_3_1() }
        it.block1
    }!!.let {
        if (it.section1 == null) { it.section1 = OBJ_4_1() }
        it.section1
    }!!.let {
        if (it.detail1 == null) { it.detail1 = OBJ_5_1()}
        it.detail1
    }!!.amount = BigDecimal("100.00")

    println(rootGson.toJson(obj))
}
