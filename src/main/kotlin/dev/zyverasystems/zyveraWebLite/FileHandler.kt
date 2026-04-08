package dev.zyverasystems.zyveraWebLite

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*

class FileHandler(private val root: File) : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val requestPath = exchange.requestURI.getPath()

        val resolved = root.toPath().resolve(".$requestPath").normalize()

        if (!resolved.startsWith(root.toPath())) {
            send403(exchange)
            return
        }

        var file = resolved.toFile()

        if (file.isDirectory()) {
            file = File(file, "index.html")
        }

        if (!file.exists() || !file.isFile()) {
            send404(exchange)
            return
        }

        val ext = getExtension(file.getName())
        val contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream")

        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(200, file.length())

        exchange.responseBody.use { os ->
            Files.newInputStream(file.toPath()).use { `is` ->
                `is`.transferTo(os)
            }
        }
    }

    @Throws(IOException::class)
    private fun send404(exchange: HttpExchange) {
        val resp = "404 Not Found".toByteArray()
        exchange.sendResponseHeaders(404, resp.size.toLong())
        exchange.responseBody.write(resp)
        exchange.close()
    }

    @Throws(IOException::class)
    private fun send403(exchange: HttpExchange) {
        val resp = "403 Forbidden".toByteArray()
        exchange.sendResponseHeaders(403, resp.size.toLong())
        exchange.responseBody.write(resp)
        exchange.close()
    }

    private fun getExtension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i == -1) "" else name.substring(i + 1).lowercase(Locale.getDefault())
    }

    companion object {
        private val CONTENT_TYPES: MutableMap<String?, String?> = HashMap()

        init {
            CONTENT_TYPES["html"] = "text/html"
            CONTENT_TYPES["css"] = "text/css"
            CONTENT_TYPES["js"] = "application/javascript"
            CONTENT_TYPES["json"] = "application/json"
            CONTENT_TYPES["png"] = "image/png"
            CONTENT_TYPES["jpg"] = "image/jpeg"
            CONTENT_TYPES["jpeg"] = "image/jpeg"
            CONTENT_TYPES["gif"] = "image/gif"
            CONTENT_TYPES["svg"] = "image/svg+xml"
            CONTENT_TYPES["txt"] = "text/plain"
        }
    }
}