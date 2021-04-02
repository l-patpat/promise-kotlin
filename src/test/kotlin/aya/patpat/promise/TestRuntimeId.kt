package aya.patpat.promise

import org.junit.Test

class TestRuntimeId {

    @Test
    fun testRepeat() {
        val runtimeId = RuntimeId()
        println(if (runtimeId.check(6 * 24 * 60 * 60 * 4096 + 4095)) "pass" else "not pass")
        println(if (runtimeId.check(6 * 24 * 60 * 60 * 4096 + 4095 + 1)) "not pass" else "pass")
        println(if (runtimeId.check(-1)) "not pass" else "pass")
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
        println(runtimeId.generate().toString(16))
    }
}