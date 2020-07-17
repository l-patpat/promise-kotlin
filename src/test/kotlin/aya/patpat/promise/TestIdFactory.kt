package aya.patpat.promise

import org.junit.Test

class TestIdFactory {

    @Test
    fun testRepeat() {
        val idFactory = IdFactory()
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
        System.out.printf("%08X\n", idFactory.generate())
    }
}