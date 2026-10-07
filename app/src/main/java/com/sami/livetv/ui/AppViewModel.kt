package com.sami.livetv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sami.livetv.data.channels.AssetChannelCatalogDataSource
import com.sami.livetv.data.channels.ChannelRepository
import com.sami.livetv.data.cloud.AuthSession
import com.sami.livetv.data.cloud.CloudException
import com.sami.livetv.data.cloud.HistoryItem
import com.sami.livetv.data.cloud.SamiCloud
import com.sami.livetv.data.cloud.UserProfile
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.data.preferences.SettingsStore
import com.sami.livetv.domain.model.ChannelCatalog
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.TvChannel
import com.sami.livetv.ui.i18n.I18n
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WatchTarget(val category: ChannelCategory, val channel: TvChannel)

/** Single source of truth mirroring the website's `state` object (V20). */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app)
    val cloud = SamiCloud(app)
    private val repository = ChannelRepository(bundledSource = AssetChannelCatalogDataSource(app))

    var catalog by mutableStateOf<ChannelCatalog?>(null); private set
    var catalogFailed by mutableStateOf(false); private set
    var searchIndex by mutableStateOf<SamiSearch.Index?>(null); private set

    // ----- browsing state -----
    var selectedCategoryId by mutableStateOf<String?>(null)
    var favoritesOnly by mutableStateOf(false)
    var query by mutableStateOf("")
    var watching by mutableStateOf<WatchTarget?>(null); private set

    // ----- account state -----
    var user by mutableStateOf<AuthSession?>(cloud.session); private set
    var profile by mutableStateOf<UserProfile?>(null); private set
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    var history by mutableStateOf<List<HistoryItem>>(emptyList()); private set
    var cloudSynced by mutableStateOf(false); private set
    var syncFailed by mutableStateOf(false); private set
    var isAdmin by mutableStateOf(false); private set
    var totalViews by mutableStateOf<Long?>(null); private set
    var accountOpen by mutableStateOf(false)
    var commandOpen by mutableStateOf(false)
    var aiOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null); private set
    var authBusy by mutableStateOf(false); private set
    var authError by mutableStateOf(""); private set
    private var pendingPlay: WatchTarget? = null
    private var toastJob: Job? = null

    init {
        I18n.load(app)
        viewModelScope.launch {
            try {
                val c = repository.loadBundledCatalog()
                catalog = c
                searchIndex = SamiSearch.Index(c)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                catalogFailed = true
            }
        }
        viewModelScope.launch { cloud.bumpViews() }
        if (cloud.session != null) viewModelScope.launch { syncFromCloud() }
    }

    fun reloadCatalog() {
        catalogFailed = false
        viewModelScope.launch {
            try {
                val c = repository.loadBundledCatalog(); catalog = c; searchIndex = SamiSearch.Index(c)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (e: Exception) { catalogFailed = true }
        }
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2400); toast = null }
    }

    // ----- helpers -----
    fun favKey(categoryId: String, channel: TvChannel): String = categoryId + "::" + channel.name.arabic
    fun isFavorite(categoryId: String, channel: TvChannel): Boolean = user != null && favorites.contains(favKey(categoryId, channel))

    fun categoryOf(id: String): ChannelCategory? = catalog?.categories?.firstOrNull { it.id == id }

    fun visibleChannels(): List<WatchTarget> {
        val c = catalog ?: return emptyList()
        val base = when {
            favoritesOnly -> c.categories.flatMap { cat -> cat.channels.filter { favorites.contains(favKey(cat.id, it)) }.map { WatchTarget(cat, it) } }
            selectedCategoryId != null -> c.categories.firstOrNull { it.id == selectedCategoryId }?.let { cat -> cat.channels.map { WatchTarget(cat, it) } } ?: emptyList()
            else -> c.categories.flatMap { cat -> cat.channels.map { WatchTarget(cat, it) } }
        }
        val q = SamiSearch.normalize(query)
        if (q.isEmpty()) return base
        return base.filter {
            SamiSearch.normalize(it.channel.name.arabic).contains(q) || SamiSearch.normalize(it.channel.name.english).contains(q) ||
                SamiSearch.normalize(it.category.name.arabic).contains(q) || SamiSearch.normalize(it.category.name.english).contains(q)
        }
    }

    fun selectCategory(id: String?) { favoritesOnly = false; selectedCategoryId = if (selectedCategoryId == id) null else id; query = "" }
    fun showFavorites() { if (user == null) { requestAccount(); return }; favoritesOnly = !favoritesOnly; if (favoritesOnly) selectedCategoryId = null; query = "" }
    fun showAllChannels() { favoritesOnly = false; selectedCategoryId = null; query = "" }

    fun requestAccount() { accountOpen = true }

    // ----- watching (account required, as on the website) -----
    fun open(target: WatchTarget): Boolean {
        if (user == null) {
            pendingPlay = target
            showToast((if (currentLang == AppLanguage.English) "Sign in to watch this channel" else "سجّل الدخول لمشاهدة هذه القناة"))
            accountOpen = true
            return false
        }
        watching = target
        pushHistory(target)
        return true
    }

    fun closeWatch() { watching = null }

    var currentLang: AppLanguage = AppLanguage.Arabic

    private fun pushHistory(t: WatchTarget) {
        val item = HistoryItem(t.category.id, t.channel.name.arabic, t.category.icon)
        val h = (listOf(item) + history.filterNot { it.key == item.key && it.name == item.name }).take(10)
        history = h
        if (cloudSynced) viewModelScope.launch { runCatching { cloud.saveHistory(h) } }
    }

    fun toggleFavorite(categoryId: String, channel: TvChannel) {
        if (user == null) { accountOpen = true; showToast((if (currentLang == AppLanguage.English) "Sign in to watch this channel" else "سجّل الدخول لمشاهدة هذه القناة")); return }
        if (!cloudSynced) return
        val k = favKey(categoryId, channel)
        val next = if (favorites.contains(k)) favorites - k else favorites + k
        favorites = next
        if (favoritesOnly && next.isEmpty()) favoritesOnly = false
        viewModelScope.launch { runCatching { cloud.saveFavorites(next) } }
    }

    // ----- account -----
    private suspend fun syncFromCloud() {
        try {
            val data = cloud.loadUser()
            favorites = data.favorites
            val merged = LinkedHashMap<String, HistoryItem>()
            (history + data.history).forEach { merged.putIfAbsent(it.key + "::" + it.name, it) }
            history = merged.values.take(10)
            profile = data.profile
            cloudSynced = true; syncFailed = false
            if (history.size != data.history.size) runCatching { cloud.saveHistory(history) }
            isAdmin = cloud.isAdmin()
            if (isAdmin) totalViews = cloud.totalViews()
            user = cloud.session
        } catch (e: Exception) {
            cloudSynced = false; syncFailed = true
        }
        // resume the channel that was requested before sign-in
        pendingPlay?.let { p -> pendingPlay = null; if (user != null) { watching = p; pushHistory(p) } }
    }

    fun submitAuth(signUp: Boolean, email: String, password: String, confirm: String, first: String, last: String, ageRaw: String) {
        authError = ""
        if (email.isBlank() || password.isBlank()) { authError = authMessage("auth/invalid-email"); return }
        var prof: UserProfile? = null
        if (signUp) {
            if (first.isBlank() || last.isBlank()) { authError = I18n.t("emailErrNameRequired", currentLang); return }
            var age: Int? = null
            if (ageRaw.isNotBlank()) {
                age = ageRaw.trim().toIntOrNull()
                if (age == null || age !in 5..120) { authError = I18n.t("emailErrAgeInvalid", currentLang); return }
            }
            if (password != confirm) { authError = (if (currentLang == AppLanguage.English) "Passwords do not match" else "كلمتا المرور غير متطابقتين"); return }
            prof = UserProfile(name = "${first.trim()} ${last.trim()}".trim(), first = first.trim(), last = last.trim(), age = age)
        }
        authBusy = true
        viewModelScope.launch {
            try {
                val s = if (signUp) cloud.signUp(email.trim(), password, prof!!) else cloud.signIn(email.trim(), password)
                user = s; accountOpen = false
                if (prof != null) profile = prof
                syncFromCloud()
            } catch (e: CloudException) {
                authError = authMessage(e.code)
            } catch (e: Exception) {
                authError = authMessage("network")
            } finally { authBusy = false }
        }
    }

    fun resetPassword(email: String) {
        authError = ""
        if (email.isBlank()) { authError = authMessage("auth/invalid-email"); return }
        viewModelScope.launch {
            try { cloud.sendPasswordReset(email.trim()); showToast(I18n.t("emailResetSent", currentLang)) }
            catch (e: CloudException) { authError = authMessage(e.code) }
            catch (e: Exception) { authError = authMessage("network") }
        }
    }

    private fun authMessage(code: String): String = when (code) {
        "auth/invalid-email" -> I18n.t("emailErrInvalid", currentLang)
        "auth/user-not-found" -> I18n.t("emailErrUserNotFound", currentLang)
        "auth/wrong-password", "auth/invalid-credential", "auth/invalid-login-credentials" -> I18n.t("emailErrWrongPassword", currentLang)
        "auth/email-already-in-use" -> I18n.t("emailErrInUse", currentLang)
        "auth/weak-password" -> I18n.t("emailErrWeakPassword", currentLang)
        else -> I18n.t("emailErrGeneric", currentLang) + " ($code)"
    }

    fun clearAuthError() { authError = "" }

    fun signOut() {
        cloud.signOut()
        user = null; profile = null; favorites = emptySet(); history = emptyList()
        cloudSynced = false; isAdmin = false; favoritesOnly = false; watching = null
    }
}
