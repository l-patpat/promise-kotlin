package aya.patpat.promise

class PromiseException : Exception {

    val promise: Promise?
    val result: PromiseResult

    constructor(result: String) : this(PromiseResult(result))
    constructor(result: String, msg: String) : this(PromiseResult(result, msg))
    constructor(result: PromiseResult) : super(result.msg) {
        this.promise = null
        this.result = result
    }
    constructor(promise: Promise, result: String) : this(promise, PromiseResult(result))
    constructor(promise: Promise, result: String, msg: String) : this(promise, PromiseResult(result, msg))
    constructor(promise: Promise, result: PromiseResult) : super(result.msg) {
        this.promise = promise
        this.result = result
    }
}