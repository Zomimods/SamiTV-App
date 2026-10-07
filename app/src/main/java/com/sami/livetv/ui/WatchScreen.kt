package com.sami.livetv.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.ContentValues
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette

private enum class Signal { Waiting, Connecting, Stable, Bad }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WatchScreen(
    vm: AppViewModel,
    target: WatchTarget,
    controller: MediaController?,
    onStream: (WatchTarget, StreamSource) -> Unit,
    onPip: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onPlayOther: (WatchTarget) -> Unit,
) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val tag = if (lang == AppLanguage.English) "en" else "ar"
    var fullscreen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var state by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var error by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var muted by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val channelId = target.channel.id

    BackHandler {
        if (fullscreen) { fullscreen = false; onFullscreen(false) } else vm.closeWatch()
    }
    DisposableEffect(Unit) { onDispose { if (fullscreen) onFullscreen(false) } }

    // start (or resume) the first source whenever this channel opens
    var startedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(channelId, controller) {
        if (controller != null && startedFor != channelId) {
            startedFor = channelId
            val current = controller.currentMediaMetadata.extras?.getString(com.sami.livetv.playback.PlaybackService.EXTRA_CHANNEL_ID)
            if (current != channelId || !controller.isPlaying) target.channel.streams.firstOrNull()?.let { onStream(target, it) }
        }
    }
    DisposableEffect(controller, channelId) {
        val listener = object : Player.Listener {
            fun refresh() {
                val c = controller ?: return
                isPlaying = c.isPlaying; state = c.playbackState
                activeId = c.currentMediaItem?.mediaId
                muted = c.volume == 0f
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState != Player.STATE_IDLE) error = false; refresh() }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { error = false; refresh() }
            override fun onPlayerError(e: PlaybackException) { error = true; refresh() }
        }
        controller?.addListener(listener); listener.refresh()
        onDispose { controller?.removeListener(listener) }
    }
    val signal = when {
        error -> Signal.Bad
        isPlaying -> Signal.Stable
        controller == null || state == Player.STATE_IDLE -> Signal.Waiting
        else -> Signal.Connecting
    }

    val playerBox: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        player = controller
                        playerView = this
                    }
                },
                update = { v -> if (v.player !== controller) v.player = controller },
                onRelease = { v -> v.player = null; if (playerView === v) playerView = null },
                modifier = Modifier.fillMaxSize(),
            )
            if (signal != Signal.Stable) {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("SAMI · LIVE TELEVISION", color = p.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    Text(
                        if (signal == Signal.Bad) t("itemFailedTitle") else t("connecting"),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black,
                    )
                    if (signal == Signal.Bad) {
                        Text(t("itemFailedText"), color = Color(0xFFB8C7CC), fontSize = 11.sp)
                        Text(t("retryBtn"), color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accent).clickable {
                                error = false; (target.channel.streams.firstOrNull { it.id == activeId } ?: target.channel.streams.firstOrNull())?.let { onStream(target, it) }
                            }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerButton(if (isPlaying) "⏸" else "▶", t("playPauseAria")) { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
                PlayerButton(if (muted) "🔇" else "🔊", t("muteAria")) { controller?.let { it.volume = if (it.volume == 0f) 1f else 0f; muted = it.volume == 0f } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PlayerButton("⧉", t("pipAria"), onPip)
                PlayerButton("↗", t("shareAria")) { shareChannel(context, channelName(lang, target.channel), categoryName(lang, target.category)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PlayerButton("◻", t("screenshotAria")) {
                    captureScreenshot(context, playerView) { ok -> vm.showToast(com.sami.livetv.ui.i18n.I18n.t(if (ok) "screenshotSaved" else "screenshotFailed", lang)) }
                }
                Spacer(Modifier.weight(1f))
                PlayerButton(if (fullscreen) "⤡" else "⛶", t("fullscreenAria")) { fullscreen = !fullscreen; onFullscreen(fullscreen) }
            }
        }
    }

    if (fullscreen) {
        playerBox(Modifier.fillMaxSize())
        return
    }

    val related = remember(target) { target.category.channels.filter { it.id != target.channel.id }.take(30) }
    LazyColumn(Modifier.fillMaxSize().background(p.bg), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp)).clickable { vm.closeWatch() }.padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (lang == AppLanguage.Arabic) "→" else "←", color = p.accent2, fontWeight = FontWeight.Black)
                    Text(t("watchBack"), color = p.accent2, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                val fav = vm.isFavorite(target.category.id, target.channel)
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(if (fav) p.star.copy(alpha = .14f) else p.surface).border(1.dp, if (fav) p.star else p.line, RoundedCornerShape(10.dp))
                        .clickable { vm.toggleFavorite(target.category.id, target.channel) }.padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (fav) "★" else "☆", color = p.star, fontWeight = FontWeight.Black)
                    Text(t("watchFav"), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW PLAYING", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text(t("playerTitle"), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(t("currentContentPrefix") + categoryName(lang, target.category), color = p.text3, fontSize = 11.sp)
            }
        }
        item { playerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp))) }
        item {
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(if (p.isDark) p.surface else Color(0xFF0A141B)).border(1.dp, p.line, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelThumb(target.category, target.channel, 52.dp, 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(t("sideTitleCurrent"), color = Color(0xFF7F949C), fontSize = 10.sp)
                        Text(channelName(lang, target.channel), color = Color(0xFFF2F7F8), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val n = target.channel.streams.size
                Text(categoryName(lang, target.category) + " · " + (if (lang == AppLanguage.English) "$n ${if (n == 1) "source" else "sources"}" else "$n مصدر"), color = Color(0xFFB8C7CC), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(when (signal) { Signal.Stable -> p.good; Signal.Bad -> p.bad; Signal.Connecting -> p.warn; Signal.Waiting -> Color(0xFF7F949C) }))
                    Text(
                        when (signal) { Signal.Stable -> t("stableSignal"); Signal.Bad -> t("sourceFailed"); Signal.Connecting -> t("connecting"); Signal.Waiting -> t("waitingSelection") },
                        color = Color(0xFFF2F7F8), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Text(t("sideTitleSources"), color = Color(0xFF7F949C), fontSize = 11.sp, fontWeight = FontWeight.Black)
                if (target.channel.streams.isEmpty()) Text(t("noSourcesForItem"), color = Color(0xFFB8C7CC), fontSize = 12.sp)
                target.channel.streams.forEachIndexed { i, s ->
                    val active = s.id == activeId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) p.accentSoft else Color(0x14FFFFFF))
                            .border(1.dp, if (active) p.accentLine else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .clickable { onStream(target, s) }.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.quality.resolve(tag).ifBlank { t("sourceOrdinalPrefix") + (i + 1) }, color = Color(0xFFF2F7F8), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(if (active) t("activeMark") else t("playMark"), color = if (active) p.accent2 else Color(0x80F2F7F8), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item(key = "chComments-$channelId") { ChannelCommentsSection(vm, target) }
        if (related.isNotEmpty()) item {
            Column {
                SectionHeader("MORE", t("relatedTitle"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(related, key = { it.id }) { ch ->
                        Row(
                            Modifier.width(188.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp))
                                .clickable { onPlayOther(WatchTarget(target.category, ch)) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            ChannelThumb(target.category, ch, 40.dp, 11.dp)
                            Column {
                                Text(channelName(lang, ch), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(categoryName(lang, target.category), color = p.text3, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp) }
}

private fun shareChannel(context: Context, name: String, category: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, "$name — $category\nSAMI Live TV")
    }
    context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun captureScreenshot(context: Context, view: PlayerView?, done: (Boolean) -> Unit) {
    val surface = view?.videoSurfaceView as? SurfaceView
    if (surface == null || surface.width == 0 || surface.height == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { done(false); return }
    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(surface, bitmap, { result ->
        if (result != PixelCopy.SUCCESS) { done(false); return@request }
        val ok = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SAMI_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SAMI")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
        done(ok)
    }, Handler(Looper.getMainLooper()))
}
