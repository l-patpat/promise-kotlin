package aya.patpat.promise

import java.security.InvalidParameterException

class IdFactory(private val cycle: Int = 6 * 24 * 60 * 60) {

    private var mLastTime = 0L
    private var mCount = 0

    init {
        if (cycle <= 0 || cycle > 0x7FFFF) {
            throw InvalidParameterException("cycle range is 1-524287")
        }
    }

    fun generate(): Int {
        val time = (System.currentTimeMillis() / 1000) % cycle
        if (time == mLastTime) {
            mCount++
            mCount = mCount.and(0xFFF)
        } else {
            mCount = 0
            mLastTime = time
        }
        return (time.shl(12).and(0xFFFL.inv()) + mCount).toInt()
    }
}