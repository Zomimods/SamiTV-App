package com.sami.livetv.domain.model

data class ChannelCatalog(
    val schemaVersion: Int,
    val categories: List<ChannelCategory>,
) {
    val channelCount: Int
        get() = categories.sumOf { it.channels.size }

    val streamCount: Int
        get() = categories.sumOf { category ->
            category.channels.sumOf { channel -> channel.streams.size }
        }

    fun findChannel(channelId: String): TvChannel? =
        categories.asSequence()
            .flatMap { it.channels.asSequence() }
            .firstOrNull { it.id == channelId }
}
