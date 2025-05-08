package concurrentprogramming.threadscope

import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implement the ThreadScope class, responsible for holding a set of related threads. Each ThreadScope
 * instance is constructed with a name (of type String) and a thread builder (of type Thread.Builder),
 * and supports the following operations:
 * • startThread` - starts a new thread managed by the scope, given a Runnable.
 * • newChildScope - creates a new child thread scope, given a name.
 * • close - closes the scope, disallowing the creation of any further thread or child scope. The close operation is idempotent.
 * • cancel - interrupts all threads in the scope and cancels all child scopes. A cancel operation performs an implicit close.
 * • join - waits for a scope to be completed, which is defined by all started threads being terminated and all child scopes being completed.
 */
class ThreadScope(
  private val scopeName: String,
  private val builder: Thread.Builder
) : Closeable {

  private val guard = ReentrantLock()
  private val threads = mutableListOf<Thread>()
  private val childScopes = mutableListOf<ThreadScope>()
  private var isClosed = false

  // Creates a new thread in the scope, if the scope is not closed.
  fun startThread(threadName: String, runnable: Runnable): Thread? {
    guard.withLock {
      // Ensure scope is not closed
      if (isClosed) return null

      return try {
        val thread = builder.name("$scopeName-$threadName").start(runnable)
        threads.add(thread)  // Add to the thread list
        thread  // Return the thread
      } catch (e: Exception) {
        //println("Failed to start thread: ${e.message}")
        null
      }
    }
  }

  // Creates a new child scope, if the current scope is not closed.
  fun newChildScope(name: String): ThreadScope? {
    guard.withLock {
      if (isClosed) return null

      val childScope = ThreadScope(scopeName = "$scopeName-$name", builder = builder)
      childScopes.add(childScope)
      return childScope
    }
  }

  // Closes the current scope, disallowing the creation of any further thread or child scope.
  override fun close() {
    guard.withLock {
      if (isClosed) return  // Ensure idempotency
      isClosed = true

      // Close all child scopes
      childScopes.forEach { it.close() }
      childScopes.clear()
    }
  }

  // Interrupts all threads in the scope and cancels all child scopes.
  fun cancel() {
    guard.withLock {
      if (isClosed) return  // Ensure idempotency
      isClosed = true

      // Interrupt all threads
      threads.forEach { it.interrupt() }
      threads.clear()

      // Cancel all child scopes
      childScopes.forEach { it.cancel() }
      childScopes.clear()
    }
  }

  // Waits until all threads and child scopes have completed
  @Throws(InterruptedException::class)
  fun join(timeout: Duration): Boolean {
    guard.withLock {
      val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds

      // Wait for all threads to complete
      for (thread in threads) {
        val remainingTime = deadline - System.currentTimeMillis()
        if (remainingTime <= 0) return false
        thread.join(remainingTime)
        if (thread.isAlive) return false
      }

      // Wait for all child scopes to complete
      for (childScope in childScopes) {
        val remainingTime = deadline - System.currentTimeMillis()
        if (remainingTime <= 0) return false
        if (!childScope.join(timeout = remainingTime.milliseconds)) return false
      }

      return true
    }
  }

  fun isClosed(): Boolean {
    guard.withLock {
      return isClosed
    }
  }

}