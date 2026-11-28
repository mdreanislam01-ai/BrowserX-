package com.browserx.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * "BrowserX Shield" — a local, server-less VPN that only covers this app.
 *
 * It captures *DNS traffic only* (the tunnel has a single /32 route to a private DNS address),
 * forwards allowed queries to a public resolver and answers ad / tracker domains with 0.0.0.0.
 * Everything else (HTTP, HTTPS, QUIC …) bypasses the tunnel and uses the normal connection,
 * so enabling it cannot slow browsing down and there is no remote server involved.
 *
 * Because the tunnel is limited with [Builder.addAllowedApplication] it never affects other
 * apps on the device.
 */
class ShieldVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.browserx.app.SHIELD_START"
        const val ACTION_STOP = "com.browserx.app.SHIELD_STOP"

        private const val CHANNEL_ID = "browserx_shield"
        private const val NOTIFICATION_ID = 4242

        private const val TUN_ADDRESS = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"
        private val UPSTREAM_DNS = listOf("1.1.1.1", "8.8.8.8")

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ShieldVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShieldVpnService::class.java))
            isRunning = false
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        AdBlockEngine.loadDownloadedHosts(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startShield()
        return START_STICKY
    }

    private fun startShield() {
        if (running) return
        // Must happen promptly: the service may have been started with startForegroundService().
        showNotification()
        val builder = Builder()
            .setSession("BrowserX Shield")
            .setMtu(1500)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
        runCatching { builder.addAllowedApplication(packageName) }

        val descriptor = try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
        if (descriptor == null) {
            isRunning = false
            stopSelf()
            return
        }
        tun = descriptor
        running = true
        isRunning = true
        reader = thread(name = "browserx-shield-dns", isDaemon = true) { pump(descriptor) }
    }

    private fun shutdown() {
        running = false
        isRunning = false
        runCatching { tun?.close() }
        tun = null
        reader = null
        runCatching { stopForeground(true) }
        stopSelf()
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BrowserX Shield", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("BrowserX Shield")
            .setContentText("Blocking ad & tracker domains for BrowserX only")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
    }

    // -----------------------------------------------------------------------------------
    // Packet pump: IPv4 + UDP + DNS only
    // -----------------------------------------------------------------------------------
    private fun pump(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32 * 1024)
        while (running) {
            val length = try {
                input.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length < 0) break
            if (length == 0) continue
            val reply = try {
                handlePacket(buffer, length)
            } catch (_: Exception) {
                null
            }
            if (reply != null) {
                try {
                    output.write(reply)
                } catch (_: Exception) {
                    // tun closed
                }
            }
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int): ByteArray? {
        if (length < 28) return null
        if (((packet[0].toInt() and 0xF0) shr 4) != 4) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null // UDP only

        val udpStart = headerLength
        val clientPort = ShieldDns.readU16(packet, udpStart)
        val destinationPort = ShieldDns.readU16(packet, udpStart + 2)
        if (destinationPort != 53) return null

        val udpLength = ShieldDns.readU16(packet, udpStart + 4)
        val dnsLength = udpLength - 8
        if (dnsLength <= 12) return null

        val dnsStart = udpStart + 8
        val dnsEnd = minOf(length, dnsStart + dnsLength)
        if (dnsEnd <= dnsStart + 12) return null
        val query = packet.copyOfRange(dnsStart, dnsEnd)

        val question = ShieldDns.parseQuestion(query) ?: return null
        val blocked = AdBlockEngine.matchHost(question.host) != null

        val dnsReply = if (blocked) {
            ShieldDns.blockedResponse(query, question)
        } else {
            forwardUpstream(query) ?: ShieldDns.errorResponse(query, question)
        }

        val clientIp = packet.copyOfRange(12, 16)   // src of the query = our tun address
        val serverIp = packet.copyOfRange(16, 20)   // fake DNS address
        return ShieldDns.buildUdpPacket(serverIp, clientIp, clientPort, dnsReply)
    }

    private fun forwardUpstream(query: ByteArray): ByteArray? {
        for (server in UPSTREAM_DNS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 3000
                    val address = InetAddress.getByName(server)
                    socket.send(DatagramPacket(query, query.size, address, 53))
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    if (response.length > 12) {
                        return buffer.copyOfRange(0, response.length)
                    }
                }
            } catch (_: Exception) {
                // try the next resolver
            }
        }
        return null
    }
}
