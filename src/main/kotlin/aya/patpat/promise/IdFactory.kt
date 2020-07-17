package aya.patpat.promise

import java.security.InvalidParameterException

class IdFactory(private val cycle: Int = 12 * 24 * 60 * 60) {

    private var mLastTime = 0L
    private var mCount = 0

    init {
        if (cycle == 0 || cycle > 0xFFFFF) {
            throw InvalidParameterException("cycle range is 1-1048575")
        }
    }

    fun generate(): Int {
        val time = (System.currentTimeMillis() / 1000) % cycle
        if (time == mLastTime) {
            mCount++
            mCount = mCount.and(0xFFF)
        } else {
            mLastTime = time
        }
        return ((time * 4096).and(0xFFFL.inv()) + mCount).toInt()
    }
}