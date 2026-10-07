package com.sami.livetv.domain.model

data class TvChannel(
    val id: String,
    val name: LocalizedText,
    val imageLight: String?,
    val imageDark: String?,
    val audioUrl: String?,
    val requiresProxy: Boolean,
    val streams: List<StreamSource>,
)
