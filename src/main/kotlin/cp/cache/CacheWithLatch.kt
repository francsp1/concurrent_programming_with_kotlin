package isel.leic.pc.demos.my.Cache

import cp.latch.Latch
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private class Holder<K, V>(
    private val transform: (K) -> V,
    private val timeout: Duration
) {
    private var value: V? = null
    private val latch = Latch()
    private val isTransformed = AtomicBoolean(false)

    @Throws(TimeoutException::class, InterruptedException::class)
    fun getValue(key: K): V {
        if (isTransformed.compareAndSet(false, true)) { // If a thread is not already computing value (isComputing == false), start computing and set isComputing to true
            Thread.sleep(3000) //  Simulate long computation (TODO: remove this line later)
            value = transform(key)
            latch.open()
            println("Holder.getValue: Value computed: latch opened")
            return value!!
        }

        try {
            if (!latch.await(timeout)) {
                throw TimeoutException("Timeout while waiting for value computation for key: $key")
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt() // Restore the interrupt flag
            throw ie
        }

        return value!!
    }
}

class CacheWithLatch<K, V>(
    private val transform: (K) -> V,
    private val timeout: Duration
) {
    private val cache = mutableMapOf<K, Holder<K, V>>()
    private val guard = ReentrantLock()

    // Change the return type to V? (nullable)
    fun get(key: K): V? {
        val holder = guard.withLock {
            val result = cache[key]
            if (result != null) {
                result
            }
            else {
                val newHolder = Holder(transform, timeout)
                cache[key] = newHolder
                newHolder
            }
        }

        return try {
            holder.getValue(key)
        } catch (te: TimeoutException) {
            guard.withLock { cache.remove(key) }
            null
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            guard.withLock { cache.remove(key) }
            null
        }
    }
}


fun cacheUsageSample() {
    val cache = CacheWithLatch(transform = { key: String -> key.length }, timeout = 2.seconds)
    val value = cache.get("Hello")  // Should call transform("Hello") = 5
    if (value == null) {
        println("Cache.get: Value is null")
    } else {
        println("Cache.get: Value is $value")
    }
    val anotherValue = cache.get("Hello") // Should NOT call transform("Hello") again
    if (anotherValue == null) {
        println("Cache.get: Value is null")
    } else {
        println("Cache.get: Value is $anotherValue")
    }
}