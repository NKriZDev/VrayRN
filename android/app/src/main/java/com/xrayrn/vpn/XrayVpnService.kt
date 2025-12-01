package com.xrayrn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.xrayrn.MainActivity
import com.xrayrn.R
import java.io.File

class XrayVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var tun2socksProcess: Process? = null

    companion object {
        private const val NOTIF_ID = 1001
        private const val TAG = "XrayVpn"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.e(TAG, "onStartCommand called")

        startForeground(NOTIF_ID, buildNotification("Starting..."))

        try {
            if (tunFd == null) {
                android.util.Log.e(TAG, "Creating TUN interface")
                tunFd = Builder()
                    .setSession("XrayRN")
                    .setMtu(1500)
                    .addAddress("26.26.26.2", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .establish()
                android.util.Log.e(TAG, "TUN established: fd=${tunFd?.fd}")
            } else {
                android.util.Log.e(TAG, "TUN already exists: fd=${tunFd?.fd}")
            }

            android.util.Log.e(TAG, "Calling startTun2socksIfNeeded")
            startTun2socksIfNeeded()
            android.util.Log.e(TAG, "startTun2socksIfNeeded returned")

            startForeground(NOTIF_ID, buildNotification("Running"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in onStartCommand", e)
            startForeground(NOTIF_ID, buildNotification("Error: ${e.message}"))
            stopSelf()
        }

        return START_STICKY
    }

    private fun prepareTun2socksBinary(): File {
        val nativeDir = applicationInfo.nativeLibraryDir
        android.util.Log.e(TAG, "nativeLibraryDir = $nativeDir")

        val soFile = File(nativeDir, "libtun2socks.so")
        android.util.Log.e(
            TAG,
            "libtun2socks.so exists=${soFile.exists()} length=${soFile.length()}"
        )

        if (!soFile.exists()) {
            throw IllegalStateException("libtun2socks.so not found in $nativeDir")
        }

        // Make sure it's executable; it should already be, but log it.
        val ok = soFile.setExecutable(true, true)
        android.util.Log.e(TAG, "setExecutable result=$ok path=${soFile.absolutePath}")

        return soFile
    }

    private fun startTun2socksIfNeeded() {
        android.util.Log.e(TAG, "startTun2socksIfNeeded entered")

        if (tun2socksProcess != null) {
            android.util.Log.e(TAG, "tun2socksProcess already running")
            return
        }

        val pfd = tunFd ?: throw IllegalStateException("TUN fd is null")
        val tunFdInt = pfd.fd

        val bin = prepareTun2socksBinary()

        val cmd = listOf(
            bin.absolutePath,
            "--netif-ipaddr", "26.26.26.2",
            "--netif-netmask", "255.255.255.252",
            "--socks-server-addr", "127.0.0.1:10808",
            "--tunfd", tunFdInt.toString(),
            "--tunmtu", "1500",
            "--loglevel", "3"
        )

        android.util.Log.e(TAG, "Starting tun2socks with cmd=$cmd")
        android.util.Log.e("TUN2SOCKS", "starting: ${cmd.joinToString(" ")}")

        try {
            tun2socksProcess = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ProcessBuilder.start failed: ${e.message}", e)
            android.util.Log.e("TUN2SOCKS", "ProcessBuilder.start failed: ${e.message}")
            throw e
        }

        // stdout/stderr reader
        Thread {
            try {
                val r = tun2socksProcess!!.inputStream.bufferedReader()
                while (true) {
                    val line = r.readLine() ?: break
                    android.util.Log.e("TUN2SOCKS", line)
                }
            } catch (_: Exception) {
            }
        }.start()

        // exit-code logger
        Thread {
            try {
                val exit = tun2socksProcess!!.waitFor()
                android.util.Log.e("TUN2SOCKS", "process exited with code=$exit")
            } catch (_: Exception) {
            }
        }.start()
    }

    override fun onDestroy() {
        android.util.Log.e(TAG, "onDestroy called, stopping tun2socks and closing TUN")

        tun2socksProcess?.destroy()
        tun2socksProcess = null

        tunFd?.close()
        tunFd = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "xray_vpn"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Xray VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("XrayRN VPN")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .build()
    }
}
