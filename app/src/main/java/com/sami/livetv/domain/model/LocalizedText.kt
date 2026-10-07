package com.sami.livetv.domain.model

data class LocalizedText(
    val arabic: String,
    val english: String,
) {
    fun resolve(languageTag: String): String =
        if (languageTag.lowercase().startsWith("en")) english else arabic
}
