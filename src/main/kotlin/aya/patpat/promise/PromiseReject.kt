package aya.patpat.promise

class PromiseReject : Promise {
    constructor(): this(Dispatchers.Unconfined, "", null)
    constructor(result: String): this(Dispatchers.Unconfined, "", PromiseResult(result, ""))
    constructor(result: String, msg: String): this(Dispatchers.Unconfined, "", PromiseResult(result, msg))
    constructor(name: String, result: String, msg: String): this(Dispatchers.Unconfined, name, PromiseResult(result, msg))
    constructor(name: String, result: PromiseResult): this(Dispatchers.Unconfined, name, result)
    constructor(dispatcher: PromiseDispatcher): this(dispatcher, "", null)
    constructor(dispatcher: PromiseDispatcher, result: String): this(dispatcher, "", PromiseResult(result, ""))
    constructor(dispatcher: PromiseDispatcher, result: PromiseResult?): this(dispatcher, "", result)
    constructor(dispatcher: PromiseDispatcher, name: String, result: String): this(dispatcher, name, PromiseResult(result, ""))
    constructor(dispatcher: PromiseDispatcher, name: String, result: String, msg: String): this(dispatcher, name, PromiseResult(result, msg))
    constructor(dispatcher: PromiseDispatcher, name: String, result: PromiseResult?): super(dispatcher, name, { it.reject(result ?: PromiseResult.Failure()) }) {
        mResult = result ?: PromiseResult.Failure()
    }
}