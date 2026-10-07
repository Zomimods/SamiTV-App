package com.sami.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sami.livetv.data.cloud.CloudException
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.ui.i18n.I18n
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class Cmd(val label: String, val hint: String, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(vm: AppViewModel, onPlay: (WatchTarget) -> Unit, onToggleTheme: () -> Unit, onHome: () -> Unit) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun close() { vm.commandOpen = false }
    val index = vm.searchIndex
    val commands = remember(q, vm.user, index, lang) {
        val base = buildList {
            add(Cmd(I18n.t("cmdHome", lang), I18n.t("cmdHomeHint", lang)) { close(); onHome() })
            if (vm.user != null) add(Cmd(I18n.t("cmdFavorites", lang), I18n.t("cmdFavoritesHint", lang)) { close(); vm.showFavorites() })
            add(Cmd(I18n.t("cmdTheme", lang), I18n.t("cmdThemeHint", lang)) { close(); onToggleTheme() })
            add(Cmd(if (lang == AppLanguage.English) "Ask the AI assistant" else "اسأل المساعد الذكي", "SAMI AI") { close(); vm.aiOpen = true })
        }
        val hits = if (index != null && q.isNotBlank()) SamiSearch.search(index, q, 10).filterIsInstance<SamiSearch.Hit.Channel>().map {
            Cmd(channelName(lang, it.channel), categoryName(lang, it.category)) { close(); onPlay(WatchTarget(it.category, it.channel)) }
        } else emptyList()
        (base + hits).take(14)
    }
    ModalBottomSheet(onDismissRequest = { close() }, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp), cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(p.surface2).border(1.dp, p.lineStrong, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp).focusRequester(focus),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("⌘  " + t("commandPlaceholder"), color = p.text3, fontSize = 14.sp); inner() } },
            )
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(commands) { c ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { c.run() }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(c.label, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(c.hint, color = p.text3, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private data class Turn(val role: String, val text: String)
private data class Chip(val title: String, val sub: String, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(vm: AppViewModel, onPlay: (WatchTarget) -> Unit) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val en = lang == AppLanguage.English
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val msgs = remember { mutableStateListOf<Pair<String, List<Chip>>>() }
    val turns = remember { mutableStateListOf<Turn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableLongStateOf(0L) }
    val mode = if (vm.user != null) "AI" else (if (en) "Local" else "محلي")

    fun cap(): Int {
        val prefs = context.getSharedPreferences("sami_ai", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return 25 - (if (prefs.getString("d", "") == today) prefs.getInt("n", 0) else 0)
    }
    fun capUse() {
        val prefs = context.getSharedPreferences("sami_ai", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val n = if (prefs.getString("d", "") == today) prefs.getInt("n", 0) else 0
        prefs.edit().putString("d", today).putInt("n", n + 1).apply()
    }

    fun channelChip(t: WatchTarget) = Chip(channelName(lang, t.channel), categoryName(lang, t.category)) { vm.aiOpen = false; onPlay(t) }

    fun localAnswer(text: String): Pair<String, List<Chip>> {
        val idx = vm.searchIndex
        val r = if (idx == null) emptyList() else SamiSearch.search(idx, text).filterIsInstance<SamiSearch.Hit.Channel>().take(5)
        return if (r.isNotEmpty()) (if (en) "Closest matches:" else "أقرب النتائج لطلبك:") to r.map { channelChip(WatchTarget(it.category, it.channel)) }
        else (if (en) "No direct match. Today's picks:" else "لم أجد تطابقًا مباشرًا. هذه اقتراحات اليوم:") to
            (vm.catalog?.categories.orEmpty().mapNotNull { c -> c.channels.firstOrNull()?.let { WatchTarget(c, it) } }.shuffled().take(4).map(::channelChip))
    }

    fun send(raw: String) {
        val text = raw.trim().take(300)
        if (text.isEmpty() || busy) return
        if (System.currentTimeMillis() - last < 2500) { vm.showToast(if (en) "One moment…" else "انتظر لحظة…"); return }
        last = System.currentTimeMillis(); input = ""
        msgs += ("user:$text") to emptyList(); busy = true
        scope.launch {
            var answer: Pair<String, List<Chip>>? = null
            var note = ""
            if (vm.user != null) {
                if (cap() <= 0) note = if (en) "Daily AI limit reached — using local search." else "وصلت للحد اليومي للمساعد، سأجيب بالبحث المحلي."
                else try {
                    val idx = vm.searchIndex
                    val cands = LinkedHashMap<String, WatchTarget>()
                    val rows = StringBuilder()
                    var n = 0
                    fun add(t: WatchTarget) { val k = t.category.id + "::" + t.channel.name.arabic; if (cands.values.none { it.channel.id == t.channel.id } && n < 30) { n++; cands["c$n"] = t; rows.append("c$n|${channelName(lang, t.channel)}|${categoryName(lang, t.category)}|ok\n") } }
                    vm.watching?.let(::add)
                    if (idx != null) SamiSearch.search(idx, text).filterIsInstance<SamiSearch.Hit.Channel>().take(14).forEach { add(WatchTarget(it.category, it.channel)) }
                    vm.catalog?.categories?.forEach { c -> c.channels.take(2).forEach { add(WatchTarget(c, it)) } }
                    val cats = vm.catalog?.categories.orEmpty().joinToString("; ") { "k:${it.id}=${categoryName(lang, it)}" }
                    val cur = vm.watching?.let { channelName(lang, it.channel) + " (" + categoryName(lang, it.category) + ")" } ?: "none"
                    val favs = vm.favorites.take(6).joinToString("; ") { it.substringAfter("::") }.ifBlank { "none" }
                    val ctx = "CATEGORIES: $cats\nNOW_PLAYING: $cur\nFAVORITES: $favs\nRECENT: ${vm.history.take(5).joinToString("; ") { it.name }.ifBlank { "none" }}\nCANDIDATES (id|channel|category|status):\n$rows"
                    val raw2 = vm.cloud.askAi(if (en) "en" else "ar", text, ctx, turns.map { it.role to it.text })
                    capUse()
                    var reply = ""; var ids = emptyList<String>()
                    runCatching {
                        val a = raw2.indexOf('{'); val b = raw2.lastIndexOf('}')
                        if (a >= 0 && b > a) { val o = JSONObject(raw2.substring(a, b + 1)); reply = o.optString("reply", o.optString("text")); o.optJSONArray("ids")?.let { arr -> ids = (0 until arr.length()).map { arr.optString(it) } } }
                    }
                    if (reply.isBlank()) reply = raw2.replace(Regex("[{}\"]"), " ").replace(Regex("\\s+"), " ").trim()
                    if (ids.isEmpty()) ids = Regex("\\bc\\d+\\b|k:[a-z]+").findAll(raw2).map { it.value }.toList()
                    val chips = ids.distinct().mapNotNull { id ->
                        cands[id]?.let(::channelChip) ?: if (id.startsWith("k:")) vm.categoryOf(id.removePrefix("k:"))?.let { c -> Chip(categoryName(lang, c), if (en) "Open section" else "فتح القسم") { vm.aiOpen = false; vm.selectCategory(c.id) } } else null
                    }.take(4)
                    answer = reply.take(700) to chips
                } catch (e: Exception) {
                    note = (if (en) "The model is unavailable (" else "تعذّر الاتصال بالنموذج الآن (") + ((e as? CloudException)?.code ?: e.message ?: "").take(120) + (if (en) ") — local results:" else ") — نتائج البحث المحلي:")
                }
            }
            val res = answer ?: localAnswer(text)
            if (note.isNotBlank()) msgs += ("bot:$note") to emptyList()
            msgs += ("bot:" + res.first.ifBlank { if (en) "Here you go:" else "تفضّل:" }) to res.second
            turns += Turn("user", text); turns += Turn("assistant", res.first)
            while (turns.size > 8) turns.removeAt(0)
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = { vm.aiOpen = false }, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAMI AI", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(mode, color = p.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.accent).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            LazyColumn(Modifier.heightIn(min = 120.dp, max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (msgs.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Bubble(false, if (vm.user != null) (if (en) "Hi! Ask me about channels — recommendations, a type of content, or what you want to watch." else "أهلًا! اسألني عن القنوات: اقترح لي قناة، أو اطلب نوع محتوى، أو قل ما تريد مشاهدته.")
                                else (if (en) "Hi! I'm in local mode — smart channel search (synonyms, typo tolerance). Sign in to enable AI." else "أهلًا! أنا في الوضع المحلي: أبحث لك في القنوات بذكاء (مرادفات وتصحيح أخطاء). سجّل الدخول لتفعيل الذكاء الاصطناعي."))
                            listOf(if (en) "Suggest a sports channel" else "اقترح لي قناة رياضية", if (en) "What should I watch?" else "ماذا أشاهد الآن؟", if (en) "News channels" else "قنوات أخبار", if (en) "Kids channels" else "قنوات أطفال").forEach { s ->
                                Text(s, color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accentSoft).border(1.dp, p.accentLine, RoundedCornerShape(10.dp)).clickable { send(s) }.padding(horizontal = 11.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
                items(msgs.size) { i ->
                    val (m, chips) = msgs[i]
                    val user = m.startsWith("user:")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Bubble(user, m.substringAfter(':'))
                        chips.forEach { c ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface2).border(1.dp, p.line, RoundedCornerShape(12.dp)).clickable { c.run() }.padding(10.dp)) {
                                Text(c.title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Black); Text(c.sub, color = p.text3, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (busy) item { Text("…", color = p.text3, fontSize = 20.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = input, onValueChange = { input = it.take(300) }, singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 14.sp), cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 13.dp),
                    decorationBox = { inner -> Box { if (input.isEmpty()) Text(if (en) "Ask about channels…" else "اسأل عن القنوات…", color = p.text3, fontSize = 14.sp); inner() } },
                )
                Box(Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(p.accent).clickable(enabled = !busy) { send(input) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(if (en) "Send" else "إرسال", color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            Text(if (en) "The assistant can be wrong. It only knows this site's channel list, not programme schedules." else "قد يخطئ المساعد. يعتمد فقط على قائمة قنوات الموقع ولا يعرف جداول البرامج.", color = p.text3, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Bubble(user: Boolean, text: String) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(text, color = if (user) p.onAccent else p.text, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(if (user) p.accent else p.surface2).padding(horizontal = 12.dp, vertical = 9.dp))
    }
}
