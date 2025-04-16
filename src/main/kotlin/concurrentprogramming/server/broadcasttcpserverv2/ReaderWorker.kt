package concurrentprogramming.server.broadcasttcpserverv2

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.server.writeLine
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter

private const val EXIT = "exit"
private const val STATS = "stats"

class ReaderWorker(
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    private val session: SafeSessionInfoForBroadcastServer,
    private val serverInfo: SafeServerInfoForBroadcastServer,
    private val buffer: BoundedStream<BroadcastMessage>,
    private val logger: Logger
) {
    fun run() {
        logger.info("[Session: ${session.id}] Reader thread for ${session.remoteSocketAddress} Started")
        writer.writeLine("Hello ${session.remoteSocketAddress}! Please type something and press Enter:")
        receiveAndEnqueueMessages(reader, writer, session)
        logger.info("[Session: ${session.id}] Reader thread for ${session.remoteSocketAddress} terminated")
    }

    private fun receiveAndEnqueueMessages(
        reader: BufferedReader,
        writer: BufferedWriter,
        session: SafeSessionInfoForBroadcastServer,
    ) {
        while (true) {
            if (session.isClosed()) {
                logger.info("[Session ${session.id}] Session marked closed. Terminating reader.")
                break
            }

            /*
            if (clientSocket.isClosed) {
                logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} socket is closed. Terminating writer.")
                break
            }
            */

            val rawLine = reader.readLine()
            if (rawLine == null) {
                logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} disconnected.")
                break
            }
            val normalized = rawLine.trim().lowercase()

            when (normalized) {
                EXIT -> {
                    logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} sent \"exit\".")
                    writer.writeLine("Exiting...")
                    break
                }

                STATS -> {
                    logger.info("[Session: ${session.id}] Client ${session.remoteSocketAddress} requested stats.")
                    writer.writeLine("Session stats: ${serverInfo.getStats()}")
                    continue
                }
            }
            serverInfo.incrementMessageCount(session)
            buffer.write(BroadcastMessage(session.id, session.remoteSocketAddress, normalized))
            logger.info("[Session: ${session.id}] Received message from session from ${session.remoteSocketAddress}: $rawLine and added to buffer")
        }
    }

}