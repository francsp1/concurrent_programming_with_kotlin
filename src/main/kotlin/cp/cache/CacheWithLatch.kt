
import cp.latch.Latch
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private class CacheWIthLatchHolder<K, V>(
    private val transform: (K) -> V,
    private val timeout: Duration
) {
    private var value: V? = null
    private val latch = Latch()
    private val isTransforming = AtomicBoolean(false)

    @Throws(TimeoutException::class, InterruptedException::class, Exception::class)
    fun getValue(key: K): V {
        if (isTransforming.compareAndSet(false, true)) {
            Thread.sleep(3000) // Simulate long computation (TODO: remove later)
            try {
                value = transform(key) // Transform might fail!
                latch.open()
                println("Holder.getValue: Value computed - latch opened")
            } catch (e: Exception) {
                isTransforming.set(false) // Reset only if transform failed
                throw e
            }
        }
        try {
            if (!latch.await(timeout)) {
                throw TimeoutException("Timeout reached while waiting for value computation for key: $key")
            }
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt() // Restore the interrupt flag
            throw ie
        }
        return value!!
    }

    fun isComputed(): Boolean {
        return latch.isOpen()
    }
}

class CacheWithLatch<K, V>(
    private val transform: (K) -> V,
    private val timeout: Duration
) {
    private val cache = mutableMapOf<K, CacheWIthLatchHolder<K, V>>()
    private val guard = ReentrantLock()

    @Throws(TimeoutException::class, InterruptedException::class, Exception::class)
    fun get(key: K): V? {
        val holder = guard.withLock {
            val result = cache[key]
            if (result != null) {
                result
            }
            else {
                val newHolder = CacheWIthLatchHolder(transform, timeout)
                cache[key] = newHolder
                newHolder
            }
        }

        return try {
            holder.getValue(key)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            if (!holder.isComputed()) {
                guard.withLock { cache.remove(key) }
            }
            throw ie
        } catch (e: Exception) { // Catches both TimeoutException and transform() thrown exceptions
            if (!holder.isComputed()) {
                guard.withLock { cache.remove(key) }
            }
            throw e
        }
    }
}

fun cacheWithLatchUsageSample() {
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