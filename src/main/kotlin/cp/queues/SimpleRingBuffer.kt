package cp.queues



class SimpleRingBuffer<T>(private val capacity: Int) {
    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>

    private var numberOfElements = 0  // Tracks the number of elements

    private var head = 0  // Points to the oldest element (physical position)
    private var tail = 0  // Points to the next insertion point (physical position)

    fun isFull(): Boolean = numberOfElements == capacity
    fun isEmpty(): Boolean = numberOfElements == 0
    fun isNotEmpty(): Boolean = numberOfElements > 0

    fun enqueue(value: T) {
        if (isFull()) {
            head = (head + 1) % capacity
        } else {
            numberOfElements++
        }
        buffer[tail] = value
        tail = (tail + 1) % capacity
    }

    // Dequeue method to remove and return the oldest element
    fun dequeue(): T? {
        if (isEmpty()) return null
        val value = buffer[head]
        //buffer[head] = null
        head = (head + 1) % capacity
        numberOfElements--
        return value
    }

    fun numberOfElements(): Int {
        return numberOfElements
    }

    // Peek method to view the oldest element without removing it
    fun peek(): T? {
        return if (isEmpty()) null else buffer[head]
    }

    /**
     * Prints the buffer contents from oldest to latest for debug proposes.
     */
    fun print() {
        println("Buffer contents: ")
        for (i in 0 until numberOfElements) {
            val index = (head + i) % capacity
            println(" buffer[$index]: ${buffer[index]} ")
        }
        println()
    }

    /**
     * Prints all elements in the buffer from 0 to max [capacity] for debug proposes.
     */
    fun printAllElements() {
        println("Debug: All buffer elements from 0 to capacity:")
        for (i in 0 until capacity) {
            println(" buffer[$i]: ${buffer[i]} ")
        }
        println()
    }

    /**
     * Clears the buffer by setting all elements to null and resetting indices.
     */
    fun clear() {
        for (i in 0 until capacity) {
            buffer[i] = null
        }
        numberOfElements = 0
        head = 0
        tail = 0
    }
}