package aya.patpat.promise

import org.junit.Test
import java.lang.StringBuilder
import kotlin.math.abs

class TestPromise {

    init {
        Promise.defaultThenDispatcher = Dispatchers.Unconfined
        Promise.defaultCatchDispatcher = Dispatchers.Unconfined
        Promise.defaultProgressDispatcher = Dispatchers.Unconfined
        Promise.defaultCloseDispatcher = Dispatchers.Unconfined
    }

    @Test
    fun testAll() {
        testLaunch()
        testResolve()
        testReject()
        testPromiseResolve()
        testPromiseReject()
        testException()
        testTimeout()
        testRetryResolve()
        testRetryReject()
        testThen2()
        testCatch2()
        testProgress()
    }

    @Test
    fun testLaunch() {
        println("testLaunch start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0

        Promise {
            countLaunch++
            println("launch count:$countLaunch")
        }.onThen { _, _ ->
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            countCatch++
            println("onCatch count:$countCatch")
        }.launch()

        Thread.sleep(100)
        println("testLaunch stop")
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
        }.onThen { data, _ ->
            val num = Promise.parseData<Int>(data)
            if (num != null && num == 100) {
                countThen++
                println("onThen count:$countThen")
            } else {
                println("onThen invalid params:${num ?: "null"}")
            }
        }.onCatch {
            countCatch++
            println("onCatch count:$countCatch")
        }.start()

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
            it.reject(PromiseResult.ErrInternal())
        }.onThen { _, _ ->
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            if (it.`is`(PromiseResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.start()

        Thread.sleep(100)
        println("testReject stop")
        assert(countLaunch == 1 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testPromiseResolve() {
        Async {
            val a = PromiseResolve().await()
            println(a)
            val b = PromiseResolve(123).await()
            println(b)
            val c = PromiseResolve("abc").await()
            println(c)
            Thread.sleep(100)
            assert(a == null && b == 123 && c == "abc")
        }
    }

    @Test
    fun testPromiseReject() {
        Async {
            PromiseReject(PromiseResult.NOTHING, "nothing").await()
            assert(false)
        }.onCatch { result, promise ->
            println(result)
            assert(result.`is`(PromiseResult.NOTHING))
        }
        Thread.sleep(100)
    }

    @Test
    fun testException() {
        println("testException start")

        var countLaunch = 0
        var countThen = 0
        var countCatch = 0
        var result = ""

        Promise {
            countLaunch++
            throw PromiseException(PromiseResult.Cancel())
        }.onThen { _, _ ->
            countThen++
        }.onCatch {
            countCatch++
            result = it.result
            println("onCatch result:${it.result}")
        }.start()

        Thread.sleep(500)
        println("testException stop")
        assert(countLaunch == 1 && countThen == 0 && countCatch == 1 && result == PromiseResult.CANCEL)
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
        }.onThen { _, _ ->
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            val time = System.currentTimeMillis() - startTime
            println("onCatch timeout:${time}ms")
            if (it.`is`(PromiseResult.TIMEOUT) && abs(time - 200) < 40) {
                countCatch++
                println("onCatch count:$countCatch")
            }
        }.timeout(200).start()
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
                it.reject(PromiseResult.ErrInternal())
            } else {
                it.resolve("aaa")
            }
        }.onThen { data, _ ->
            val str = Promise.parseData<String>(data)
            if (str != null && str == "aaa") {
                countThen++
                println("onThen count:$countThen")
            } else {
                println("onThen invalid params:${str ?: "null"}")
            }
        }.onCatch {
            if (it.`is`(PromiseResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.retry(3).start()

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
            it.reject(PromiseResult.ErrInternal())
        }.onThen { _, _ ->
            countThen++
            println("onThen count:$countThen")
        }.onCatch {
            if (it.`is`(PromiseResult.ERR_INTERNAL)) {
                countCatch++
                println("onCatch count:$countCatch")
            } else {
                println("onCatch invalid error:${it.result}")
            }
        }.retry(9).start()

        Thread.sleep(100)
        println("testRetryReject stop")
        assert(countLaunch == 10 && countThen == 0 && countCatch == 1)
    }

    @Test
    fun testThen2() {
        var record = ""

        var launchTime = 0L
        var thenTime1 = 0L
        var thenTime2 = 0L
        val startTime = System.nanoTime()
        Promise {
            launchTime = System.nanoTime()
            record += "launch\n"
            it.resolve(1)
        }.onThen { data, resolve ->
            thenTime1 = System.nanoTime()
            record += "onThen$data\n"
            resolve(2)
        }.onThen { data, _ ->
            thenTime2 = System.nanoTime()
            record += "onThen$data\n"
        }.onCatch {
            record += "onCatch1\n"
        }.onCatch {
            record += "onCatch2\n"
        }.start()

        Thread.sleep(100)
        println("testThen2 start\n" + record + "testThen2 stop\n")
        println("launchTime: ${0.000001 * (launchTime - startTime)}ms")
        println("thenTime1: ${0.000001 * (thenTime1 - launchTime)}ms")
        println("thenTime2: ${0.000001 * (thenTime2 - thenTime1)}ms")
        assert(record == "launch\nonThen1\nonThen2\n")
    }

    @Test
    fun testCatch2() {
        var record = ""

        Promise {
            record += "launch\n"
            it.reject()
        }.onThen { _, _ ->
            record += "onThen1\n"
        }.onThen { _, _ ->
            record += "onThen2\n"
        }.onCatch(Dispatchers.IO) {
            record += "onCatch1\n"
        }.onCatch(Dispatchers.Unconfined) {
            record += "onCatch2\n"
        }.start()

        Thread.sleep(100)
        println("testCatch2 start\n" + record + "testCatch2 stop\n")
        assert(record == "launch\nonCatch1\nonCatch2\n")
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
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Promise { System.out.printf("%08X\n", it.id) }.start()
        Thread.sleep(100)
    }

    @Test
    fun testCatchLog() {
        Promise {
            it.reject(PromiseResult.Cancel())
        }.onCatchLog().start()
        Thread.sleep(100)
    }

    @Test
    fun testAsync() {
        println("testAsync start")
        Async {
            println(Thread.currentThread().name)
            for (i in 0..100) {
                println(PromiseResolve(Dispatchers.IO, i).await())
            }
            Promise("aaa") { promise ->
                Promise("bbb") {
                    Thread.sleep(500)
                }.onClose {
                    promise.resolve()
                    println("testAsync launch")
                }.launch()
            }.timeout(1000).await()
            println("testAsync success")
        }.onCatch { result, _ ->
            println("${result.result} ${result.msg}")
        }
        Thread.sleep(2000)
        println("testAsync stop")
    }

    @Test
    fun testOnClose() {
        var record = ""
        Promise {
            record += "onClose1\n"
            it.resolve()
        }.onClose {
            record += "onClose11\n"
        }.onClose {
            record += "onClose111\n"
        }.start()

        Promise {
            record += "onClose2\n"
            it.reject()
        }.onClose(Dispatchers.IO) {
            record += "onClose22\n"
        }.onClose(Dispatchers.IO) {
            record += "onClose222\n"
        }.start()

        Thread.sleep(500)
        println(record)
    }

    @Test
    fun testProgress() {
        var str1 = ""
        var str2 = ""
        Promise { promise ->
            repeat(100) {
                str1 += "$it "
                promise.progress(it)
            }
        }.onProgress {
            str2 += "${it.progress} "
        }.launch()
        Thread.sleep(500)
        println(str2)
        assert(str1 == str2)
    }
}