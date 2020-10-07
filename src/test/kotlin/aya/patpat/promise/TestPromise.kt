package aya.patpat.promise

import aya.patpat.result.GlobalResult
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

class TestPromise {

    @Test
    fun testAll() {
        testDoNothing()
        testResolve()
        testReject()
        testTimeout()
        testRetryResolve()
        testRetryReject()
        testBeforeThen()
        testBeforeCatch()
    }

    @Test
    fun testDoNothing() {
        println("testDoNothing start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
        }.onThen {
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            countCatch++
            println("onCatch count:$countCatch")
        }.launch()

        Thread.sleep(100)
        println("testDoNothing stop")
        assert(countLaunch == 1 && countThen == 0 && countCatch == 0)
    }

    @Test
    fun testResolve() {
        println("testResolve start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
            it.resolve(100)
        }.onThen {
            if (it?.equals(100) == true) {
                countThen++
                println("onThen count:$countThen")
            } else {
                println("onThen invalid params:${it ?: "null"}")
            }
        }.onCatch {
            countCatch++
            println("onCatch count:$countCatch")
        }.launch()

        Thread.sleep(100)
        println("testResolve stop")
        assert(countLaunch == 1 && countThen == 1 && countCatch == 0)
    }

    @Test
    fun testReject() {
        println("testReject start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
            it.reject(GlobalResult.ErrInternal())
        }.onThen {
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            if (it.`is`(GlobalResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.launch()

        Thread.sleep(100)
        println("testReject stop")
        assert(countLaunch == 1 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testTimeout() {
        println("testTimeout start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0
        var startTime = 0L

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
            Thread.sleep(300)
        }.onThen {
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            val time = System.currentTimeMillis() - startTime
            println("onCatch timeout:${time}ms")
            if (it.`is`(GlobalResult.TIMEOUT) && abs(time - 200) < 40) {
                countCatch++
                println("onCatch count:$countCatch")
            }
        }.timeout(200).launch()
        startTime = System.currentTimeMillis()

        Thread.sleep(500)
        println("testTimeout stop")
        assert(countLaunch == 1 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testRetryResolve() {
        println("testRetryResolve start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
            if (it.retryTimes < 3) {
                it.reject(GlobalResult.ErrInternal())
            } else {
                it.resolve("aaa")
            }
        }.onThen {
            if (it?.equals("aaa") == true) {
                countThen++
                println("onThen count:$countThen")
            } else {
                println("onThen invalid params:${it ?: "null"}")
            }
        }.onCatch {
            if (it.`is`(GlobalResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.retry(3).launch()

        Thread.sleep(100)
        println("testRetryResolve stop")
        assert(countLaunch == 4 && countThen == 1 && countCatch == 0)
    }

    @Test
    fun testRetryReject() {
        println("testRetryReject start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
            it.reject(GlobalResult.ErrInternal())
        }.onThen {
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            if (it.`is`(GlobalResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.retry(9).launch()

        Thread.sleep(100)
        println("testRetryReject stop")
        assert(countLaunch == 10 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testBeforeThen() {
        var record = ""

        var launchTime = 0L
        var beforeThenTime = 0L
        var thenTime = 0L
        Promise {
            launchTime = System.nanoTime()
            record += "launch\n"
            it.resolve()
        }.onBeforeThen {
            beforeThenTime = System.nanoTime()
            record += "onBeforeThen\n"
        }.onBeforeCatch {
            record += "onBeforeCatch\n"
        }.onThen {
            thenTime = System.nanoTime()
            record += "onThen\n"
        }.onCatch {
            record += "onCatch\n"
        }.launch()

        val startTime = System.nanoTime()
        Thread.sleep(100)
        println("testBeforeThen start\n" + record + "testBeforeThen stop\n")
        println("launchTime: ${0.000001 * (launchTime - startTime)}ms")
        println("beforeThenTime: ${0.000001 * (beforeThenTime - launchTime)}ms")
        println("thenTime: ${0.000001 * (thenTime - beforeThenTime)}ms")
        assert(record == "launch\nonBeforeThen\nonThen\n")
    }

    @Test
    fun testBeforeCatch() {
        var record = ""

        Promise {
            record += "launch\n"
            it.reject()
        }.onBeforeThen {
            record += "onBeforeThen\n"
        }.onBeforeCatch {
            record += "onBeforeCatch\n"
        }.onThen {
            record += "onThen\n"
        }.onCatch {
            record += "onCatch\n"
        }.launch()

        Thread.sleep(100)
        println("testBeforeCatch start\n" + record + "testBeforeCatch stop\n")
        assert(record == "launch\nonBeforeCatch\nonCatch\n")
    }

    @Test
    fun testExternResolve() {
//        println("testRetryReject start")
//
//        var countLaunch = 0
//        var countThen = 0
//        var countCatch = 0
//        val id: Long
//        Promise {
//            it.extern()
//        }.onThen {
//
//        }.onCatch {
//
//        }.launch()
//
//        Thread.sleep(100)
//        println("testRetryReject stop")
//        assert(countLaunch == 10 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testId() {
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Promise { System.out.printf("%08X\n", it.id) }.launch()
        Thread.sleep(100)
    }

    @Test
    fun testCatchLog() {
        Promise {
            it.reject(GlobalResult.Cancel())
        }.onCatchLog().launch()
        Thread.sleep(100)
    }

    @Test
    fun testAsync() {
        println("testAsync start")
        Async {
            println(Thread.currentThread().name)
            for (i in 0..100) {
                Promise { it.resolve() }.await()
                println(i)
            }
            Promise("aaa") { promise ->
                promise.extern()
                Promise("bbb") {
                    Thread.sleep(500)
                    it.resolve()
                }.onThen {
                    promise.resolve()
                    println("testAsync launch")
                }.launch()
            }.timeout(1000).await()
            println("testAsync success")
        }.onCatch { result, promise ->
            println("${result.result} ${result.msg}")
        }
        Thread.sleep(2000)
        println("testAsync stop")
    }

    @Test
    fun testOnClose() {
        Promise().onClose {
            println("onClose1")
        }.onClose {
            println("onClose11")
        }.launch()

        Promise {
            it.resolve()
        }.onClose(Dispatchers.Unconfined) {
            println("onClose2")
        }.onClose(Dispatchers.Unconfined) {
            println("onClose22")
        }.launch()

        Promise {
            it.reject()
        }.onClose(Dispatchers.IO) {
            println("onClose3")
        }.onClose(Dispatchers.IO) {
            println("onClose33")
        }.launch()

        Thread.sleep(500)
    }

    @Test
    fun testRound() {
        println(0.4.roundToInt())
    }
}