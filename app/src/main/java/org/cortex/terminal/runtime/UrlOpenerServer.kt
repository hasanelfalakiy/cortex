package org.cortex.terminal.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * UrlOpenerServer listens on localhost:4715 for browser redirection requests from CLI tools
 * (e.g. `xdg-open`, `sensible-browser`, `google-chrome`, `gh auth login`, `antigravity auth login`).
 *
 * Supported formats:
 *   OPEN <url>
 *   <url>
 *   HTTP GET requests: GET /open?url=<encoded_url> or GET /<url> HTTP/1.1
 *
 * When received, Cortex dispatches an Android Intent to Chrome or the system default browser.
 */
object UrlOpenerServer {
    private const val TAG = "UrlOpenerServer"
    const val PORT = 4715

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun start(context: Context) {
        if (isRunning) return
        val appContext = context.applicationContext

        threadPool.execute {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 50)
                serverSocket = server
                isRunning = true
                Log.i(TAG, "UrlOpenerServer started on 127.0.0.1:$PORT")

                while (isRunning && !server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }
                    threadPool.execute {
                        handleClient(appContext, client)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running UrlOpenerServer", e)
            } finally {
                isRunning = false
            }
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        Log.i(TAG, "UrlOpenerServer stopped")
    }

    private fun handleClient(context: Context, socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

            val rawLine = reader.readLine()?.trim()
            if (rawLine.isNullOrEmpty()) {
                writer.write("ERR empty request\n")
                writer.flush()
                return
            }

            var isHttp = false
            var targetUrl: String = rawLine

            if (rawLine.startsWith("GET ", ignoreCase = true) || rawLine.startsWith("POST ", ignoreCase = true)) {
                isHttp = true
                val path = rawLine.substringAfter(" ").substringBefore(" ").trim()
                targetUrl = when {
                    path.contains("url=") -> {
                        val encoded = path.substringAfter("url=").substringBefore("&")
                        try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                    }
                    path.startsWith("/http://", ignoreCase = true) -> path.substring(1)
                    path.startsWith("/https://", ignoreCase = true) -> path.substring(1)
                    path.startsWith("/open/", ignoreCase = true) -> path.substring(6)
                    else -> path.trimStart('/')
                }
            } else if (rawLine.startsWith("OPEN ", ignoreCase = true)) {
                targetUrl = rawLine.substring(5).trim()
            }

            targetUrl = targetUrl.trim('\"', '\'', ' ', '\t')

            val success = openUrlInBrowser(context, targetUrl)
            if (isHttp) {
                if (success) {
                    val body = "OK\n"
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    writer.write(resp)
                } else {
                    val body = "ERR failed to open URL\n"
                    val resp = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    writer.write(resp)
                }
            } else {
                if (success) {
                    writer.write("OK\n")
                } else {
                    writer.write("ERR failed to open URL\n")
                }
            }
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    fun openUrlInBrowser(context: Context, rawUrl: String): Boolean {
        var cleanUrl = rawUrl.trim().trim('\"', '\'')
        if (cleanUrl.isEmpty()) return false

        // Normalize URL scheme
        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true) &&
            !cleanUrl.startsWith("ftp://", ignoreCase = true) &&
            !cleanUrl.startsWith("file://", ignoreCase = true)
        ) {
            cleanUrl = "https://$cleanUrl"
        }

        val uri = try {
            Uri.parse(cleanUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Malformed URL: $cleanUrl", e)
            return false
        }

        mainHandler.post {
            val pm = context.packageManager
            val browserPackages = listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.opera.browser",
                "com.microsoft.emmx",
                "com.sec.android.app.sbrowser"
            )

            for (pkg in browserPackages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    return@post
                } catch (e: Exception) {
                    // Try next browser
                }
            }

            // Fallback to default browser / system handler
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch default browser for $cleanUrl", e)
            }
        }
        return true
    }
}
