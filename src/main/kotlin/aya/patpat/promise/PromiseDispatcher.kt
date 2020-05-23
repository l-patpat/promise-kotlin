package aya.patpat.promise

import kotlinx.coroutines.CoroutineDispatcher

class PromiseDispatcher(val type: String = TYPE_DEFAULT) {
    companion object {
        @JvmStatic val TYPE_DEFAULT = "Default"
        @JvmStatic val TYPE_MAIN = "Main"
        @JvmStatic val TYPE_IO = "IO"
        @JvmStatic val TYPE_UNCONFINED = "Unconfined"
    }

    val instance: CoroutineDispatcher
        get() {
            return when (type) {
                TYPE_UNCONFINED -> kotlinx.coroutines.Dispatchers.Unconfined
                TYPE_IO -> kotlinx.coroutines.Dispatchers.IO
                TYPE_MAIN -> kotlinx.coroutines.Dispatchers.Main
                else -> kotlinx.coroutines.Dispatchers.Default
            }
        }
}