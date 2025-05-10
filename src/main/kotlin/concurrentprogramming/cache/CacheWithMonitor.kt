import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

private class CacheWithMonitorHolder<K, V>(
  private val transform: (K) -> V
) {
  private var value: V? = null
  private var computed = false
  private var computing = false
  private val guard = ReentrantLock()
  private val condition = guard.newCondition()

  @Throws(TimeoutException::class, InterruptedException::class)
  fun getValue(key: K, timeout: Duration): V {
    guard.withLock {
      if (computed) return value!!

      if (computing) {
        var timeLeft = timeout.inWholeNanoseconds

        while (true) {
          if (computed) return value!!

          timeLeft = condition.awaitNanos(timeLeft)

          if (timeLeft <= 0) {
            throw TimeoutException("Timeout reached while waiting for value computation for key: $key")
          }
        }
      }

      computing = true
    }

    // compute outside the lock
    val result = transform(key)

    guard.withLock {
      value = result
      computed = true
      condition.signalAll()
      return result
    }
  }
}


class CacheWithMonitor<K, V>(private val transform: (K) -> V) {
  private val cache = mutableMapOf<K, CacheWithMonitorHolder<K, V>>()
  private val guard = ReentrantLock()

  @Throws(TimeoutException::class, InterruptedException::class)
  fun get(key: K): V {
    val holder = guard.withLock {
      val result = cache[key]
      if (result != null) {
        result
      } else {
        val newHolder = CacheWithMonitorHolder(transform)
        cache[key] = newHolder
        newHolder
      }
    }

    return holder.getValue(key, Duration.INFINITE)
  }
}