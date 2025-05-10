package concurrentprogramming.labs

private const val NTHREADS = 1

fun main() {

  repeat(NTHREADS) {
    Thread.ofPlatform().start {
      makeToast()
    }
  }

  /*
  Thread {
    makeToast()
  }.start()

  Thread.Virtual().start {
      makeToast()
  }

  */
}

private fun makeToast() {
while (true) {
  println("making toast")
}
}