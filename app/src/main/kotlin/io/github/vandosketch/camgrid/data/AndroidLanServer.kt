package io.github.vandosketch.camgrid.data

import io.github.vandosketch.camgrid.platform.AppLog
import io.github.vandosketch.camgrid.platform.LanServer
import io.github.vandosketch.camgrid.transfer.TransferConnectionHandler
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * The socket side of the backup transfer on TVs (the protocol is common code): one
 * [ServerSocket] bound to the device's private IPv4 address on Wi-Fi or Ethernet, never to all
 * interfaces, and one daemon thread that handles one connection at a time. Needs no permission
 * beyond INTERNET. Logs only exception types, never request contents.
 */
class AndroidLanServer : LanServer {

    private var serverSocket: ServerSocket? = null

    /** The connection being handled, closed by [stop] so the thread does not wait for a slow client. */
    @Volatile
    private var client: Socket? = null

    @Synchronized
    override fun start(handler: TransferConnectionHandler): String? {
        stop()
        val address = lanAddress() ?: return null
        val socket = bind(address, PREFERRED_PORT) ?: bind(address, 0) ?: return null
        serverSocket = socket
        Thread({ serve(socket, handler) }, "camgrid-transfer").apply {
            isDaemon = true
            start()
        }
        return "${address.hostAddress}:${socket.localPort}"
    }

    @Synchronized
    override fun stop() {
        closeQuietly(serverSocket)
        closeQuietly(client)
        serverSocket = null
    }

    /** A server socket on [port] (0: any free port), or null when it is taken. */
    private fun bind(address: InetAddress, port: Int): ServerSocket? = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(address, port), BACKLOG)
        }
    } catch (e: IOException) {
        AppLog.w("Backup transfer cannot listen: ${e.javaClass.simpleName}")
        null
    }

    private fun serve(socket: ServerSocket, handler: TransferConnectionHandler) {
        while (!socket.isClosed) {
            // accept() throws once stop() closed the socket, which ends the thread.
            val connection = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            client = connection
            try {
                connection.use { handle(it, handler) }
            } catch (e: IOException) {
                AppLog.w("Backup transfer connection failed: ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                // A bug must not end the server while the screen still shows its address.
                AppLog.e("Backup transfer request failed: ${e.javaClass.simpleName}")
            } finally {
                client = null
            }
        }
    }

    private fun handle(connection: Socket, handler: TransferConnectionHandler) {
        connection.soTimeout = READ_TIMEOUT_MS
        val input = connection.getInputStream()
        val output = connection.getOutputStream()
        handler.serve({ buffer, offset, length -> input.read(buffer, offset, length) }) { bytes -> output.write(bytes) }
        output.flush()
        // A refused upload (wrong PIN, too large) leaves its body unread. Closing right away
        // would reset the connection and the browser would show a network error instead of the
        // answer, so read and drop what is left, for a short while.
        connection.shutdownOutput()
        connection.soTimeout = DRAIN_TIMEOUT_MS
        val scratch = ByteArray(8192)
        var drained = 0L
        try {
            while (drained < MAX_DRAIN_BYTES) {
                val n = input.read(scratch)
                if (n < 0) break
                drained += n
            }
        } catch (_: IOException) {
            // Timeout or reset: the response is out either way.
        }
    }

    private companion object {
        /** Easy to type; any free port when it is taken. */
        const val PREFERRED_PORT = 8765
        const val BACKLOG = 4
        const val READ_TIMEOUT_MS = 15_000
        const val DRAIN_TIMEOUT_MS = 2_000
        const val MAX_DRAIN_BYTES = 4L * 1024 * 1024

        /**
         * The device's private (RFC 1918) IPv4 address on an interface that is up, preferring
         * Wi-Fi and Ethernet; null without one. Mobile data and VPN addresses are not private
         * LAN addresses, or not reachable from the phone on the same Wi-Fi.
         */
        fun lanAddress(): InetAddress? {
            val interfaces = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            } catch (_: SocketException) {
                return null
            }
            val candidates = interfaces.filter { isUsable(it) }.flatMap { networkInterface ->
                networkInterface.inetAddresses.toList()
                    .filter { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
                    .map { networkInterface.name.orEmpty() to it }
            }
            val preferred = candidates.firstOrNull { (name, _) -> name.startsWith("wlan") || name.startsWith("eth") }
            return (preferred ?: candidates.firstOrNull())?.second
        }

        private fun isUsable(networkInterface: NetworkInterface): Boolean = try {
            networkInterface.isUp && !networkInterface.isLoopback && !networkInterface.isPointToPoint
        } catch (_: SocketException) {
            false
        }

        private fun closeQuietly(closeable: java.io.Closeable?) {
            try {
                closeable?.close()
            } catch (_: IOException) {
                // Already closed.
            }
        }
    }
}
