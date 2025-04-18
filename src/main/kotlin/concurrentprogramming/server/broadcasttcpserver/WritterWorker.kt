package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.server.writeLine
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter

import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT_SECONDS: Int = 30

class WriterWorker(
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    private val session: SafeSessionInfoForBroadcastServer,
    private val serverInfo: SafeServerInfoForBroadcastServer,
    private val buffer: BoundedStream<BroadcastMessage>,
    private val logger: Logger
) {
    fun run() {
        dequeueAndSendMessages(writer, session)
    }

    private fun dequeueAndSendMessages(
        writer: BufferedWriter,
        session: SafeSessionInfoForBroadcastServer,
    ) {
        var currentIndex = buffer.getLogicalTail()

        while (true) {
            if (session.isClosed()) {
                logger.info("[Session ${session.id}] Client ${session.remoteAddress} was session marked as closed. Terminating writer.")
                break
            }

            /*
            if (clientSocket.isClosed) {
                logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} socket is closed. Terminating writer.")
                break
            }
            */

            val readResult = try {
                buffer.read(currentIndex, TIMEOUT_SECONDS.seconds)
            } catch (ie: InterruptedException) {
                logger.info("[Session ${session.id}] Client ${session.remoteAddress} had is writer thread interrupted. Terminating writer.")
                throw ie
            }

            when (readResult) {
                is BoundedStream.ReadResult.Success -> {
                    if (readResult.items.isEmpty()) {
                        logger.info("[Session ${session.id}] Client ${session.remoteAddress} had no new messages. currentIndex=$currentIndex")
                        currentIndex = buffer.getLogicalTail()
                        continue
                    }

                    for (msg in readResult.items) {
                        val line = "${msg.senderId} wrote: ${msg.message}"
                        writer.writeLine(line)
                        logger.info("[Session ${session.id}] Client ${session.remoteAddress} sent a message to ${msg.clientAddress} (${msg.message}) -> $line")
                    }

                    currentIndex += readResult.items.size
                }

                is BoundedStream.ReadResult.Timeout -> {
                    logger.info("[Session ${session.id}] Client ${session.remoteAddress} will be disconnected due to inactivity (Read messages timeout is $TIMEOUT_SECONDS seconds).")
                    writer.writeLine("You have been disconnected due to inactivity. No new messages received in $TIMEOUT_SECONDS seconds.")
                    break
                }

                is BoundedStream.ReadResult.Closed -> {
                    logger.info("[Session ${session.id}] Client ${session.remoteAddress} found the stream closed. Terminating writer.")
                    break
                }

                is BoundedStream.ReadResult.IndexOverwritten -> {
                    logger.info("[Session ${session.id}] Client ${session.remoteAddress} tried to read from an overwritten index. currentIndex=$currentIndex")
                    currentIndex = buffer.getLogicalTail()
                    continue
                }
            }
        }
    }

}
