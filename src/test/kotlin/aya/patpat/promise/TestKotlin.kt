package aya.patpat.promise

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Test

class TestKotlin {

    @Test
    fun testGlobalScope() {
        GlobalScope.launch {
            println("GlobalScope")
        }
    }

    @Test
    fun test() {

    }
}