// !LANGUAGE: +ArrayLiteralsInAnnotations, +AssigningArraysToVarargsInNamedFormInAnnotations
// !WITH_NEW_INFERENCE

// FILE: JavaAnn.java

@interface JavaAnn {
    String[] value() default {};
    String[] path() default {};
}

// FILE: test.kt

annotation class Ann(vararg val s: String)

@Ann(s = <!NI;TYPE_MISMATCH!><!NI;TYPE_MISMATCH!>arrayOf()<!><!>)
fun test1() {}

@Ann(s = <!NI;TYPE_MISMATCH!><!NI;TYPE_MISMATCH!><!TYPE_MISMATCH!>intArrayOf()<!><!><!>)
fun test2() {}

@Ann(s = <!NI;TYPE_MISMATCH!><!NI;TYPE_MISMATCH!><!TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH!>arrayOf(1)<!><!><!>)
fun test3() {}

@Ann("value1", "value2")
fun test4() {}

@Ann(s = ["value"])
fun test5() {}

@JavaAnn(value = <!NI;TYPE_MISMATCH!>arrayOf("value")<!>)
fun jTest1() {}

@JavaAnn(value = ["value"])
fun jTest2() {}

@JavaAnn(value = ["value"], path = ["path"])
fun jTest3() {}


annotation class IntAnn(vararg val i: Int)

@IntAnn(i = <!NI;TYPE_MISMATCH!>[1, 2]<!>)
fun foo1() {}

@IntAnn(i = <!NI;TYPE_MISMATCH!>intArrayOf(0)<!>)
fun foo2() {}