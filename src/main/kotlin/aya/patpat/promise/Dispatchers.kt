package aya.patpat.promise

object Dispatchers {

    @JvmStatic
    val Default = PromiseDispatcher("Default")

    @JvmStatic
    val Main = PromiseDispatcher("Main")

    @JvmStatic
    val IO = PromiseDispatcher("IO")

    @JvmStatic
    val Unconfined = PromiseDispatcher("Unconfined")
}