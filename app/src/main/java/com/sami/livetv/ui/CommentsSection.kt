package com.sami.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sami.livetv.data.cloud.CommentRecord
import com.sami.livetv.data.cloud.PublicProfile
import com.sami.livetv.data.cloud.channelCommentPath
import com.sami.livetv.ui.i18n.I18n
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private val EMOJIS = listOf("😀", "😂", "😍", "🥰", "😎", "🤩", "👏", "🙏", "💪", "🔥", "❤️", "💚", "⭐", "🎉", "👍", "⚽", "📺", "🌹")
private const val PAGE = 40

@Composable
fun SiteCommentsSection(vm: AppViewModel) {
    CommentsThread(vm, path = "siteComments", site = true, kicker = "cm_siteKicker", title = "cm_siteTitle", sub = "cm_siteSub", placeholder = "cm_phSite")
}

@Composable
fun ChannelCommentsSection(vm: AppViewModel, target: WatchTarget) {
    CommentsThread(vm, path = channelCommentPath(target.category.id, target.channel.name.arabic), site = false, kicker = "cm_chKicker", title = "cm_chTitle", sub = "cm_chSub", placeholder = "cm_phCh")
}

@Composable
private fun ct(key: String): String = I18n.t("cm_$key", LocalLang.current)

@Composable
private fun CommentsThread(vm: AppViewModel, path: String, site: Boolean, kicker: String, title: String, sub: String, placeholder: String) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var comments by remember(path) { mutableStateOf<List<CommentRecord>>(emptyList()) }
    var loading by remember(path) { mutableStateOf(true) }
    var failed by remember(path) { mutableStateOf(false) }
    var limit by remember(path) { mutableIntStateOf(PAGE) }
    var sortTop by remember(path) { mutableStateOf(false) }
    var draft by remember(path) { mutableStateOf("") }
    var rating by remember(path) { mutableIntStateOf(0) }
    var replyTo by remember(path) { mutableStateOf<CommentRecord?>(null) }
    var replyDraft by remember(path) { mutableStateOf("") }
    var busy by remember(path) { mutableStateOf(false) }
    var lastPost by remember { mutableLongStateOf(0L) }
    var confirmDelete by remember { mutableStateOf<CommentRecord?>(null) }
    var supporters by remember { mutableIntStateOf(0) }
    var supporting by remember(vm.user) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val profiles = remember(path) { mutableStateMapOf<String, PublicProfile?>() }
    val user = vm.user

    fun say(msg: String) = vm.showToast(msg)

    LaunchedEffect(path, limit, reload) {
        loading = comments.isEmpty()
        try { comments = vm.cloud.loadComments(path, limit); failed = false } catch (e: Exception) { failed = true }
        loading = false
    }
    LaunchedEffect(comments) {
        comments.map { it.uid }.distinct().filter { !profiles.containsKey(it) }.forEach { uid ->
            profiles[uid] = runCatching { vm.cloud.publicProfile(uid) }.getOrNull()
        }
    }
    if (site) LaunchedEffect(user?.uid, reload) {
        supporters = vm.cloud.siteSupportCount()
        supporting = user != null && vm.cloud.isSupporting()
    }

    fun post(text: String, parent: String?, done: () -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) { say(I18n.t("cm_emptyText", lang)); return }
        if (Regex("https?://|www\\.", RegexOption.IGNORE_CASE).containsMatchIn(clean)) { say(I18n.t("cm_noLinks", lang)); return }
        if (System.currentTimeMillis() - lastPost < 15_000) { say(I18n.t("cm_tooFast", lang)); return }
        busy = true
        scope.launch {
            try {
                if (vm.cloud.isBanned()) { say(I18n.t("cm_banErr", lang)); return@launch }
                val name = vm.cloud.ensurePublicProfile(vm.profile)
                vm.cloud.postComment(path, name, clean, parent, if (site) rating else 0)
                lastPost = System.currentTimeMillis(); done(); reload++
            } catch (e: Exception) { say(I18n.t("cm_postErr", lang)) } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ct(kicker.removePrefix("cm_")), color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text(ct(title.removePrefix("cm_")), color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(ct(sub.removePrefix("cm_")), color = p.text3, fontSize = 11.sp)
            }
        }
        if (site) {
            val rated = comments.filter { it.parent == null && it.rating in 1..5 }
            val avg = if (rated.isEmpty()) "—" else String.format(java.util.Locale.US, "%.1f", rated.map { it.rating }.average())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(ct("sAvg"), "★ $avg", Modifier.weight(1f))
                MiniStat(ct("sCount"), comments.size.toString(), Modifier.weight(1f))
                MiniStat(ct("sFans"), supporters.toString(), Modifier.weight(1f))
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (supporting) p.accentSoft else p.surface2)
                    .border(1.dp, if (supporting) p.accentLine else p.line, RoundedCornerShape(12.dp))
                    .clickable {
                        if (user == null) { vm.accountOpen = true; say(I18n.t("cm_signInFirst", lang)) } else scope.launch {
                            try {
                                vm.cloud.setSupport(!supporting); supporting = !supporting
                                say(I18n.t(if (supporting) "cm_thanks" else "cm_unsupported", lang)); reload++
                            } catch (e: Exception) { say(I18n.t("cm_actErr", lang)) }
                        }
                    }.padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text((if (supporting) "💚 " else "🤍 ") + ct(if (supporting) "supportedSite" else "supportSite"), color = p.text, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }

        if (user == null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface2).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔒 " + ct("lockedTitle"), color = p.text, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(ct(if (site) "lockedSite" else "lockedCh"), color = p.text3, fontSize = 12.sp)
                GradientButton(ct("signIn"), { vm.accountOpen = true })
            }
        } else {
            if (site) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ct("rate"), color = p.text3, fontSize = 11.sp, modifier = Modifier.weight(1f))
                (1..5).forEach { n -> Text("★", color = if (rating >= n) p.star else p.line, fontSize = 22.sp, modifier = Modifier.clickable { rating = if (rating == n) 0 else n }) }
            }
            ComposeBox(draft, { draft = it.take(500) }, ct(placeholder.removePrefix("cm_")))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f)) {
                    androidx.compose.foundation.lazy.items(EMOJIS) { e -> Text(e, fontSize = 20.sp, modifier = Modifier.clickable { draft = (draft + e).take(500) }.padding(4.dp)) }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent).clickable(enabled = !busy) { post(draft, null) { draft = ""; rating = 0 } }.padding(12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(ct(if (busy) "sending" else "send"), color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip(ct("newest"), !sortTop) { sortTop = false }
            SortChip(ct("top"), sortTop) { sortTop = true }
        }

        when {
            loading -> Text(ct("loading"), color = p.text3, fontSize = 12.sp)
            failed && comments.isEmpty() -> Text(ct("loadErr"), color = p.bad, fontSize = 12.sp)
            comments.isEmpty() -> Text(ct("empty"), color = p.text3, fontSize = 12.sp)
            else -> {
                val byId = comments.associateBy { it.id }
                val kids = comments.filter { it.parent != null && byId.containsKey(it.parent) }.groupBy { it.parent!! }
                val tops = comments.filter { it.parent == null || !byId.containsKey(it.parent) }
                    .sortedWith(
                        compareByDescending<CommentRecord> { if (it.pinned) 1 else 0 }
                            .thenByDescending { if (sortTop) it.likes.size else 0 }
                            .thenByDescending { it.ts },
                    )
                tops.forEach { c ->
                    CommentItem(vm, c, profiles[c.uid], false, path, scope, { reload++ }, { confirmDelete = it }, { replyTo = c; replyDraft = "" })
                    kids[c.id].orEmpty().sortedBy { it.ts }.forEach { r ->
                        Box(Modifier.padding(start = 26.dp)) {
                            CommentItem(vm, r, profiles[r.uid], true, path, scope, { reload++ }, { confirmDelete = it }, { replyTo = c; replyDraft = "@" + (profiles[r.uid]?.name ?: r.name) + " " })
                        }
                    }
                    if (replyTo?.id == c.id && user != null) {
                        Column(Modifier.padding(start = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ComposeBox(replyDraft, { replyDraft = it.take(500) }, ct("phReply"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SortChip(ct("send"), true) { post(replyDraft, c.id) { replyTo = null; replyDraft = "" } }
                                SortChip(ct("cancel"), false) { replyTo = null; replyDraft = "" }
                            }
                        }
                    }
                }
                if (comments.size >= limit) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp)).clickable { limit += PAGE }.padding(11.dp), contentAlignment = Alignment.Center) {
                        Text(ct("more"), color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(ct("del")) },
            text = { Text(ct("delConfirm")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { try { vm.cloud.deleteComment(path, c.id); reload++ } catch (e: Exception) { say(I18n.t("cm_actErr", lang)) } }
                }) { Text(ct("del")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(ct("cancel")) } },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.text, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = p.text3, fontSize = 9.sp)
    }
}

@Composable
private fun SortChip(text: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, color = if (active) p.text else p.text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (active) p.accentSoft else p.surface2)
            .border(1.dp, if (active) p.accentLine else p.line, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun ComposeBox(value: String, onChange: (String) -> Unit, hint: String) {
    val p = LocalPalette.current
    BasicTextField(
        value = value, onValueChange = onChange,
        textStyle = TextStyle(color = p.text, fontSize = 13.sp), cursorBrush = SolidColor(p.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(12.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = p.text3, fontSize = 13.sp); inner() } },
    )
}

@Composable
private fun CommentItem(
    vm: AppViewModel, c: CommentRecord, profile: PublicProfile?, isReply: Boolean, path: String,
    scope: kotlinx.coroutines.CoroutineScope, onChanged: () -> Unit, onDelete: (CommentRecord) -> Unit, onReply: () -> Unit,
) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val me = vm.user
    val liked = me != null && c.likes.contains(me.uid)
    val name = profile?.name?.ifBlank { null } ?: c.name.ifBlank { "—" }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface2).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
            if (profile?.photo != null) SamiImage(profile.photo, Modifier.fillMaxSize(), fallback = { Text(name.take(1), color = p.accent2, fontWeight = FontWeight.Black) })
            else Text(name.take(1).uppercase(), color = p.accent2, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
                if (profile?.owner == true) Text(ct("owner"), color = p.onAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.accent).padding(horizontal = 6.dp, vertical = 1.dp))
                if (me != null && me.uid == c.uid) Text("(" + ct("you") + ")", color = p.text3, fontSize = 10.sp)
                Text(ago(c.ts, lang), color = p.text3, fontSize = 10.sp)
            }
            if (c.pinned) Text(ct("pinned"), color = p.warn, fontSize = 10.sp)
            if (!isReply && c.rating in 1..5) Text("★".repeat(c.rating) + "☆".repeat(5 - c.rating), color = p.star, fontSize = 12.sp)
            Text(c.text, color = p.text2, fontSize = 13.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (liked) "💚 " else "🤍 ") + ct("support") + if (c.likes.isNotEmpty()) " ${c.likes.size}" else "", color = if (liked) p.accent2 else p.text3, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (me == null) { vm.accountOpen = true } else scope.launch { try { vm.cloud.setLike(path, c.id, !liked); onChanged() } catch (e: Exception) { vm.showToast(I18n.t("cm_actErr", lang)) } }
                    })
                if (me != null) Text(ct("reply"), color = p.text3, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onReply))
                if (me != null && (me.uid == c.uid || vm.isAdmin)) Text(ct("del"), color = p.bad, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDelete(c) })
                if (vm.isAdmin && !isReply) Text(ct(if (c.pinned) "unpin" else "pin"), color = p.warn, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { scope.launch { try { vm.cloud.setPinned(path, c.id, !c.pinned); onChanged() } catch (e: Exception) { vm.showToast(I18n.t("cm_actErr", lang)) } } })
                if (vm.isAdmin && me?.uid != c.uid) Text(ct("ban"), color = p.bad, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { scope.launch { try { vm.cloud.ban(c.uid); vm.cloud.deleteComment(path, c.id); vm.showToast(I18n.t("cm_banned", lang)); onChanged() } catch (e: Exception) { vm.showToast(I18n.t("cm_actErr", lang)) } } })
            }
        }
    }
}

private fun ago(ts: Long, lang: com.sami.livetv.data.preferences.AppLanguage): String {
    if (ts <= 0) return ""
    val s = (System.currentTimeMillis() - ts) / 1000
    val en = lang == com.sami.livetv.data.preferences.AppLanguage.English
    return when {
        s < 60 -> I18n.t("cm_now", lang)
        s < 3600 -> (s / 60).let { if (en) "${it}m" else "منذ $it د" }
        s < 86400 -> (s / 3600).let { if (en) "${it}h" else "منذ $it س" }
        else -> (s / 86400).let { if (en) "${it}d" else "منذ $it يوم" }
    }
}
