package com.sami.livetv.data.channels

import com.sami.livetv.domain.model.ChannelCatalog
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.LocalizedText
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.domain.model.TvChannel
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class ChannelCatalogJsonParser {
    fun parse(json: String): ChannelCatalog {
        val root = JSONObject(json)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported channel catalog schema version: $schemaVersion"
        }

        val categoryArray = root.getJSONArray("categories")
        require(categoryArray.length() > 0) { "Channel catalog has no categories." }

        val categoryIds = mutableSetOf<String>()
        val channelIds = mutableSetOf<String>()
        val streamIds = mutableSetOf<String>()
        val categories = categoryArray.mapIndexed { categoryIndex, categoryJson ->
            val category = categoryJson as? JSONObject
                ?: error("categories[$categoryIndex] must be a JSON object.")
            val categoryPath = "categories[$categoryIndex]"
            val categoryId = category.requiredString("id", categoryPath)
            require(categoryIds.add(categoryId)) {
                "Duplicate category id '$categoryId' at $categoryPath."
            }

            val channelArray = category.getJSONArray("channels")
            val channels = channelArray.mapIndexed { channelIndex, channelJson ->
                val channel = channelJson as? JSONObject
                    ?: error("$categoryPath.channels[$channelIndex] must be a JSON object.")
                val channelPath = "$categoryPath.channels[$channelIndex]"
                val channelId = channel.requiredString("id", channelPath)
                require(channelIds.add(channelId)) {
                    "Duplicate channel id '$channelId' at $channelPath."
                }

                val channelAudioUrl = channel.optionalString("audioUrl")
                val streamArray = channel.getJSONArray("streams")
                require(streamArray.length() > 0) {
                    "$channelPath has no stream sources."
                }
                val streams = streamArray.mapIndexed { streamIndex, streamJson ->
                    val stream = streamJson as? JSONObject
                        ?: error("$channelPath.streams[$streamIndex] must be a JSON object.")
                    val streamPath = "$channelPath.streams[$streamIndex]"
                    val streamId = stream.requiredString("id", streamPath)
                    require(streamIds.add(streamId)) {
                        "Duplicate stream id '$streamId' at $streamPath."
                    }

                    val url = stream.requiredString("url", streamPath)
                    validateHttpUrl(url, streamPath)
                    StreamSource(
                        id = streamId,
                        quality = LocalizedText(
                            arabic = stream.requiredString("qualityAr", streamPath),
                            english = stream.optionalString("qualityEn")
                                ?: stream.requiredString("qualityAr", streamPath),
                        ),
                        url = url,
                        audioUrl = stream.optionalString("audioUrl") ?: channelAudioUrl,
                        requiresProxy = stream.booleanOrFalse("requiresProxy", streamPath),
                    )
                }

                TvChannel(
                    id = channelId,
                    name = channel.localizedText(channelPath),
                    imageLight = channel.optionalString("imageLight"),
                    imageDark = channel.optionalString("imageDark"),
                    audioUrl = channelAudioUrl,
                    requiresProxy = channel.booleanOrFalse("requiresProxy", channelPath),
                    streams = streams,
                )
            }

            ChannelCategory(
                id = categoryId,
                name = category.localizedText(categoryPath),
                icon = category.optionalString("icon"),
                imageLight = category.optionalString("imageLight"),
                imageDark = category.optionalString("imageDark"),
                channels = channels,
            )
        }

        return ChannelCatalog(
            schemaVersion = schemaVersion,
            categories = categories,
        )
    }

    private fun JSONObject.localizedText(path: String): LocalizedText {
        val arabic = requiredString("nameAr", path)
        return LocalizedText(
            arabic = arabic,
            english = optionalString("nameEn") ?: arabic,
        )
    }

    private fun JSONObject.requiredString(key: String, path: String): String {
        val value = optionalString(key)
        require(!value.isNullOrBlank()) { "$path.$key is required." }
        return value
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "")
            .trim()
            .takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.booleanOrFalse(key: String, path: String): Boolean {
        if (!has(key) || isNull(key)) return false
        return try {
            getBoolean(key)
        } catch (cause: Exception) {
            throw IllegalArgumentException("$path.$key must be a boolean.", cause)
        }
    }

    private fun validateHttpUrl(url: String, path: String) {
        val uri = try {
            URI(url)
        } catch (cause: Exception) {
            throw IllegalArgumentException("$path.url is not a valid URL.", cause)
        }
        val supportedScheme = uri.scheme
            ?.lowercase()
            ?.let(SUPPORTED_URL_SCHEMES::contains) == true
        require(supportedScheme && !uri.host.isNullOrBlank()) {
            "$path.url must be an absolute HTTP or HTTPS URL."
        }
    }

    private inline fun <T> JSONArray.mapIndexed(
        crossinline transform: (index: Int, value: Any) -> T,
    ): List<T> = List(length()) { index -> transform(index, get(index)) }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        val SUPPORTED_URL_SCHEMES = setOf("http", "https")
    }
}
