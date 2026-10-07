package com.sami.livetv.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.sami.livetv.data.preferences.AppLanguage
import org.json.JSONObject

/** The website's own T/CMS dictionaries (assets/i18n.json, extracted verbatim from V20). */
object I18n {
    private var ar: Map<String, String> = emptyMap()
    private var en: Map<String, String> = emptyMap()

    fun load(context: Context) {
        if (ar.isNotEmpty()) return
        val raw = context.assets.open("i18n.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        fun read(tag: String): Map<String, String> {
            val o = root.optJSONObject(tag) ?: return emptyMap()
            return buildMap { o.keys().forEach { put(it, o.optString(it)) } }
        }
        ar = read("ar"); en = read("en")
    }

    fun t(key: String, lang: AppLanguage): String =
        (if (lang == AppLanguage.English) en[key] else ar[key]) ?: ar[key] ?: key
}

val LocalLang = compositionLocalOf { AppLanguage.Arabic }

@Composable
fun t(key: String): String = I18n.t(key, LocalLang.current)

@Composable
fun isEnglish(): Boolean = LocalLang.current == AppLanguage.English
