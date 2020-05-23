package aya.patpat.promise

import aya.patpat.result.GlobalResult

class PromiseException(val promise: Promise, val result: GlobalResult) : Exception(result.msg)