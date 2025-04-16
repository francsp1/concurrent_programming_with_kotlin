package concurrentprogramming.server

import java.io.BufferedWriter
import java.io.IOException

/**
 * Writes to this [BufferedWriter] the line comprised of the text in [line].
 * @param [line] the line to write.
 * @return the [BufferedWriter] instance, for fluent use.
 * @throws IOException if an I/O error occurs.
 */
@Throws(IOException::class)
fun BufferedWriter.writeLine(line: String): BufferedWriter = also {
    write(line)
    newLine()
    flush()
}