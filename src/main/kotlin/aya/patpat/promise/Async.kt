package aya.patpat.promise

import aya.patpat.promise.action.AsyncCatchAction
import aya.patpat.result.GlobalResult
import aya.patpat.result.GlobalResultException
import java.lang.Exception
import java.util.concurrent.Executors

class Async {

    companion object {
        private val sThreadPool = Executors.newCachedThreadPool()
    }

    private var mCatchAction: AsyncCatchAction? = null

    constructor(runnable: Runnable) : this({ runnable.run() })
    constructor(func: () -> Unit) {
        sThreadPool.submit {
            try {
                func()
            } catch (e: PromiseException) {
                mCatchAction?.run(e.result, e.promise)
            } catch (e: GlobalResultException) {
                mCatchAction?.run(e.result, null)
            } catch (e: Exception) {
                mCatchAction?.run(GlobalResult.ErrInternal(e.message), null)
            }
        }
    }

    fun onCatch(func: (result: GlobalResult, promise: Promise?) -> Unit) = onCatch(AsyncCatchAction { result, promise -> func(result, promise) })
    fun onCatch(action: AsyncCatchAction) {
        mCatchAction = action
    }
}