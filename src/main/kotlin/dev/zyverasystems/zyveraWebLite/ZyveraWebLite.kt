package dev.zyverasystems.zyveraWebLite

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
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

        executor = Executors.newFixedThreadPool(
            max(4, Runtime.getRuntime().availableProcessors())
        )

        val wwwFolder = File(dataFolder, "www")
        if (!wwwFolder.exists()) {
            logger.info("Creating default files")

            wwwFolder.mkdirs()

            saveResource("www/index.html", false)
            saveResource("www/style.css", false)
            saveResource("www/script.js", false)
        }

        val sslFolder = File(dataFolder, "ssl")
        if (!sslFolder.exists()) {
            sslFolder.mkdir()
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

            val certFile: String = "" + dataFolder + "/ssl/" + getConfig().getString("https.SSLPubl")
            val keyFile: String = "" + dataFolder + "/ssl/" + getConfig().getString("https.SSLPriv")

            if (!File(certFile).exists() || !File(keyFile).exists()) {
                logger.info("SSL public or private key file does not exist, HTTPS disabled!")
                return
            }

            val sslContext: SSLContext = getSslContext(certFile, keyFile)

            httpsServer = HttpsServer.create(InetSocketAddress(host, port), 0)

            httpsServer!!.httpsConfigurator = object : HttpsConfigurator(sslContext) {
                override fun configure(params: HttpsParameters) {
                    val sslParams = sslContext.defaultSSLParameters

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
    private fun getSslContext(certFile: String, keyFile: String): SSLContext {

        val certFactory = CertificateFactory.getInstance("X.509")
        val certInput = FileInputStream(certFile)
        val certs = certFactory.generateCertificates(certInput)
        certInput.close()

        val cert = certs.first() as java.security.cert.X509Certificate

        val keyBytes = File(keyFile).readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val decodedKey = java.util.Base64.getDecoder().decode(keyBytes)

        val keySpec = PKCS8EncodedKeySpec(decodedKey)
        val privateKey = getPrivateKey(keySpec)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)

        keyStore.setKeyEntry(
            "alias",
            privateKey,
            "password".toCharArray(),
            arrayOf(cert)
        )

        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(keyStore, "password".toCharArray())

        val tmf = TrustManagerFactory.getInstance("SunX509")
        tmf.init(null as KeyStore?)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())

        return sslContext
    }

    private fun getPrivateKey(keySpec: PKCS8EncodedKeySpec): PrivateKey? {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch (_: Exception) {}
        try {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec)
        } catch (_: Exception) {}
        return null
    }
}