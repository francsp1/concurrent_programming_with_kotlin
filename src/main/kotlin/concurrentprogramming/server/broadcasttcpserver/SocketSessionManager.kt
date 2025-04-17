package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import org.slf4j.Logger
import java.net.Socket
import java.util.concurrent.Phaser

class SocketSessionManager(
    private val socket: Socket,
    private val session: SafeSessionInfoForBroadcastServer,
    private val serverInfo: SafeServerInfoForBroadcastServer,
    private val buffer: BoundedStream<BroadcastMessage>,
    private val logger: Logger
) {
    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()

    private val readerThread = Thread.ofPlatform().name("reader-${serverInfo.totalClients}-${session.id}").unstarted { readerThreadTask() }
    @Volatile private var readerThreadStarted = false

    private val writerThread = Thread.ofPlatform().name("writer-${serverInfo.totalClients}-${session.id}").unstarted { writerThreadTask() }
    @Volatile private var writerThreadStarted = false

    private val phaser = Phaser(2)

    @Volatile var isClosed = false
        private set

    fun start() {
        readerThread.start()
        writerThread.start()

        val test = socket.getInputStream()
        test.read()

    }

    private fun readerThreadTask() {
        readerThreadStarted = true
        try {
            phaser.arriveAndAwaitAdvance()
            ReaderWorker(reader, writer, session, serverInfo, buffer, logger).run()
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Reader failed: ${e.message}")
        } finally {
            shutdown("Reader done")

            if (writerThread.isAlive) {
                writerThread.interrupt()
            }
        }
    }

    private fun writerThreadTask() {
        writerThreadStarted = true
        try {
            phaser.arriveAndAwaitAdvance()
            WriterWorker(reader, writer, session, serverInfo, buffer, logger).run()
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Writer failed: ${e.message}")
        } finally {
            shutdown("Writer done")

            if (readerThread.isAlive) {
                readerThread.interrupt()
            }
        }
    }

    @Synchronized
    fun shutdown(reason: String) {
        if (isClosed) return
        isClosed = true
        logger.info("[Session ${session.id}] Shutting down: $reason")
        try {

            reader.close()
            writer.close()
            socket.shutdownOutput()
            socket.shutdownInput()
            socket.close()
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Shutdown caught an exception: ${e.message}")
        } finally {
            serverInfo.endSession(session)
        }
    }
}