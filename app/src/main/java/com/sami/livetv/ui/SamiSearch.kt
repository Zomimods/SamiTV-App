package com.sami.livetv.ui

import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.domain.model.ChannelCatalog
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.TvChannel

/** Port of the website's normalizeArabic + synonym/typo-tolerant search (V10 AI search, local and free). */
object SamiSearch {
    fun normalize(value: String?): String {
        var s = (value ?: "").lowercase()
        s = s.replace(Regex("[أإآٱ]"), "ا")
            .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي').replace('ة', 'ه').replace("ـ", "")
            .replace(Regex("[\u064B-\u0652]"), "")
            .replace(Regex("[\u200e\u200f\u061c]"), "")
            .replace(Regex("\\s+"), " ").trim()
        return s
    }

    private val SYN = mapOf(
        "رياضه" to listOf("رياض", "sport", "sports", "سبورت", "بين", "كاس", "دوري", "مباريات", "كوره"),
        "اخبار" to listOf("news", "نيوز", "الجزيره", "العربيه", "عاجل", "سكاي", "اخباريه"),
        "اطفال" to listOf("kids", "كرتون", "سبيس", "بيبي", "طيور", "كيدز", "cartoon"),
        "افلام" to listOf("فيلم", "movie", "movies", "سينما", "دراما", "مسلسل", "مسلسلات", "cinema"),
        "دين" to listOf("قران", "quran", "اسلام", "islam", "مكه", "المدينه", "شريعه", "ديني"),
        "موسيقى" to listOf("اغاني", "music", "طرب", "مزيكا", "song"),
        "وثائقي" to listOf("وثائق", "documentary", "طبيعه", "discovery", "ناشيونال"),
    )

    data class Entry(val category: ChannelCategory, val channel: TvChannel, val text: String, val words: List<String>)

    sealed interface Hit {
        val score: Double
        data class Channel(val category: ChannelCategory, val channel: TvChannel, override val score: Double) : Hit
        data class Section(val category: ChannelCategory, override val score: Double) : Hit
    }

    class Index(val catalog: ChannelCatalog) {
        val entries: List<Entry> = catalog.categories.flatMap { cat ->
            cat.channels.map { ch ->
                val txt = normalize("${ch.name.arabic} ${ch.name.english} ${cat.name.arabic} ${cat.name.english}")
                Entry(cat, ch, txt, txt.split(' ').filter { it.length > 2 })
            }
        }
    }

    private fun lev(a: String, b: String, m: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > m) return m + 1
        val p = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = p[0]; p[0] = i; var min = i
            for (j in 1..b.length) {
                val tmp = p[j]
                p[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, p[j], p[j - 1])
                prev = tmp
                if (p[j] < min) min = p[j]
            }
            if (min > m) return m + 1
        }
        return p[b.length]
    }

    fun search(index: Index, query: String, limit: Int = 24): List<Hit> {
        val n = normalize(query)
        if (n.isEmpty()) return emptyList()
        val toks = n.split(' ').filter { it.isNotEmpty() }
        val terms = HashSet<String>()
        SYN.forEach { (k, arr) ->
            val all = (listOf(k) + arr).map(::normalize)
            if (toks.any { tk -> tk.length > 2 && all.any { w -> w == tk || w.contains(tk) || tk.contains(w) } }) terms.addAll(all)
        }
        val out = ArrayList<Hit>()
        index.entries.forEach { e ->
            var s = 0.0
            if (e.text.contains(n)) s += 10
            toks.forEach { tk ->
                if (e.text.contains(tk)) s += 4
                else if (tk.length >= 4) {
                    val m = if (tk.length >= 7) 2 else 1
                    if (e.words.any { w -> lev(w, tk, m) <= m }) s += 2
                }
            }
            terms.forEach { w -> if (w.length > 2 && e.text.contains(w)) s += 1.5 }
            if (s > 0) out += Hit.Channel(e.category, e.channel, s)
        }
        index.catalog.categories.forEach { cat ->
            val c = normalize("${cat.name.arabic} ${cat.name.english}")
            if (c.contains(n) || toks.all { c.contains(it) }) out += Hit.Section(cat, 12.0)
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    fun label(lang: AppLanguage, text: com.sami.livetv.domain.model.LocalizedText): String =
        if (lang == AppLanguage.English) text.english.ifBlank { text.arabic } else text.arabic
}
