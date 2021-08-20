package aya.patpat.promise

class PromiseResolve : Promise {
    constructor(): this(Dispatchers.Unconfined, "", null)
    constructor(data: Any?): this(Dispatchers.Unconfined, "", data)
    constructor(name: String, data: Any?): this(Dispatchers.Unconfined, name, data)
    constructor(dispatcher: PromiseDispatcher): this(dispatcher, "", null)
    constructor(dispatcher: PromiseDispatcher, data: Any?): this(dispatcher, "", data)
    constructor(dispatcher: PromiseDispatcher, name: String, data: Any?): super(dispatcher, name, { it.resolve(data) })
}