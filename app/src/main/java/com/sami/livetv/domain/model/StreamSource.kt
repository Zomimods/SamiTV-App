package com.sami.livetv.domain.model

data class StreamSource(
    val id: String,
    val quality: LocalizedText,
    val url: String,
    val audioUrl: String?,
    val requiresProxy: Boolean,
)
