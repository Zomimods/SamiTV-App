package com.sami.livetv.domain.model

data class ChannelCategory(
    val id: String,
    val name: LocalizedText,
    val icon: String?,
    val imageLight: String?,
    val imageDark: String?,
    val channels: List<TvChannel>,
)
