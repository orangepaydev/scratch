package complex.obj.init

// ─── Option 1 helpers: getOrInit extension functions ─────────────────────────
// Each function returns the existing child or creates + assigns a new one.

fun OBJ_1_1.getOrInitChild1() = child1 ?: OBJ_2_1().also { child1 = it }
fun OBJ_1_1.getOrInitChild2() = child2 ?: OBJ_2_2().also { child2 = it }
fun OBJ_1_1.getOrInitChild3() = child3 ?: OBJ_2_3().also { child3 = it }

fun OBJ_2_1.getOrInitBlock1() = block1 ?: OBJ_3_1().also { block1 = it }
fun OBJ_2_1.getOrInitBlock2() = block2 ?: OBJ_3_2().also { block2 = it }
fun OBJ_2_1.getOrInitBlock3() = block3 ?: OBJ_3_3().also { block3 = it }

fun OBJ_3_1.getOrInitSection1() = section1 ?: OBJ_4_1().also { section1 = it }
fun OBJ_3_1.getOrInitSection2() = section2 ?: OBJ_4_2().also { section2 = it }
fun OBJ_3_1.getOrInitSection3() = section3 ?: OBJ_4_3().also { section3 = it }

fun OBJ_4_1.getOrInitDetail1() = detail1 ?: OBJ_5_1().also { detail1 = it }
fun OBJ_4_1.getOrInitDetail2() = detail2 ?: OBJ_5_2().also { detail2 = it }
fun OBJ_4_1.getOrInitDetail3() = detail3 ?: OBJ_5_3().also { detail3 = it }

// ─── Option 4 helper: generic getOrInit ──────────────────────────────────────
// Works on any class without per-field extensions.

fun <T> getOrInit(get: () -> T?, set: (T) -> Unit, create: () -> T): T =
    get() ?: create().also(set)

