package com.embabel.agent

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class BravePacer {
    private val lock = ReentrantLock(true)
    private val cond = lock.newCondition()
    private var nextAllowedAtNanos = 0L

    fun <T> pace(block: () -> T): T = lock.withLock {
        var waitNanos = nextAllowedAtNanos - System.nanoTime()
        while (waitNanos > 0) {
            cond.awaitNanos(waitNanos)
            waitNanos = nextAllowedAtNanos - System.nanoTime()
        }
        // run outside of 'await' but still under the lock so start times serialize
        val result = try {
            block()
        } finally {
            nextAllowedAtNanos = System.nanoTime() + SECONDS.toNanos(1)
            cond.signalAll()
        }
        result
    }
}