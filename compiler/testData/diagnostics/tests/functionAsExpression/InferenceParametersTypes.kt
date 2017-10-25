// !CHECK_TYPE
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_ANONYMOUS_PARAMETER -UNUSED_VARIABLE
// !WITH_NEW_INFERENCE
fun <T> listOf(): List<T> = null!!

fun test(a: (Int) -> Int) {
    test(fun (x) = 4)

    test(fun (x) = x)

    test(fun (x): Int { checkSubtype<Int>(x); return 4 })
}

fun test2(a: () -> List<Int>) {
    test2(fun () = <!TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>listOf<!>())
}

val a: (Int) -> Unit = fun(x) { checkSubtype<Int>(x) }

val b: (Int) -> Unit = <!TYPE_MISMATCH!>fun(<!NI;EXPECTED_PARAMETER_TYPE_MISMATCH!><!EXPECTED_PARAMETER_TYPE_MISMATCH!>x: String<!><!>) {}<!>