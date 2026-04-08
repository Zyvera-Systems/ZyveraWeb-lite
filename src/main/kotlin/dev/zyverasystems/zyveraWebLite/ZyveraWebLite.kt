package dev.zyverasystems.zyveraWebLite

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.math.max

class ZyveraWebLite : JavaPlugin() {
    private var httpServer: HttpServer? = null
    private var httpsServer: HttpsServer? = null
    private var executor: ExecutorService? = null

    override fun onEnable() {
        saveDefaultConfig()

        // Create thread pool based on CPU cores (safe + performant)
        executor = Executors.newFixedThreadPool(
            max(4, Runtime.getRuntime().availableProcessors())
        )

        // Ensure www folder exists
        val wwwFolder = File(dataFolder, "www")
        if (!wwwFolder.exists()) {
            logger.info("Creating default files")

            wwwFolder.mkdirs()

            saveResource("www/index.html", false)
            saveResource("www/style.css", false)
            saveResource("www/script.js", false)

        }

        startHttp(wwwFolder)
        startHttps(wwwFolder)
    }

    override fun onDisable() {
        if (httpServer != null) {
            httpServer!!.stop(0)
        }

        if (httpsServer != null) {
            httpsServer!!.stop(0)
        }

        if (executor != null) {
            executor!!.shutdownNow()
        }
    }

    private fun startHttp(wwwFolder: File) {
        if (!getConfig().getBoolean("http.enabled")) {
            logger.info("HTTP disabled.")
            return
        }

        try {
            val host = getConfig().getString("server.host")
            val port = getConfig().getInt("http.port")

            httpServer = HttpServer.create(InetSocketAddress(host, port), 0)

            val handler = FileHandler(wwwFolder)
            httpServer!!.createContext("/", handler)

            httpServer!!.executor = executor
            httpServer!!.start()

            logger.info("HTTP server running on http://$host:$port")
        } catch (e: Exception) {
            logger.severe("Failed to start HTTP server")
            e.printStackTrace()
        }
    }

    private fun startHttps(wwwFolder: File) {
        if (!getConfig().getBoolean("https.enabled")) {
            logger.info("HTTPS disabled.")
            return
        }

        try {
            val host = getConfig().getString("server.host")
            val port = getConfig().getInt("https.port")

            val keystoreFile = File(
                dataFolder,
                getConfig().getString("https.keystore").toString()
            )

            if (!keystoreFile.exists()) {
                logger.warning("Keystore not found -> HTTPS not started.")
                return
            }

            val password = getConfig().getString("https.password")!!.toCharArray()

            val sslContext = createSSLContext(keystoreFile, password)

            httpsServer = HttpsServer.create(InetSocketAddress(host, port), 0)

            httpsServer!!.httpsConfigurator = object : HttpsConfigurator(sslContext) {
                override fun configure(params: HttpsParameters) {
                    val sslParams = sslContext.defaultSSLParameters

                    // 🔐 Enforce modern TLS
                    sslParams.protocols = arrayOf("TLSv1.3", "TLSv1.2")

                    params.setSSLParameters(sslParams)
                }
            }

            val handler = FileHandler(wwwFolder)
            httpsServer!!.createContext("/", handler)

            httpsServer!!.executor = executor
            httpsServer!!.start()

            logger.info("HTTPS server running on https://$host:$port")
        } catch (e: Exception) {
            logger.severe("Failed to start HTTPS server")
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun createSSLContext(keystoreFile: File, password: CharArray?): SSLContext {
        val keyStore = KeyStore.getInstance("JKS")

        FileInputStream(keystoreFile).use { fis ->
            keyStore.load(fis, password)
        }
        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(keyStore, password)

        val tmf = TrustManagerFactory.getInstance("SunX509")
        tmf.init(keyStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

        return sslContext
    }
}