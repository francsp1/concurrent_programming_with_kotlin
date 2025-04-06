package cp.queues



class RingBuffer<T>(private val capacity: Int) {
    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<T?> = arrayOfNulls<Any?>(capacity) as Array<T?>

    private var numberOfElements = 0  // Tracks the number of elements

    private var physicalHead = 0  // Points to the oldest element (physical position)
    private var physicalTail = 0  // Points to the next insertion point (physical position)

    var logicalHead = 0L  // The index of the oldest element (logical position)
    var logicalTail = 0L  // The index of the next element to be inserted (logical position)

    sealed interface ReadResult<out T> {
        data class Success<T>(val items: List<T>, val startIndex: Long): ReadResult<T>
        data object Overwritten : ReadResult<Nothing>
        data object Empty : ReadResult<Nothing>
        data object InvalidIndex : ReadResult<Nothing>
        data object NotYetAvailable : ReadResult<Nothing>
    }

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
        return ((physicalHead + (index - logicalHead).toInt()) % capacity).toInt()
    }

    /**
     * To read all items from a [startIndex] to the end of the buffer
     */
    fun readFromIndex(startIndex: Long): ReadResult<T> {
        if (startIndex < 0) {
            return ReadResult.InvalidIndex
        }

        if (isEmpty()) return ReadResult.Empty

        val highestIndex = logicalTail - 1

        if (startIndex < logicalHead) {
            return ReadResult.Overwritten
        }

        if (startIndex > highestIndex) {
            return ReadResult.NotYetAvailable
        }


        val items = mutableListOf<T>()
        for (i in startIndex..highestIndex) {
            val physicalIndex = physicalIndexFromLogicalIndex(i)
            buffer[physicalIndex]?.let {
                items.add(it)
            }

        }

        return ReadResult.Success(items, startIndex)
    }

    fun numberOfElements(): Int {
        return numberOfElements
    }

    // Peek method to view the oldest element without removing it
    fun peek(): T? {
        return if (isEmpty()) null else buffer[physicalHead]
    }

    fun peekAt(index: Long): T? {
        if (index < logicalHead) {
            return null
        }

        if (index >= logicalTail) {
            return null
        }

        val physicalIndex = physicalIndexFromLogicalIndex(index)
        return buffer[physicalIndex]
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