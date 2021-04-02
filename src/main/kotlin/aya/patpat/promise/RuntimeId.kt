package aya.patpat.promise

import java.security.InvalidParameterException

private const val ID_BITS = 32
private const val TIME_PART_BITS = 19
private const val TIME_PART_MAX = 1.shl(TIME_PART_BITS) - 1
private const val COUNT_PART_BITS = ID_BITS - 1 - TIME_PART_BITS
private const val COUNT_PART_MAX = 1.shl(COUNT_PART_BITS) - 1

class RuntimeId(private val cycle: Int = 6 * 24 * 60 * 60) {

    private var mLastTime = 0L
    private var mCount = 0

    init {
        if (cycle < 1 || cycle > TIME_PART_MAX) {
            throw InvalidParameterException("Runtime id max range is 1 to $TIME_PART_MAX")
        }
    }

    fun check(id: Int): Boolean {
        val max = cycle.shl(COUNT_PART_BITS).or(COUNT_PART_MAX)
        return id in 0..max
    }

    fun generate(): Int {
        val time = (System.currentTimeMillis() / 1000) % cycle
        if (time == mLastTime) {
            mCount++
            mCount %= COUNT_PART_MAX
        } else {
            mCount = 0
            mLastTime = time
        }
        return (time.shl(COUNT_PART_BITS).and(COUNT_PART_MAX.toLong().inv()) + mCount).toInt()
    }
}