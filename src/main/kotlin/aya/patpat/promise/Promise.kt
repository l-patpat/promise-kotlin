package aya.patpat.promise

import aya.patpat.promise.action.Action
import aya.patpat.promise.action.ActionCatch
import aya.patpat.promise.action.ActionPromise
import aya.patpat.promise.action.ActionThen
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

open class Promise {

    companion object {
        var defaultLaunchDispatcher: PromiseDispatcher = Dispatchers.IO
        var defaultThenDispatcher: PromiseDispatcher = Dispatchers.Main
        var defaultCatchDispatcher: PromiseDispatcher = Dispatchers.Main
        var defaultProgressDispatcher: PromiseDispatcher = Dispatchers.Main
        var defaultCloseDispatcher: PromiseDispatcher = Dispatchers.Main

        private const val STATE_INIT = 0
        private const val STATE_LAUNCH = 1
        private const val STATE_RETRY = 2
        private const val STATE_RESOLVE = 3
        private const val STATE_REJECT = 4
        private const val STATE_CLOSE = 5

        @JvmStatic
        val runtimeId = RuntimeId(6 * 24 * 60 * 60)

        private val sTimeoutThreadPool = Executors.newScheduledThreadPool(1)
        private val sPromiseMap = HashMap<Int, Promise>()

        @JvmStatic
        inline fun <reified T : Any>parseData(result: PromiseResult?): T? {
            result ?: return null
            if (result !is PromiseResult.SuccessWith<*>) return null
            return parseData(result.data)
        }

        @JvmStatic
        inline fun <reified T : Any>parseData(data: Any?): T? {
            data ?: return null
            if (data !is T) return null
            return data
        }

        @JvmStatic
        fun resolve(id: Int, data: Any? = null) = sPromiseMap[id]?.resolve(data)

        @JvmStatic
        fun reject(id: Int, err: PromiseResult) = sPromiseMap[id]?.reject(err)
    }

    val id = generateId()

    private var mName = ""
    val name: String
        get() {
            return mName
        }

    private var mAllowRetryTimes = 0
    var retryTimes = 0
        private set

    class Flags {
        var started = false
        var sync = false
        var resolved = false
        var rejected = false
    }
    private val mFlags = Flags()
    private var mState = STATE_INIT
    private var mTimeoutFuture: ScheduledFuture<*>? = null
    private var mTimeoutMillis = 0L
    private val mLaunchFunc: (promise: Promise) -> Unit
    private val mLaunchDispatcher: PromiseDispatcher
    private var mLaunchJob: Job? = null
    private var mLaunchBlock: suspend CoroutineScope.() -> Unit = {}
    private val mOnThenBlocks = ArrayList<(PromiseResult) -> Deferred<PromiseResult>>()
    private val mOnCatchBlocks = ArrayList<(PromiseResult) -> Deferred<Any?>>()
    private var mOnProgressBlocks = ArrayList<(PromiseProgress) -> Deferred<Any?>>()
    private var mOnCloseBlocks = ArrayList<() -> Deferred<Unit>>()
    private var mProgressQueue = ArrayList<PromiseProgress>()

    open var mResult: PromiseResult? = null
    private val mAwaitLock = Object()

    constructor(action: ActionPromise) : this(defaultLaunchDispatcher, "", { action.run(it) })
    constructor(func: (promise: Promise) -> Unit) : this(defaultLaunchDispatcher, "", func)
    constructor(name: String, action: ActionPromise) : this(defaultLaunchDispatcher, name, { action.run(it) })
    constructor(name: String, func: (promise: Promise) -> Unit) : this(defaultLaunchDispatcher, name, func)
    constructor(dispatcher: PromiseDispatcher, action: ActionPromise) : this(dispatcher, "", { action.run(it) })
    constructor(dispatcher: PromiseDispatcher, func: (promise: Promise) -> Unit) : this(dispatcher, "", func)
    constructor(dispatcher: PromiseDispatcher, name: String, action: ActionPromise): this(dispatcher, name, { action.run(it) })
    constructor(dispatcher: PromiseDispatcher, name: String, func: (promise: Promise) -> Unit) {
        mName = name
        mLaunchDispatcher = dispatcher
        mLaunchFunc = { promise ->
            try {
                func(promise)
            } catch (e: PromiseException) {
                reject(e.result)
            } catch (e: Exception) {
                reject(PromiseResult.ErrInternal(e.message))
            }
        }
    }

    fun isFinished(): Boolean {
        return when (mState) {
            STATE_RESOLVE, STATE_REJECT, STATE_CLOSE -> true
            else -> false
        }
    }

    fun close() {
        synchronized(mState) {
            closeReal()
        }
    }

    private fun closeReal() {
        when (mState) {
            STATE_CLOSE -> return
            STATE_RESOLVE, STATE_REJECT -> {}
            else -> notifyResult(PromiseResult.Abort())
        }
        mState = STATE_CLOSE
        mLaunchJob?.cancel()
        mLaunchJob = null
        mLaunchBlock = {}
        mTimeoutFuture?.cancel(true)
        mTimeoutFuture = null
        sPromiseMap.remove(id)
        mOnThenBlocks.clear()
        mOnCatchBlocks.clear()
        GlobalScope.launch {
            for (it in mOnCloseBlocks) {
                it().await()
            }
            mOnCloseBlocks.clear()
        }
    }

    fun retry(times: Int): Promise {
        mAllowRetryTimes = times
        return this
    }

    fun timeout(timeoutMillis: Long): Promise {
        mTimeoutMillis = timeoutMillis
        return this
    }

    private fun generateId(): Int {
        var id: Int
        do {
            id = runtimeId.generate()
        } while (sPromiseMap[id] != null)
        return id
    }

    open fun makeSuccessResult(data: Any?): PromiseResult {
        return when (data) {
            null, Unit -> PromiseResult.Success()
            else -> PromiseResult.SuccessWith(data)
        }
    }

    private fun notifyResult(result: PromiseResult) {
        mResult = result
        synchronized(mAwaitLock) {
            mAwaitLock.notify()
        }
    }

    private fun startTimeoutCount() {
        mTimeoutFuture?.cancel(true)
        mTimeoutFuture = when {
            mTimeoutMillis > 0 -> {
                sTimeoutThreadPool.schedule({
                    reject(PromiseResult.Timeout())
                }, mTimeoutMillis, TimeUnit.MILLISECONDS)
            }
            else -> null
        }
    }

    fun launch() = launch(false)
    fun launch(await: Boolean): Any? {
        mFlags.sync = true
        if (await && mLaunchDispatcher.type == PromiseDispatcher.TYPE_UNCONFINED) {
            throw PromiseException(this, PromiseResult.Failure("禁止在 Dispatchers.Unconfined 中使用 await"))
        }
        return start(await)
    }
    fun await() = start(true)
    fun start() = start(false)
    private fun start(await: Boolean): Any? {
        if (await) {
            if (this is PromiseResolve || this is PromiseReject) {
                val result = mResult ?: throw PromiseException(this, PromiseResult.ErrInternal())
                if (!result.isSuccess) throw PromiseException(this, result)
                return parseData(result)
            } else if (mLaunchDispatcher.type == PromiseDispatcher.TYPE_UNCONFINED) {
                throw PromiseException(this, PromiseResult.Failure("禁止在 Dispatchers.Unconfined 中使用 await"))
            }
        }
        if (mState != STATE_INIT) throw PromiseException(this, PromiseResult.Failure("重复操作"))
        mState = STATE_LAUNCH

        if (mAllowRetryTimes < 0) mAllowRetryTimes = 0
        retryTimes = 0
        mLaunchBlock = {
            var state: Int
            do {
                startTimeoutCount()
                mState = STATE_LAUNCH
                mFlags.resolved = false
                mFlags.rejected = false
                mFlags.started = true
                if (!mFlags.sync) sPromiseMap[id] = this@Promise
                mLaunchFunc(this@Promise)
                synchronized(mState) {
                    mFlags.started = false
                    if (mFlags.sync && !mFlags.resolved && !mFlags.rejected) {
                        mState = STATE_RESOLVE
                        notifyResult(PromiseResult.Success())
                        closeReal()
                    }
                    state = mState
                }
            } while (state == STATE_RETRY)
        }
        val job = GlobalScope.launch(mLaunchDispatcher.instance, CoroutineStart.LAZY, mLaunchBlock)
        mLaunchJob = job

        if (await) {
            synchronized(mAwaitLock) {
                job.start()
                try { mAwaitLock.wait() }
                catch (e: InterruptedException) {  }
            }
        } else {
            job.start()
            return null
        }

        val result = mResult ?: throw PromiseException(this, PromiseResult.ErrInternal())
        if (!result.isSuccess) throw PromiseException(this, result)
        return parseData(result)
    }

    fun resolve() = resolve(null)
    fun resolve(data: Any?) {
        synchronized(mState) {
            if (mState != STATE_LAUNCH) return
            mFlags.resolved = true
            mState = STATE_RESOLVE
            GlobalScope.launch {
                var result = makeSuccessResult(data)
                for (it in mOnThenBlocks) {
                    result = it(result).await()
                    if (!result.`is`(PromiseResult.SUCCESS)) {
                        reject(result)
                        return@launch
                    }
                }
                notifyResult(result)
                close()
            }
        }
    }

    fun reject() = reject(PromiseResult.Failure())
    fun reject(result: String, msg: String) = reject(PromiseResult(result, msg))
    fun reject(result: PromiseResult) {
        synchronized(mState) {
            if (mState != STATE_LAUNCH) return
            mFlags.resolved = true
            if (retryTimes < mAllowRetryTimes) {
                retryTimes++
                if (mFlags.started) {
                    mState = STATE_RETRY
                } else {
                    mState = STATE_LAUNCH
                    mLaunchJob = GlobalScope.launch(mLaunchDispatcher.instance, CoroutineStart.DEFAULT, mLaunchBlock)
                }
                return
            }
            mState = STATE_REJECT
            GlobalScope.launch {
                for (it in mOnCatchBlocks) {
                    it(result).await()
                }
                notifyResult(result)
                close()
            }
        }
    }

    fun progress(progress: Int) = progress(PromiseProgress(progress, null))
    fun progress(progress: Int, result: String) = progress(PromiseProgress(progress, PromiseResult(result)))
    fun progress(progress: Int, result: String, msg: String) = progress(PromiseProgress(progress, PromiseResult(result, msg)))
    fun progress(progress: Int, result: PromiseResult?) = progress(PromiseProgress(progress, result))
    fun progress(progress: PromiseProgress) {
        var isEmpty: Boolean
        synchronized(mProgressQueue) {
            isEmpty = mProgressQueue.isEmpty()
            mProgressQueue.add(progress)
        }
        if (isEmpty) {
            GlobalScope.launch {
                while (true) {
                    var p: PromiseProgress? = null
                    synchronized(mProgressQueue) {
                        if (mProgressQueue.isNotEmpty()) {
                            p = mProgressQueue[0]
                        }
                    }
                    p ?: break
                    for (it in mOnProgressBlocks) {
                        it(p!!).await()
                    }
                    synchronized(mProgressQueue) {
                        mProgressQueue.remove(p!!)
                    }
                }
            }
        }
    }

    fun onThen(action: ActionThen) = onThen(defaultThenDispatcher) { data, resolve -> action.run(data, resolve) }
    fun onThen(func: (data: Any?, resolve: (data: Any?) -> Unit) -> Unit) = onThen(defaultThenDispatcher, func)
    fun onThen(dispatcher: PromiseDispatcher, action: ActionThen) = onThen(dispatcher) { data, resolve -> action.run(data, resolve) }
    fun onThen(dispatcher: PromiseDispatcher, func: (data: Any?, resolve: (data: Any?) -> Unit) -> Unit): Promise {
        mOnThenBlocks.add { result ->
            GlobalScope.async(dispatcher.instance) {
                try {
                    var res = result
                    val data = if (res is PromiseResult.SuccessWith<*>) res.data else null
                    func(data) {
                        res = makeSuccessResult(it)
                    }
                    res
                } catch (e: PromiseException) {
                    e.result
                } catch (e: Exception) {
                    PromiseResult.ErrInternal(e.message.toString())
                }
            }
        }
        return this
    }

    fun onCatch(action: ActionCatch) = onCatch(defaultCatchDispatcher) { action.run(it) }
    fun onCatch(func: (result: PromiseResult) -> Unit) = onCatch(defaultCatchDispatcher, func)
    fun onCatch(dispatcher: PromiseDispatcher, action: ActionCatch) = onCatch(dispatcher) { action.run(it) }
    fun onCatch(dispatcher: PromiseDispatcher, func: (err: PromiseResult) -> Unit): Promise {
        mOnCatchBlocks.add { result ->
            GlobalScope.async(dispatcher.instance) {
                try { func(result) }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
        return this
    }
    fun onCatchLog(): Promise = onCatchLog(true)
    fun onCatchLog(debug: Boolean): Promise {
        return if (debug) {
            onCatch {
                val sb = StringBuilder()
                sb.appendln()
                sb.appendln("----------------------------------------")
                sb.appendln("<promise: id: $id name: ${if (name.isBlank()) "<empty>" else name}>")
                sb.appendln("onCatch")
                sb.appendln("result: ${it.result}")
                sb.appendln("msg: ${it.msg}")
                sb.appendln("----------------------------------------")
                println(sb.toString())
            }
        } else this
    }

    fun onProgress(action: Action<PromiseProgress>) = onProgress(defaultProgressDispatcher) { action.run(it) }
    fun onProgress(func: (PromiseProgress) -> Unit) = onProgress(defaultProgressDispatcher, func)
    fun onProgress(dispatcher: PromiseDispatcher, action: Action<PromiseProgress>) = onProgress(dispatcher) { action.run(it) }
    fun onProgress(dispatcher: PromiseDispatcher, func: (PromiseProgress) -> Unit): Promise {
        mOnProgressBlocks.add { progress ->
            GlobalScope.async(dispatcher.instance) {
                try { func(progress) }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
        return this
    }

    fun onClose(runnable: Runnable) = onClose(defaultCloseDispatcher, runnable)
    fun onClose(dispatcher: PromiseDispatcher, runnable: Runnable) = onClose(dispatcher) { runnable.run() }
    fun onClose(func: () -> Unit) = onClose(defaultCloseDispatcher, func)
    fun onClose(dispatcher: PromiseDispatcher, func: () -> Unit): Promise {
        mOnCloseBlocks.add {
            GlobalScope.async(dispatcher.instance) {
                try { func() }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
        return this
    }
}
