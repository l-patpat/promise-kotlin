package aya.patpat.promise

import aya.patpat.promise.action.ActionAsyncCatch
import java.lang.Exception
import java.util.concurrent.Executors

class Async {

    companion object {
        private val sThreadPool = Executors.newCachedThreadPool()
    }

    private var mCatchAction: ActionAsyncCatch? = null

    constructor(runnable: Runnable) : this({ runnable.run() })
    constructor(func: () -> Unit) {
        sThreadPool.submit {
            try {
                func()
            } catch (e: PromiseException) {
                mCatchAction?.run(e.result, e.promise)
            } catch (e: Exception) {
                mCatchAction?.run(PromiseResult.ErrInternal(e.message), null)
            }
        }
    }

    fun onCatch(func: (result: PromiseResult, promise: Promise?) -> Unit) = onCatch(ActionAsyncCatch { result, promise -> func(result, promise) })
    fun onCatch(action: ActionAsyncCatch) {
        mCatchAction = action
    }
}