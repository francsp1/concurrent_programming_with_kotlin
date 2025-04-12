package concurrentprogramming.datastructures

class RingBuffer<T>(private val capacity: Int) {
    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>

    private var numberOfElements = 0  // Tracks the number of elements

    private var physicalHead = 0  // Points to the oldest element (physical position)
    private var physicalTail = 0  // Points to the next insertion point (physical position)

    private var logicalHead:Long = 0L  // The index of the oldest element (logical position)
    private var logicalTail:Long = 0L  // The index of the next element to be inserted (logical position)

    fun isFull(): Boolean = numberOfElements == capacity
    fun isEmpty(): Boolean = numberOfElements == 0
    fun isNotEmpty(): Boolean = numberOfElements > 0

    fun enqueue(value: T) {
        if (isFull()) {
            // Overwrite the oldest element:
            // Move physicalHead forward and update logicalHead accordingly.
            physicalHead = (physicalHead + 1) % capacity
            logicalHead++
        } else {
            numberOfElements++
        }

        // Insert the new value at physicalTail and update it.
        buffer[physicalTail] = value
        physicalTail = (physicalTail + 1) % capacity

        // Increment logicalTail as a new element is added.
        logicalTail++
    }

    // Dequeue method to remove and return the oldest element
    fun dequeue(): T? {
        if (isEmpty()) return null  // Return null if the buffer is empty

        // Get the value at `head` (oldest element)
        val value = buffer[physicalHead]
        //buffer[physicalHead] = null

        // Move `head` to the next position, wrapping around if necessary
        physicalHead = (physicalHead + 1) % capacity
        logicalHead++

        // Decrease [numberOfElements] when an element is removed
        numberOfElements--

        return value
    }

    // Function to calculate the physical index from the logical index
    private fun physicalIndexFromLogicalIndex(index: Long): Int {
        return ((physicalHead + (index - logicalHead)) % capacity).toInt()
    }

    // Peek method to view the oldest element without removing it
    fun peek(): T? {
        return if (isEmpty()) null else buffer[physicalHead]
    }

    /**
     * Peek at a specific logical [index] in the buffer.
     * Returns null if the index is out of bounds or if the element has been overwritten.
     */
    fun peekAt(index: Long): T? {
        if (index < logicalHead || index >= logicalTail) {
            return null // Index is either too old (overwritten) or not yet available
        }

        val physicalIndex = physicalIndexFromLogicalIndex(index)
        return buffer[physicalIndex]
    }

    fun getLogicalHead(): Long {
        return logicalHead
    }

    fun getLogicalTail(): Long {
        return logicalTail
    }

    fun numberOfElements(): Int {
        return numberOfElements
    }

    /**
     * Prints the buffer contents from oldest to latest for debug proposes.
     */
    fun print() {
        println("Buffer contents: ")
        for (i in 0 until numberOfElements) {
            val index = (physicalHead + i) % capacity
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
        physicalHead = 0
        physicalTail = 0
        logicalHead = 0L
        logicalTail = 0L
    }
}