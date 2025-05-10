package concurrentprogramming.labs

import kotlin.concurrent.thread

private const val NTHREADS = 1

fun main() {

  val thread = thread(
    start = true,
    isDaemon = true,
    name = "ToastMaker",
  ) {
    makeToast()
  }

}

private fun makeToast() {
  while (true) {
    println("making toast")
  }
}