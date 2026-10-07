package com.sami.livetv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette
import java.util.Locale

@Composable
fun SamiApp(
    vm: AppViewModel,
    controller: MediaController?,
    language: AppLanguage,
    onStream: (WatchTarget, StreamSource) -> Unit,
    onPip: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleLang: () -> Unit,
) {
    val p = LocalPalette.current
    var top by remember { mutableIntStateOf(0) }
    var toChannels by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    val watching = vm.watching

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.query = it }
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AppLanguage.English) "en-US" else "ar-SA")
        }
        runCatching { voice.launch(intent) }
        Unit
    }
    val play: (WatchTarget) -> Unit = { target -> vm.open(target) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen || watching == null) TopBar(vm, onToggleTheme, onToggleLang) { vm.closeWatch(); vm.showAllChannels(); top++ }
            Box(Modifier.weight(1f)) {
                if (watching != null) {
                    WatchScreen(
                        vm = vm, target = watching, controller = controller,
                        onStream = onStream, onPip = onPip,
                        onFullscreen = { fullscreen = it; onFullscreen(it) },
                        onPlayOther = { vm.open(it) },
                    )
                } else {
                    HomeScreen(vm, play, startVoice, top, toChannels)
                }
            }
        }
        if (!fullscreen) BottomNav(
            modifier = Modifier.align(Alignment.BottomCenter),
            playerActive = watching != null,
            onHome = { vm.closeWatch(); vm.showAllChannels(); top++ },
            onSearch = { vm.commandOpen = true },
            onFavorites = { vm.closeWatch(); vm.showFavorites() },
            onPlayer = { if (watching == null) { vm.history.firstOrNull()?.let { h ->
                val cat = vm.categoryOf(h.key); val ch = cat?.channels?.firstOrNull { it.name.arabic == h.name }
                if (cat != null && ch != null) vm.open(WatchTarget(cat, ch)) else toChannels++
            } ?: run { toChannels++ } } },
        )
        vm.toast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).clip(RoundedCornerShape(12.dp)).background(p.surface3).border(1.dp, p.lineStrong, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(msg, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!fullscreen && !vm.aiOpen) Box(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 88.dp).size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(p.accent).clickable { vm.aiOpen = true },
            contentAlignment = Alignment.Center,
        ) { Text("✦", color = p.onAccent, fontSize = 22.sp, fontWeight = FontWeight.Black) }
    }
    if (vm.accountOpen) AccountSheet(vm)
    if (vm.commandOpen) CommandPalette(vm, play, onToggleTheme) { vm.closeWatch(); vm.showAllChannels(); top++ }
    if (vm.aiOpen) AiAssistantSheet(vm, play)
}

@Composable
private fun BottomNav(modifier: Modifier, playerActive: Boolean, onHome: () -> Unit, onSearch: () -> Unit, onFavorites: () -> Unit, onPlayer: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().background(p.bgElev).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("⌂", t("mobileHome"), !playerActive, onHome)
        NavItem("⌕", t("mobileSearch"), false, onSearch)
        NavItem("♡", t("mobileFavorites"), false, onFavorites)
        NavItem("▶", t("mobilePlayer"), playerActive, onPlayer)
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) p.accentSoft else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = if (active) p.accent2 else p.text3, fontSize = 18.sp)
        Text(label, color = if (active) p.accent2 else p.text3, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}
