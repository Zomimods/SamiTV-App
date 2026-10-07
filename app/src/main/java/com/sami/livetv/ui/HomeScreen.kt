package com.sami.livetv.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.ui.i18n.LocalLang
import com.sami.livetv.ui.i18n.t
import com.sami.livetv.ui.theme.LocalPalette

private const val PAGE_SIZE = 24

@Composable
fun TopBar(
    vm: AppViewModel,
    onToggleTheme: () -> Unit,
    onToggleLang: () -> Unit,
    onGoHome: () -> Unit,
) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().background(p.bgElev).statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).clickable(onClick = onGoHome), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BrandMark()
            Column {
                Text("SAMI", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text("LIVE TELEVISION", color = p.text3, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Filled.Search, t("openSearchTitle"), { vm.commandOpen = true })
            PillText(if (lang == AppLanguage.Arabic) "EN" else "ع", t("langToggleAria"), onToggleLang)
            EmojiCircle(if (p.isDark) "🌙" else "☀️", t("themeToggleAria"), onToggleTheme)
            val u = vm.user
            if (u == null) {
                IconCircle(Icons.Filled.AccountCircle, t("accountAria"), { vm.accountOpen = true })
            } else {
                val initial = (u.displayName ?: vm.profile?.name ?: u.email ?: "?").trim().take(1).uppercase()
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.accent, p.accent2)))
                        .clickable { vm.accountOpen = true },
                    contentAlignment = Alignment.Center,
                ) { Text(initial, color = p.onAccent, fontWeight = FontWeight.Black, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, onPlay: (WatchTarget) -> Unit, onVoice: () -> Unit, scrollToTop: Int, scrollToChannels: Int) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val catalog = vm.catalog
    val listState = rememberLazyListState()
    var shown by remember(vm.selectedCategoryId, vm.favoritesOnly, vm.query) { mutableIntStateOf(PAGE_SIZE) }
    val visible = remember(catalog, vm.selectedCategoryId, vm.favoritesOnly, vm.query, vm.favorites) { vm.visibleChannels() }
    val rows = remember(visible, shown) { visible.take(shown).chunked(2) }
    val uri = LocalUriHandler.current

    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) listState.animateScrollToItem(0) }
    LaunchedEffect(scrollToChannels) { if (scrollToChannels > 0) listState.animateScrollToItem(if (vm.isAdmin) 3 else 2) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(p.bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") { Hero(catalogCount = catalog?.categories?.size ?: 0, onExplore = { vm.showAllChannels() }) }
        if (vm.isAdmin && catalog != null) item(key = "admin") { AdminPanel(vm) }
        item(key = "guide") { Guide(vm, onPlay, onVoice, visible.size) }
        if (vm.user != null && vm.history.isNotEmpty() && catalog != null) item(key = "recent") { Recent(vm, onPlay) }
        item(key = "channelsHeader") {
            val title = when {
                vm.favoritesOnly -> t("quickFavs")
                vm.selectedCategoryId != null -> vm.categoryOf(vm.selectedCategoryId!!)?.let { categoryName(lang, it) } ?: t("channelsTitle")
                vm.query.isNotBlank() -> t("searchResultsTitle")
                else -> t("channelsTitle")
            }
            SectionHeader("DISCOVER", title, trailing = {
                if (vm.favoritesOnly || vm.selectedCategoryId != null || vm.query.isNotBlank()) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, p.line, RoundedCornerShape(10.dp))
                            .clickable { vm.showAllChannels() }.padding(horizontal = 11.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(if (lang == AppLanguage.Arabic) "→" else "←", color = p.accent2, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(t("backToCats"), color = p.accent2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            })
        }
        when {
            catalog == null && !vm.catalogFailed -> items(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    repeat(2) { Box(Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(16.dp)).background(p.surface2)) }
                }
            }
            vm.catalogFailed -> item { EmptyBox("⚠", "تعذّر تحميل قائمة القنوات", "اضغط للمحاولة مجددًا", onClick = { vm.reloadCatalog() }) }
            visible.isEmpty() -> item {
                EmptyBox(
                    if (vm.favoritesOnly) "♡" else "∅",
                    t(if (vm.favoritesOnly) "emptyNoFavorites" else "emptyNoResults"),
                    t(if (vm.favoritesOnly) "emptyNoFavoritesSub" else "emptyNoResultsSub"),
                )
            }
            else -> {
                items(rows.size, key = { i -> rows[i].joinToString("|") { it.channel.id } }) { i ->
                    val row = rows[i]
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        row.forEach { target ->
                            val s0 = target.channel.streams.firstOrNull()
                            ChannelCard(
                                target = target,
                                favorite = vm.isFavorite(target.category.id, target.channel),
                                quality = s0?.quality?.resolve(if (lang == AppLanguage.English) "en" else "ar") ?: t("liveTag"),
                                onOpen = { onPlay(target) },
                                onFavorite = { vm.toggleFavorite(target.category.id, target.channel) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    if (i == rows.lastIndex && shown < visible.size) {
                        LaunchedEffect(shown) { shown += PAGE_SIZE }
                    }
                }
            }
        }
        item(key = "idle") { IdleWatch() }
        item(key = "siteComments") { SiteCommentsSection(vm) }
        item(key = "about") { About(uri) }
        item(key = "footer") { Footer(vm) }
    }
}

@Composable
private fun Hero(catalogCount: Int, onExplore: () -> Unit) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(p.surface2, p.surface, p.accentSoft)))
            .border(1.dp, p.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(p.accentSoft).border(1.dp, p.accentLine, RoundedCornerShape(99.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(p.live))
            Text(t("heroKicker"), color = p.accent2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("hA"), color = p.text, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
            Text(t("hB"), color = p.accent2, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
        }
        Text(t("hP"), color = p.text2, fontSize = 14.sp, lineHeight = 22.sp)
        GradientButton(t("hBtn") + "  ↓", onExplore)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("hFoot"), color = p.text3, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                if (catalogCount == 0) "—" else if (lang == AppLanguage.English) "$catalogCount categories" else "$catalogCount تصنيفًا",
                color = p.accent2, fontSize = 11.sp, fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AdminPanel(vm: AppViewModel) {
    val p = LocalPalette.current
    val c = vm.catalog ?: return
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.accentLine, shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("adminPanelTitle"), color = p.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent).padding(horizontal = 9.dp, vertical = 3.dp))
            Text(t("adminPanelSub"), color = p.text3, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(t("statCategories"), c.categories.size.toString(), t("statCategoriesSub"), Modifier.weight(1f))
            Stat(t("statChannels"), c.channelCount.toString(), t("statChannelsSub"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(t("statSources"), c.streamCount.toString(), t("statSourcesSub"), Modifier.weight(1f))
            Stat(t("statVisits"), vm.totalViews?.toString() ?: "—", t("statVisitsHintActive"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface2).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text(label, color = p.text3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(sub, color = p.text3, fontSize = 9.sp, maxLines = 2)
    }
}

@Composable
private fun Guide(vm: AppViewModel, onPlay: (WatchTarget) -> Unit, onVoice: () -> Unit, count: Int) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    val catalog = vm.catalog
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(t("gKick"), color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text(t("gTitle"), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(t("gSub"), color = p.text3, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (count < 10) "0$count" else "$count", color = p.accent2, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(t("gCount"), color = p.text3, fontSize = 10.sp)
            }
        }
        // search box
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(shape).background(p.surface).border(1.dp, p.lineStrong, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = p.text3)
            BasicTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.query.isEmpty()) Text(t("gPh"), color = p.text3, fontSize = 14.sp)
                        inner()
                    }
                },
            )
            if (vm.query.isNotEmpty()) Icon(Icons.Filled.Clear, null, tint = p.text3, modifier = Modifier.clickable { vm.query = "" })
            Text("🎤", fontSize = 18.sp, modifier = Modifier.clickable(onClick = onVoice))
        }
        val index = vm.searchIndex
        if (vm.query.isNotBlank() && index != null) {
            val hits = remember(vm.query, index) { SamiSearch.search(index, vm.query, 8) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.lineStrong, RoundedCornerShape(14.dp)).padding(8.dp)) {
                if (hits.isEmpty()) {
                    Text(t("noResultTitle"), color = p.text, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    Text(t("noResultSub"), color = p.text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                hits.forEach { hit ->
                    when (hit) {
                        is SamiSearch.Hit.Channel -> HitRow(hit.category, categoryName(lang, hit.category), channelName(lang, hit.channel), "${hit.channel.streams.size}") { vm.query = ""; onPlay(WatchTarget(hit.category, hit.channel)) }
                        is SamiSearch.Hit.Section -> HitRow(hit.category, categoryName(lang, hit.category), t("categoryBadge"), null) { vm.query = ""; vm.selectCategory(hit.category.id) }
                    }
                }
            }
        }
        if (catalog != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChipItem(t("allChip"), active = !vm.favoritesOnly && vm.selectedCategoryId == null, onClick = { vm.showAllChannels() })
                }
                if (vm.user != null) item {
                    FilterChipItem(t("quickFavs"), active = vm.favoritesOnly, onClick = { vm.showFavorites() }, leading = {
                        Icon(Icons.Filled.Favorite, null, tint = p.live, modifier = Modifier.size(15.dp))
                    })
                }
                items(catalog.categories, key = { it.id }) { cat ->
                    FilterChipItem(categoryName(lang, cat), active = !vm.favoritesOnly && vm.selectedCategoryId == cat.id, onClick = { vm.selectCategory(cat.id) }, leading = {
                        CategoryEmblem(cat, 20.dp, 6.dp, 14)
                    })
                }
            }
        }
    }
}

@Composable
private fun HitRow(category: com.sami.livetv.domain.model.ChannelCategory, title: String, sub: String, badge: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CategoryEmblem(category, 32.dp, 9.dp, 18)
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = p.text3, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Recent(vm: AppViewModel, onPlay: (WatchTarget) -> Unit) {
    val p = LocalPalette.current
    val lang = LocalLang.current
    Column {
        SectionHeader("RECENT", t("recentTitle"), t("recentSub"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(vm.history, key = { it.key + "::" + it.name }) { item ->
                val cat = vm.categoryOf(item.key)
                val ch = cat?.channels?.firstOrNull { it.name.arabic == item.name }
                if (cat != null && ch != null) {
                    Row(
                        Modifier.width(188.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp))
                            .clickable { onPlay(WatchTarget(cat, ch)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        ChannelThumb(cat, ch, 40.dp, 11.dp)
                        Column {
                            Text(channelName(lang, ch), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(categoryName(lang, cat), color = p.text3, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleWatch() {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("wKick"), color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(t("wTitle"), color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(p.surface3, p.bg))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SAMI · LIVE TELEVISION", color = p.accent2, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                Text(t("placeholderTitleDefault"), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(t("placeholderTextDefault"), color = p.text3, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
        Text(t("readyTitle"), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(t("readySub"), color = p.text3, fontSize = 11.sp)
    }
}

@Composable
private fun About(uri: androidx.compose.ui.platform.UriHandler) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("ABOUT", t("aboutTitle"), t("aboutSub"))
        Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("aboutQ1" to "aboutA1", "aboutQ2" to "aboutA2", "aboutQ3" to "aboutA3").forEach { (q, a) ->
                Text(t(q), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(t(a), color = p.text2, fontSize = 12.sp, lineHeight = 20.sp)
            }
            Accordion(t("faqTitle")) {
                listOf("faq1" to "faq1", "faq2" to "faq2", "faq3" to "faq3").forEach { (k, _) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(t(k + "q"), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(t(k + "a"), color = p.text2, fontSize = 12.sp)
                    }
                }
            }
            Accordion(t("privTitle")) {
                Text(t("priv1"), color = p.text2, fontSize = 12.sp, lineHeight = 20.sp)
                Text(t("priv2"), color = p.text2, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 6.dp)) {
                    Text(t("priv3l1"), color = p.accent2, fontSize = 12.sp, modifier = Modifier.clickable { uri.openUri("https://adssettings.google.com") })
                    Text("  ·  ", color = p.text3, fontSize = 12.sp)
                    Text(t("priv3l2"), color = p.accent2, fontSize = 12.sp, modifier = Modifier.clickable { uri.openUri("https://policies.google.com/technologies/partner-sites") })
                }
                Text(t("priv3c").trimStart('.', ' ', '،'), color = p.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Accordion(t("termsTitle")) {
                listOf("terms1", "terms2", "terms3", "terms4").forEach { Text("• " + t(it), color = p.text2, fontSize = 12.sp, lineHeight = 20.sp) }
            }
            Accordion(t("contactTitle")) {
                Text(t("contactA"), color = p.text2, fontSize = 12.sp)
                Text("Instagram", color = p.accent2, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp).clickable { uri.openUri("https://www.instagram.com/4y.c6") })
            }
        }
    }
}

@Composable
private fun Accordion(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface2).border(1.dp, p.line, shape).animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.text3)
        }
        if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), content = content)
    }
}

@Composable
private fun Footer(vm: AppViewModel) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandMark(26.dp)
            Text("SAMI", color = p.text, fontWeight = FontWeight.Black)
        }
        Text(t("footTag"), color = p.text3, fontSize = 11.sp)
        Text(t("footerCredit") + "م. سامي الحيدري © 2026", color = p.text2, fontSize = 11.sp)
        Text("TV VIP LIVE ENGINE · TV 2027", color = p.text3, fontSize = 10.sp)
    }
}

@Composable
fun EmptyBox(icon: String, title: String, sub: String, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(icon, fontSize = 28.sp)
        Text(title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(sub, color = p.text3, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
