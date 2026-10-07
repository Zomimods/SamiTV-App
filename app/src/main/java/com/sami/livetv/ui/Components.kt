package com.sami.livetv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.TvChannel
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette

// ---------- image loading: same fallback chain as the website's loadImg() ----------
fun cleanImageUrl(raw: String?): String {
    var u = (raw ?: "").trim().trim('"', '\'')
    if (u.isEmpty()) return ""
    if (u.startsWith("//")) u = "https:$u"
    if (u.startsWith("http://", ignoreCase = true)) u = "https://" + u.substring(7)
    Regex("drive\\.google\\.com/file/d/([\\w-]+)").find(u)?.let { return "https://lh3.googleusercontent.com/d/" + it.groupValues[1] }
    Regex("drive\\.google\\.com/(?:open|uc)\\?(?:[^#]*&)?id=([\\w-]+)").find(u)?.let { return "https://lh3.googleusercontent.com/d/" + it.groupValues[1] }
    Regex("^https?://(?:www\\.)?imgur\\.com/(?!a/|gallery/)(\\w+)$", RegexOption.IGNORE_CASE).find(u)?.let { return "https://i.imgur.com/" + it.groupValues[1] + ".png" }
    if (u.contains("dropbox.com", true)) u = u.replace(Regex("[?&]dl=0"), "").replace("www.dropbox.com", "dl.dropboxusercontent.com")
    return u
}

fun imageCandidates(raw: String?): List<String> {
    val u = cleanImageUrl(raw)
    if (u.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    out += u
    if (u.contains("hyphenhyphen")) out += u.replace("hyphenhyphen", "--")
    if (!Regex("^https://(wsrv\\.nl|images\\.weserv\\.nl)/", RegexOption.IGNORE_CASE).containsMatchIn(u)) {
        out += "https://wsrv.nl/?url=" + java.net.URLEncoder.encode(u.replace(Regex("^https?://", RegexOption.IGNORE_CASE), ""), "UTF-8") +
            "&w=256&h=256&fit=cover&output=webp"
    }
    return out
}

/** Shows [fallback] until one of the candidate URLs actually loads. */
@Composable
fun SamiImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val candidates = remember(url) { imageCandidates(url) }
    var index by remember(url) { mutableIntStateOf(0) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) fallback()
        if (index < candidates.size) {
            AsyncImage(
                model = candidates[index],
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { loaded = false; index += 1 },
            )
        }
    }
}

fun categoryName(lang: AppLanguage, c: ChannelCategory) = if (lang == AppLanguage.English) c.name.english.ifBlank { c.name.arabic } else c.name.arabic
fun channelName(lang: AppLanguage, c: TvChannel) = if (lang == AppLanguage.English) c.name.english.ifBlank { c.name.arabic } else c.name.arabic

fun channelImage(c: TvChannel, dark: Boolean): String? =
    if (dark) (c.imageDark ?: c.imageLight) else (c.imageLight ?: c.imageDark)

/** Category emblem: the section image when present (like catIco), otherwise the flag/emoji. */
@Composable
fun CategoryEmblem(category: ChannelCategory, size: Dp, radius: Dp = 12.dp, fontSize: Int = 24) {
    val dark = LocalPalette.current.isDark
    val url = if (dark) (category.imageDark ?: category.imageLight) else (category.imageLight ?: category.imageDark)
    SamiImage(
        url = url,
        modifier = Modifier.size(size).clip(RoundedCornerShape(radius)),
        fallback = { Text(category.icon ?: "◉", fontSize = fontSize.sp) },
    )
}

@Composable
fun ChannelThumb(category: ChannelCategory, channel: TvChannel, size: Dp, radius: Dp = 13.dp) {
    val p = LocalPalette.current
    val own = channelImage(channel, p.isDark)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(p.surface2)
            .border(1.dp, p.line, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        if (own.isNullOrBlank()) {
            CategoryEmblem(category, size, radius, fontSize = (size.value * 0.46f).toInt())
        } else {
            SamiImage(
                url = own,
                modifier = Modifier.fillMaxSize(),
                fallback = { CategoryEmblem(category, size, radius, fontSize = (size.value * 0.46f).toInt()) },
            )
        }
    }
}

@Composable
fun LiveTag(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.clip(RoundedCornerShape(7.dp)).background(p.live.copy(alpha = .08f))
            .border(1.dp, p.live.copy(alpha = .20f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(p.live))
        Text(t("liveTag"), color = if (p.isDark) Color(0xFFFFD6DC) else p.live, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun BrandMark(size: Dp = 34.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(p.accentSoft)
            .border(1.dp, p.accentLine, RoundedCornerShape(10.dp)).drawBehind {
                val r = this.size.minDimension * 0.23f
                val cy = this.size.height / 2
                val cx = this.size.width / 2
                val stroke = Stroke(width = this.size.minDimension * 0.055f, cap = StrokeCap.Round)
                drawCircle(p.accent2, r, Offset(cx - r * 0.55f, cy), style = stroke)
                drawCircle(p.accent2, r, Offset(cx + r * 0.55f, cy), style = stroke)
                drawCircle(p.accent2, r * 0.22f, Offset(cx, cy))
            },
    )
}

@Composable
fun IconCircle(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null, active: Boolean = false) {
    val p = LocalPalette.current
    Box(
        modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) p.accentSoft else p.surface)
            .border(1.dp, if (active) p.accentLine else p.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint ?: p.text2, modifier = Modifier.size(19.dp)) }
}

@Composable
fun PillText(text: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun SectionHeader(kicker: String, title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kicker, color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        if (trailing != null) trailing() else if (sub != null) Text(sub, color = p.text3, fontSize = 11.sp)
    }
}

@Composable
fun FilterChipItem(label: String, active: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(38.dp).clip(shape)
            .background(if (active) p.accentSoft else p.surface)
            .border(BorderStroke(1.dp, if (active) p.accentLine else p.line), shape)
            .clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke()
        Text(label, color = if (active) p.text else p.text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** The website's compact channel card: 64dp thumb + name/section + favourite. */
@Composable
fun ChannelCard(
    target: WatchTarget,
    favorite: Boolean,
    quality: String,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(p.surface).border(1.dp, p.line, shape).clickable(onClick = onOpen).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            ChannelThumb(target.category, target.channel, 64.dp)
            LiveTag(Modifier.align(Alignment.TopStart).padding(5.dp).scale(0.9f))
        }
        Column(Modifier.weight(1f).height(64.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    channelName(lang, target.channel), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    categoryName(lang, target.category), color = p.text3, fontSize = 9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$quality · HLS", color = p.text3, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (favorite) p.star.copy(alpha = .14f) else p.surface2)
                        .clickable(onClick = onFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        t("favAria"), tint = if (favorite) p.star else p.text3, modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(p.accent, p.accent2))).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) { Text(text, color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

@Composable
fun EmojiCircle(emoji: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 17.sp) }
}
