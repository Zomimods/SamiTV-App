package com.sami.livetv.data.channels

import android.content.Context
import com.sami.livetv.domain.model.ChannelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetChannelCatalogDataSource(
    context: Context,
    private val parser: ChannelCatalogJsonParser = ChannelCatalogJsonParser(),
    private val assetName: String = DEFAULT_ASSET_NAME,
) : ChannelCatalogDataSource {
    private val assets = context.applicationContext.assets

    override suspend fun load(): ChannelCatalog = withContext(Dispatchers.IO) {
        val json = assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
        parser.parse(json)
    }

    private companion object {
        const val DEFAULT_ASSET_NAME = "channels.json"
    }
}
