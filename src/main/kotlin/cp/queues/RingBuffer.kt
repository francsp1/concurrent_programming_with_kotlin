package cp.queues

class RingBuffer<T>(private val capacity: Int) {
    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>
    private var head = 0  // Points to the oldest element
    private var tail = 0  // Points to the next insertion point
    private var size = 0  // Tracks the number of elements

    fun isFull(): Boolean = size == capacity
    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = size > 0

    fun enqueue(value: T) {
        if (isFull()) {
            // Move head forward when full, to overwrite the oldest element
            head = (head + 1) % capacity
        } else {
            size++  // Only increment size if the buffer isn't full
        }

        // Insert the new value at `tail`
        buffer[tail] = value
        tail = (tail + 1) % capacity  // Wrap around when tail reaches capacity
    }

    // Dequeue method to remove and return the oldest element
    fun dequeue(): T? {
        if (isEmpty()) return null  // Return null if the buffer is empty

        // Get the value at `head` (oldest element)
        val value = buffer[head]
        buffer[head] = null  // Avoid memory leak by clearing the reference

        // Move `head` to the next position, wrapping around if necessary
        head = (head + 1) % capacity

        // Decrease size when an element is removed
        size--

        return value
    }

    // Peek method to view the oldest element without removing it
    fun peek(): T? {
        return if (isEmpty()) null else buffer[head]
    }

    // Method to print the current elements in the buffer
    fun printBuffer() {
        println("Buffer contents: ")
        for (i in 0 until size) {
            val index = (head + i) % capacity
            print("${buffer[index]} ")
        }
        println()
    }
}