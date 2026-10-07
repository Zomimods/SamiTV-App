package com.sami.livetv.data.channels

import com.sami.livetv.domain.model.ChannelCatalog

interface ChannelCatalogDataSource {
    suspend fun load(): ChannelCatalog
}
