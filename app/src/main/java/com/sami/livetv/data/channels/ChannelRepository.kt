package com.sami.livetv.data.channels

import com.sami.livetv.domain.model.ChannelCatalog

class ChannelRepository(
    private val bundledSource: ChannelCatalogDataSource,
    private val remoteSourceFactory: (String) -> ChannelCatalogDataSource = {
        RemoteChannelCatalogDataSource(it)
    },
) {
    suspend fun loadBundledCatalog(): ChannelCatalog = bundledSource.load()

    suspend fun loadRemoteCatalog(httpsUrl: String): ChannelCatalog =
        remoteSourceFactory(httpsUrl).load()
}
