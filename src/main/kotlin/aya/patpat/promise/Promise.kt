package aya.patpat.promise

import aya.patpat.promise.action.Action
import aya.patpat.result.GlobalResult
import aya.patpat.result.GlobalResultException
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Promise {

    companion object {
        private const val STATE_INIT = 0
        private const val STATE_LAUNCH = 1
        private const val STATE_RETRY = 2
        private const val STATE_RESOLVE = 3
        private const val STATE_REJECT = 4
        private const val STATE_CLOSE = 5

        private val sIdFactory = IdFactory(6 * 24 * 60 * 60)
        private val sTimeoutThreadPool = Executors.newScheduledThreadPool(1)
        private val sPromiseMap = HashMap<Int, Promise>()

        @JvmStatic
        inline fun <reified T : Any>parseData(result: GlobalResult?): T? {
            result ?: return null
            if (result !is GlobalResult.SuccessWith<*>) return null
            return parseData(result.data)
        }

        @JvmStatic
        inline fun <reified T : Any>parseData(data: Any?): T? {
            data ?: return null
            if (data !is T) return null
            return data
        }

        @JvmStatic
        fun handleResult(result: GlobalResult.Normal<*>) {
            if (result.isSuccess) {
                resolve(result.id.toInt(), result.data)
            } else {
                reject(result.id.toInt(), result)
            }
        }

        @JvmStatic
        fun resolve(id: Int, data: Any? = null) {
            sPromiseMap[id]?.resolve(data)
        }

        @JvmStatic
        fun reject(id: Int, err: GlobalResult) {
            sPromiseMap[id]?.reject(err)
        }
    }

    val id = generateId()

    private var nName = ""
    val name: String
        get() {
            return nName
        }

    private var mAllowRetryTimes = 0
    var retryTimes = 0
        private set

    private object mFlags {
        var launch = false
        var extern = false
        var resolve = false
        var reject = false
    }
    private var mState = STATE_INIT
    private var mTimeoutFuture: ScheduledFuture<*>? = null
    private var mTimeoutMillis = 0L
    private val mLaunchAction: Action<Promise>
    private val mLaunchDispatcher: PromiseDispatcher
    private var mLaunchJob: Job? = null
    private var mLaunchBlock: suspend CoroutineScope.() -> Unit = {}
    private lateinit var mResolveAction: Action<Any?>
    private lateinit var mRejectAction: Action<GlobalResult>
    private lateinit var mBeforeResolveAction: Action<Any?>
    private lateinit var mBeforeRejectAction: Action<GlobalResult>
    private var mResult: GlobalResult? = null
    private val mAwaitLock = Object()

    constructor() : this(Dispatchers.Default, "", Action<Promise> { })
    constructor(func: (promise: Promise) -> Unit) : this(Dispatchers.Default, "", Action<Promise> { func(it) })
    constructor(action: Action<Promise>) : this(Dispatchers.Default, "", action)
    constructor(name: String, func: (promise: Promise) -> Unit) : this(Dispatchers.Default, name, Action<Promise> { func(it) })
    constructor(name: String, action: Action<Promise>) : this(Dispatchers.Default, name, action)
    constructor(dispatcher: PromiseDispatcher, func: (promise: Promise) -> Unit) : this(dispatcher, "", Action<Promise> { func(it) })
    constructor(dispatcher: PromiseDispatcher, action: Action<Promise>) : this(dispatcher, "", action)
    constructor(dispatcher: PromiseDispatcher, name: String, func: (promise: Promise) -> Unit) : this(dispatcher, name, Action<Promise> { func(it) })
    constructor(dispatcher: PromiseDispatcher, name: String, action: Action<Promise>) {
        nName = name
        onBeforeThen {  }
        onThen {  }
        onBeforeCatch {  }
        onCatch {  }
        mLaunchDispatcher = dispatcher
        mLaunchAction = Action { promise ->
            try {
                action.run(promise)
            } catch (e: Exception) {
                reject(GlobalResult.ErrInternal(e.message.toString()))
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
            else -> notifyResult(GlobalResult.Abort())
        }
        mState = STATE_CLOSE
        mLaunchJob?.cancel()
        mLaunchJob = null
        mLaunchBlock = {}
        mTimeoutFuture?.cancel(true)
        mTimeoutFuture = null
        sPromiseMap.remove(id)
    }

    fun extern() {
        synchronized(mState) {
            if (isFinished()) return
            sPromiseMap[id] = this
            mFlags.extern = true
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
            id = sIdFactory.generate()
        } while (sPromiseMap[id] != null)
        return id
    }

    private fun makeSuccessResult(data: Any?): GlobalResult {
        return when (data) {
            null -> GlobalResult.Success()
            else -> GlobalResult.SuccessWith(data)
        }
    }

    private fun notifyResult(result: GlobalResult) {
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
                    reject(GlobalResult.Timeout())
                }, mTimeoutMillis, TimeUnit.MILLISECONDS)
            }
            else -> null
        }
    }

    fun await(): Any? {
        if (mLaunchDispatcher.type == PromiseDispatcher.TYPE_UNCONFINED) throw PromiseException(this, GlobalResult.Failure("禁止在 Dispatchers.Unconfined 中使用 await"))
        return launch(true)
    }

    fun launch() {
        launch(false)
    }

    private fun launch(await: Boolean): Any? {
        if (mState != STATE_INIT) throw PromiseException(this, GlobalResult.Failure("重复操作"))
        mState = STATE_LAUNCH

        if (mAllowRetryTimes < 0) mAllowRetryTimes = 0
        retryTimes = 0
        mFlags.extern = false
        mLaunchBlock = {
            try {
                var state: Int
                do {
                    startTimeoutCount()
                    mState = STATE_LAUNCH
                    mFlags.resolve = false
                    mFlags.reject = false
                    mFlags.launch = true
                    mLaunchAction.run(this@Promise)
                    synchronized(mState) {
                        mFlags.launch = false
                        if (!mFlags.extern && !mFlags.resolve && !mFlags.reject) {
                            mState = STATE_RESOLVE
                            notifyResult(GlobalResult.Success())
                            closeReal()
                        }
                        state = mState
                    }
                } while (state == STATE_RETRY)
            } catch (e: GlobalResultException) {
                reject(e.result)
            } catch (e: Exception) {
                reject(GlobalResult.ErrInternal(e.message.toString()))
            }
        }
        mLaunchJob = GlobalScope.launch(mLaunchDispatcher.instance, CoroutineStart.DEFAULT, mLaunchBlock)

        if (!await) return null

        synchronized(mAwaitLock) {
            try { mAwaitLock.wait() }
            catch (e: InterruptedException) {  }
        }

        val result = mResult ?: throw PromiseException(this, GlobalResult.ErrInternal())
        if (!result.isSuccess) throw PromiseException(this, result)
        return parseData(result)
    }

    private fun shouldLaunch(): Boolean {
        synchronized(mState) {
            if (isFinished()) return false
            mState = STATE_LAUNCH
            return true
        }
    }


    fun resolve() = resolve(null)
    fun resolve(data: Any?) {
        synchronized(mState) {
            if (mState != STATE_LAUNCH) return
            mFlags.resolve = true
            mState = STATE_RESOLVE
            mBeforeResolveAction.run(data)
        }
    }

    fun reject() = reject(GlobalResult.Failure())
    fun reject(err: GlobalResult) {
        synchronized(mState) {
            if (mState != STATE_LAUNCH) return
            mFlags.resolve = true
            if (retryTimes < mAllowRetryTimes) {
                retryTimes++
                if (mFlags.launch) {
                    mState = STATE_RETRY
                } else {
                    mState = STATE_LAUNCH
                    mLaunchJob = GlobalScope.launch(mLaunchDispatcher.instance, CoroutineStart.DEFAULT, mLaunchBlock)
                }
                return
            }
            mState = STATE_REJECT
            mBeforeRejectAction.run(err)
        }
    }

    fun onBeforeThen(func: (data: Any?) -> Unit): Promise = onBeforeThen(Dispatchers.Default, func)
    fun onBeforeThen(dispatcher: PromiseDispatcher, func: (data: Any?) -> Unit): Promise = onBeforeThen(dispatcher, Action { value -> func(value) })
    fun onBeforeThen(action: Action<Any?>): Promise = onBeforeThen(Dispatchers.Default, action)
    fun onBeforeThen(dispatcher: PromiseDispatcher, action: Action<Any?>): Promise {
        mBeforeResolveAction = Action { data ->
            GlobalScope.launch(dispatcher.instance) {
                try {
                    action.run(data)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mResolveAction.run(data)
            }
        }
        return this
    }
    fun onThen(func: (data: Any?) -> Unit): Promise = onThen(Dispatchers.Default, func)
    fun onThen(dispatcher: PromiseDispatcher, func: (data: Any?) -> Unit): Promise = onThen(dispatcher, Action { value -> func(value) })
    fun onThen(action: Action<Any?>): Promise = onThen(Dispatchers.Default, action)
    fun onThen(dispatcher: PromiseDispatcher, action: Action<Any?>): Promise {
        mResolveAction = Action { data ->
            GlobalScope.launch(dispatcher.instance) {
                try {
                    val result = makeSuccessResult(data)
                    action.run(data)
                    notifyResult(result)
                    close()
                } catch (e: Exception) {
                    reject(GlobalResult.ErrInternal(e.message.toString()))
                }
            }
        }
        return this
    }

    fun onBeforeCatch(func: (err: GlobalResult) -> Unit): Promise = onBeforeCatch(Dispatchers.Default, func)
    fun onBeforeCatch(dispatcher: PromiseDispatcher, func: (err: GlobalResult) -> Unit): Promise = onBeforeCatch(dispatcher, Action { value -> func(value) })
    fun onBeforeCatch(action: Action<GlobalResult>): Promise = onBeforeCatch(Dispatchers.Default, action)
    fun onBeforeCatch(dispatcher: PromiseDispatcher, action: Action<GlobalResult>): Promise {
        mBeforeRejectAction = Action { result ->
            GlobalScope.launch(dispatcher.instance) {
                try {
                    action.run(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mRejectAction.run(result)
            }
        }
        return this
    }
    fun onCatch(func: (err: GlobalResult) -> Unit): Promise = onCatch(Dispatchers.Default, func)
    fun onCatch(dispatcher: PromiseDispatcher, func: (err: GlobalResult) -> Unit): Promise = onCatch(dispatcher, Action { value -> func(value) })
    fun onCatch(action: Action<GlobalResult>): Promise = onCatch(Dispatchers.Default, action)
    fun onCatch(dispatcher: PromiseDispatcher, action: Action<GlobalResult>): Promise {
        mRejectAction = Action { result ->
            GlobalScope.launch(dispatcher.instance) {
                try {
                    action.run(result)
                    notifyResult(result)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                close()
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
                sb.appendln("<promise: ${if (name.isEmpty()) hashCode().toString(16) else name }>")
                sb.appendln("onCatch")
                sb.appendln("resultId: ${it.id}")
                sb.appendln("result: ${it.result}")
                sb.appendln("msg: ${it.msg}")
                sb.appendln("----------------------------------------")
                println(sb.toString())
            }
        } else this

    }
}
