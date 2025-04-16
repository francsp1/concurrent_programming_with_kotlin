package concurrentprogramming.server.broadcasttcpserverv2

import concurrentprogramming.datastructures.BoundedStream
import org.slf4j.Logger
import java.net.Socket
import kotlin.concurrent.thread

class SocketSessionManager(
    private val socket: Socket,
    private val session: SafeSessionInfoForBroadcastServer,
    private val serverInfo: SafeServerInfoForBroadcastServer,
    private val buffer: BoundedStream<BroadcastMessage>,
    private val logger: Logger
) {
    private val reader1 = socket.getInputStream().bufferedReader()
    private val writer1 = socket.getOutputStream().bufferedWriter()
    private val reader2 = socket.getInputStream().bufferedReader()
    private val writer2 = socket.getOutputStream().bufferedWriter()

    @Volatile var isClosed = false
        private set

    fun start() {
        val readerThread = thread(name = "reader-${session.id}") {
            try {
                ReaderWorker(reader1, writer1, session, serverInfo, buffer, logger).run()
            } catch (e: Exception) {
                logger.info("[Session ${session.id}] Reader failed: ${e.message}")
            } finally {
                shutdown("Reader done")
            }
        }

        val writerThread = thread(name = "writer-${session.id}") {
            try {
                WriterWorker(reader2, writer2, session, serverInfo, buffer, logger).run()
            } catch (e: Exception) {
                logger.info("[Session ${session.id}] Writer failed: ${e.message}")
            } finally {
                shutdown("Writer done")
            }
        }
    }

    @Synchronized
    fun shutdown(reason: String) {
        if (isClosed) return
        isClosed = true
        logger.info("[Session ${session.id}] Shutting down: $reason")
        try {
            reader1.close()
            writer1.close()
            reader2.close()
            writer2.close()
            socket.close()
        } catch (_: Exception) { /* ignore */ }
        serverInfo.endSession(session)
    }
}