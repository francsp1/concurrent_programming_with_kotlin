package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import org.slf4j.Logger
import java.io.IOException
import java.net.Socket
import java.util.concurrent.Phaser
import java.util.concurrent.Semaphore

class SocketSessionManager(
    private val clientSocket: Socket,
    private val permit: Semaphore,
    //private val session: SafeSessionInfoForBroadcastServer,
    private val serverInfo: SafeServerInfoForBroadcastServer,
    private val buffer: BoundedStream<BroadcastMessage>,
    private val logger: Logger
) {
    private val session = serverInfo.createSession(clientSocket)

    private val reader = clientSocket.getInputStream().bufferedReader()
    private val writer = clientSocket.getOutputStream().bufferedWriter()

    private val readerThread = Thread.ofPlatform().name("reader-${serverInfo.totalClients}-${session.id}").unstarted { readerThreadTask() }
    @Volatile private var readerThreadStarted = false

    private val writerThread = Thread.ofPlatform().name("writer-${serverInfo.totalClients}-${session.id}").unstarted { writerThreadTask() }
    @Volatile private var writerThreadStarted = false

    private val phaser = Phaser(2)

    @Volatile var isClosed = false
        private set


    fun start() {
        logger.info("[Session: ${session.id} Client ${session.remoteAddress} connected] and new session created. Initializing threads...")
        readerThread.start()
        writerThread.start()
    }

    private fun readerThreadTask() {
        readerThreadStarted = true
        logger.info("[Session: ${session.id}] Reader thread for ${session.remoteAddress} Started")
        try {
            phaser.arriveAndAwaitAdvance()
            ReaderWorker(reader, writer, session, serverInfo, buffer, logger).run()
        } catch (ioe: IOException) {
            logger.info("[Session ${session.id}] Reader thread thew an IOException: ${ioe.message}")
        } catch (ie: InterruptedException) {
            logger.info("[Session ${session.id}] Reader thread threw an InterruptedException: ${ie.message}")
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Reader thread threw an Exception: ${e.message}")
        } finally {
            shutdown("Reader done")
            if (writerThread.isAlive) {
                writerThread.interrupt()
            }
        }
        logger.info("[Session: ${session.id}] Reader thread for ${session.remoteAddress} terminated")
    }

    private fun writerThreadTask() {
        writerThreadStarted = true
        logger.info("[Session: ${session.id}] Writer thread for ${session.remoteAddress} Started")
        try {
            phaser.arriveAndAwaitAdvance()
            WriterWorker(reader, writer, session, serverInfo, buffer, logger).run()
        } catch (ioe: IOException) {
            logger.info("[Session ${session.id}] Writer thread thew an IOException: ${ioe.message}")
        } catch (ie: InterruptedException) {
            logger.info("[Session ${session.id}] Writer thread threw an InterruptedException: ${ie.message}")
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Writer thread threw an Exception: ${e.message}")
        } finally {
            shutdown("Writer done")
            if (readerThread.isAlive) {
                readerThread.interrupt()
            }
        }
        logger.info("[Session: ${session.id}] Writer thread for ${session.remoteAddress} terminated")
    }

    @Synchronized
    fun shutdown(reason: String) {
        if (isClosed) return
        isClosed = true
        logger.info("[Session ${session.id}] Shutting down: $reason")
        try {
            clientSocket.close()
        } catch (e: Exception) {
            logger.info("[Session ${session.id}] Shutdown caught an exception: ${e.message}")
        } finally {
            serverInfo.endSession(session)
            permit.release()
        }
    }
}