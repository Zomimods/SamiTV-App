package com.sami.livetv.data.channels

import com.sami.livetv.domain.model.ChannelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class RemoteChannelCatalogDataSource(
    private val sourceUrl: String,
    private val parser: ChannelCatalogJsonParser = ChannelCatalogJsonParser(),
) : ChannelCatalogDataSource {
    override suspend fun load(): ChannelCatalog = withContext(Dispatchers.IO) {
        val url = URL(sourceUrl.trim())
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Remote channel catalogs must use HTTPS."
        }
        val connection = url.openConnection() as? HttpsURLConnection
            ?: throw IllegalArgumentException("Remote channel catalog URL must use HTTPS.")

        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")

            val statusCode = connection.responseCode
            if (statusCode !in HttpURLConnection.HTTP_OK..299) {
                throw IOException("Remote channel catalog request failed with HTTP $statusCode.")
            }

            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            parser.parse(json)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
