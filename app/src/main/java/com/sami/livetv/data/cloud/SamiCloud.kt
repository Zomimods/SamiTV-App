package com.sami.livetv.data.cloud

import android.content.Context
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Same Firebase project and the same Realtime Database layout as the V20 website, reached over the
 * official Firebase REST APIs (so no google-services.json is needed and web + app share one account,
 * favourites, history and comments).
 */
object SamiCloudConfig {
    const val API_KEY = "AIzaSyA9BGGiXjaOG8CXCWQvynXwBVpmWRM9bdI"
    const val DATABASE_URL = "https://plrcet-default-rtdb.firebaseio.com"
    // Sent as Referer/Origin so a key restricted to the website's domain keeps working from the app.
    const val REFERER = "https://plrcet.firebaseapp.com/"
    const val AI_ENDPOINT = "https://sami.alhydrys220.workers.dev/"
    const val HLS_PROXY = "https://bitter-brook-6557.alhydrys220.workers.dev"
}

class CloudException(val code: String, message: String = code) : Exception(message)

data class AuthSession(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
)

data class UserProfile(
    val name: String = "",
    val first: String = "",
    val last: String = "",
    val age: Int? = null,
    val photo: String? = null,
)

data class UserCloudData(
    val favorites: Set<String> = emptySet(),
    val history: List<HistoryItem> = emptyList(),
    val profile: UserProfile? = null,
)

data class HistoryItem(val key: String, val name: String, val icon: String?)

data class PublicProfile(val name: String, val photo: String?, val owner: Boolean)

data class CommentRecord(
    val id: String,
    val uid: String,
    val name: String,
    val text: String,
    val ts: Long,
    val parent: String?,
    val rating: Int,
    val likes: Set<String>,
    val pinned: Boolean,
)

class SamiCloud(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sami_cloud_session", Context.MODE_PRIVATE)

    @Volatile
    var session: AuthSession? = readSession()
        private set

    // ---------- session persistence ----------
    private fun readSession(): AuthSession? {
        val uid = prefs.getString("uid", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        return AuthSession(
            uid = uid,
            email = prefs.getString("email", null),
            displayName = prefs.getString("name", null),
            idToken = prefs.getString("id", "") ?: "",
            refreshToken = refresh,
            expiresAtMs = prefs.getLong("exp", 0L),
        )
    }

    private fun store(value: AuthSession?) {
        session = value
        prefs.edit().apply {
            if (value == null) clear() else {
                putString("uid", value.uid)
                putString("email", value.email)
                putString("name", value.displayName)
                putString("id", value.idToken)
                putString("refresh", value.refreshToken)
                putLong("exp", value.expiresAtMs)
            }
        }.apply()
    }

    // ---------- low level HTTP ----------
    private data class Reply(val code: Int, val body: String, val etag: String?)

    private suspend fun http(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Reply = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 12_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Referer", SamiCloudConfig.REFERER)
            conn.setRequestProperty("Origin", SamiCloudConfig.REFERER.trimEnd('/'))
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            Reply(code, text, conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }

    // ---------- Firebase Auth (REST) ----------
    private suspend fun authCall(endpoint: String, payload: JSONObject): JSONObject {
        val reply = http(
            "POST",
            "https://identitytoolkit.googleapis.com/v1/accounts:$endpoint?key=${SamiCloudConfig.API_KEY}",
            payload.toString(),
        )
        val json = runCatching { JSONObject(reply.body) }.getOrNull() ?: JSONObject()
        if (reply.code !in 200..299) {
            val msg = json.optJSONObject("error")?.optString("message").orEmpty()
            throw CloudException(mapAuthCode(msg), msg)
        }
        return json
    }

    private fun mapAuthCode(message: String): String = when {
        message.startsWith("EMAIL_EXISTS") -> "auth/email-already-in-use"
        message.startsWith("INVALID_EMAIL") -> "auth/invalid-email"
        message.startsWith("EMAIL_NOT_FOUND") -> "auth/user-not-found"
        message.startsWith("INVALID_PASSWORD") -> "auth/wrong-password"
        message.startsWith("INVALID_LOGIN_CREDENTIALS") -> "auth/invalid-login-credentials"
        message.startsWith("WEAK_PASSWORD") -> "auth/weak-password"
        message.startsWith("TOO_MANY_ATTEMPTS") -> "auth/too-many-requests"
        message.isBlank() -> "network"
        else -> message.lowercase()
    }

    private fun sessionFrom(json: JSONObject, fallbackName: String? = null): AuthSession {
        val expires = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
        return AuthSession(
            uid = json.optString("localId"),
            email = json.optString("email").ifBlank { null },
            displayName = json.optString("displayName").ifBlank { fallbackName },
            idToken = json.optString("idToken"),
            refreshToken = json.optString("refreshToken"),
            expiresAtMs = System.currentTimeMillis() + expires * 1000L - 60_000L,
        )
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        val json = authCall(
            "signInWithPassword",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true),
        )
        return sessionFrom(json).also(::store)
    }

    suspend fun signUp(email: String, password: String, profile: UserProfile): AuthSession {
        val json = authCall(
            "signUp",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true),
        )
        var s = sessionFrom(json, profile.name)
        store(s)
        runCatching {
            val upd = authCall(
                "update",
                JSONObject().put("idToken", s.idToken).put("displayName", profile.name).put("returnSecureToken", true),
            )
            s = sessionFrom(upd, profile.name).copy(email = s.email)
            store(s)
        }
        runCatching { saveProfile(profile) }
        return s
    }

    suspend fun sendPasswordReset(email: String) {
        authCall("sendOobCode", JSONObject().put("requestType", "PASSWORD_RESET").put("email", email))
    }

    fun signOut() = store(null)

    private suspend fun token(): String? {
        val s = session ?: return null
        if (s.idToken.isNotBlank() && System.currentTimeMillis() < s.expiresAtMs) return s.idToken
        val reply = http(
            "POST",
            "https://securetoken.googleapis.com/v1/token?key=${SamiCloudConfig.API_KEY}",
            "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, "UTF-8"),
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        ).let { r -> r }
        if (reply.code !in 200..299) {
            if (reply.code == 400) store(null)
            return null
        }
        val j = JSONObject(reply.body)
        val updated = s.copy(
            idToken = j.optString("id_token"),
            refreshToken = j.optString("refresh_token", s.refreshToken),
            expiresAtMs = System.currentTimeMillis() + (j.optString("expires_in", "3600").toLongOrNull() ?: 3600L) * 1000L - 60_000L,
        )
        store(updated)
        return updated.idToken
    }

    // ---------- Realtime Database (REST) ----------
    private suspend fun dbUrl(path: String, extra: String = "", authed: Boolean = true): String {
        val t = if (authed) token() else null
        val params = buildList {
            if (t != null) add("auth=" + URLEncoder.encode(t, "UTF-8"))
            if (extra.isNotBlank()) add(extra)
        }.joinToString("&")
        return SamiCloudConfig.DATABASE_URL + "/" + path + ".json" + if (params.isEmpty()) "" else "?$params"
    }

    private suspend fun dbGet(path: String, extra: String = "", authed: Boolean = true): String? {
        val r = http("GET", dbUrl(path, extra, authed))
        if (r.code !in 200..299) throw CloudException("db/${r.code}")
        return r.body.takeIf { it.isNotBlank() && it != "null" }
    }

    private suspend fun dbPut(path: String, json: String) {
        val r = http("PUT", dbUrl(path), json)
        if (r.code !in 200..299) throw CloudException("db/${r.code}")
    }

    private suspend fun dbDelete(path: String) {
        val r = http("DELETE", dbUrl(path))
        if (r.code !in 200..299) throw CloudException("db/${r.code}")
    }

    // ---------- account data: users/<uid> ----------
    suspend fun loadUser(): UserCloudData {
        val uid = session?.uid ?: return UserCloudData()
        val raw = dbGet("users/$uid") ?: return UserCloudData()
        val root = JSONObject(raw)
        val favorites = buildSet {
            val f = root.optJSONObject("favorites")
            f?.keys()?.forEach { if (f.optBoolean(it, false)) add(it) }
        }
        val history = buildList {
            val h = root.opt("history")
            val arr: JSONArray? = when (h) {
                is JSONArray -> h
                is JSONObject -> JSONArray().also { a -> h.keys().asSequence().sorted().forEach { k -> a.put(h.opt(k)) } }
                else -> null
            }
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val key = o.optString("key"); if (key.isBlank()) continue
                add(HistoryItem(key, o.optString("name"), o.optString("icon").ifBlank { null }))
            }
        }
        val profile = root.optJSONObject("profile")?.let {
            UserProfile(
                name = it.optString("name"),
                first = it.optString("first"),
                last = it.optString("last"),
                age = if (it.has("age")) it.optInt("age") else null,
                photo = it.optString("photo").ifBlank { null },
            )
        }
        return UserCloudData(favorites, history, profile)
    }

    suspend fun saveFavorites(favorites: Set<String>) {
        val uid = session?.uid ?: return
        val o = JSONObject()
        favorites.forEach { o.put(it, true) }
        dbPut("users/$uid/favorites", if (favorites.isEmpty()) "null" else o.toString())
    }

    suspend fun saveHistory(history: List<HistoryItem>) {
        val uid = session?.uid ?: return
        val a = JSONArray()
        history.forEach { a.put(JSONObject().put("key", it.key).put("name", it.name).apply { it.icon?.let { i -> put("icon", i) } }) }
        dbPut("users/$uid/history", if (history.isEmpty()) "null" else a.toString())
    }

    suspend fun saveProfile(profile: UserProfile) {
        val uid = session?.uid ?: return
        val o = JSONObject().put("first", profile.first).put("last", profile.last).put("name", profile.name)
        profile.age?.let { o.put("age", it) }
        profile.photo?.let { o.put("photo", it) }
        dbPut("users/$uid/profile", o.toString())
    }

    suspend fun isAdmin(): Boolean {
        val uid = session?.uid ?: return false
        return runCatching { dbGet("admins/$uid") == "true" }.getOrDefault(false)
    }

    // ---------- site visits counter: site_stats/total_views ----------
    suspend fun totalViews(): Long? = runCatching { dbGet("site_stats/total_views", authed = false)?.trim()?.toLongOrNull() }.getOrNull()

    suspend fun bumpViews() {
        runCatching {
            repeat(3) {
                val get = http("GET", dbUrl("site_stats/total_views", authed = false), null, mapOf("X-Firebase-ETag" to "true"))
                if (get.code !in 200..299) return
                val current = get.body.trim().toLongOrNull() ?: 0L
                val put = http(
                    "PUT",
                    dbUrl("site_stats/total_views", authed = false),
                    (current + 1).toString(),
                    mapOf("if-match" to (get.etag ?: "null_etag")),
                )
                if (put.code in 200..299) return
            }
        }
    }

    // ---------- comments (same schema as the website) ----------
    suspend fun isBanned(): Boolean {
        val uid = session?.uid ?: return false
        return runCatching { dbGet("bans/$uid") == "true" }.getOrDefault(false)
    }

    suspend fun loadComments(path: String, limit: Int): List<CommentRecord> {
        val raw = dbGet(path, "orderBy=%22%24key%22&limitToLast=$limit", authed = false) ?: return emptyList()
        val root = JSONObject(raw)
        val out = ArrayList<CommentRecord>()
        root.keys().forEach { id ->
            val o = root.optJSONObject(id) ?: return@forEach
            val text = o.optString("text"); val uid = o.optString("uid")
            if (text.isBlank() || uid.isBlank()) return@forEach
            val likes = buildSet { o.optJSONObject("likes")?.let { l -> l.keys().forEach { k -> add(k) } } }
            out += CommentRecord(
                id = id,
                uid = uid,
                name = o.optString("name"),
                text = text,
                ts = o.optLong("ts", 0L),
                parent = o.optString("parent").ifBlank { null },
                rating = o.optInt("rating", 0),
                likes = likes,
                pinned = o.optBoolean("pinned", false),
            )
        }
        return out
    }

    suspend fun publicProfile(uid: String): PublicProfile? {
        val raw = dbGet("publicProfiles/$uid", authed = false) ?: return null
        val o = JSONObject(raw)
        return PublicProfile(o.optString("name"), o.optString("photo").ifBlank { null }, o.optString("badge") == "owner")
    }

    suspend fun ensurePublicProfile(fallback: UserProfile?): String {
        val s = session ?: throw CloudException("no-auth")
        val name = (s.displayName?.takeIf { it.isNotBlank() }
            ?: fallback?.name?.takeIf { it.isNotBlank() }
            ?: s.email?.substringBefore('@')
            ?: "مستخدم").trim().take(40)
        val o = JSONObject().put("name", name)
        fallback?.photo?.takeIf { it.length < 50_000 && it.startsWith("https://") }?.let { o.put("photo", it) }
        dbPut("publicProfiles/${s.uid}", o.toString())
        return name
    }

    suspend fun postComment(path: String, name: String, text: String, parent: String?, rating: Int) {
        val s = session ?: throw CloudException("no-auth")
        val rec = JSONObject().put("uid", s.uid).put("name", name).put("text", text.take(500))
            .put("ts", JSONObject().put(".sv", "timestamp"))
        if (parent != null) rec.put("parent", parent) else if (rating in 1..5) rec.put("rating", rating)
        val r = http("POST", dbUrl(path), rec.toString())
        if (r.code !in 200..299) throw CloudException("db/${r.code}")
    }

    suspend fun setLike(path: String, id: String, like: Boolean) {
        val uid = session?.uid ?: throw CloudException("no-auth")
        if (like) dbPut("$path/$id/likes/$uid", "true") else dbDelete("$path/$id/likes/$uid")
    }

    suspend fun setPinned(path: String, id: String, pinned: Boolean) {
        if (pinned) dbPut("$path/$id/pinned", "true") else dbDelete("$path/$id/pinned")
    }

    suspend fun deleteComment(path: String, id: String) = dbDelete("$path/$id")

    suspend fun ban(uid: String) = dbPut("bans/$uid", "true")

    suspend fun siteSupportCount(): Int =
        runCatching { dbGet("siteSupport", "shallow=true", authed = false)?.let { JSONObject(it).length() } ?: 0 }.getOrDefault(0)

    suspend fun isSupporting(): Boolean {
        val uid = session?.uid ?: return false
        return runCatching { dbGet("siteSupport/$uid") == "true" }.getOrDefault(false)
    }

    suspend fun setSupport(on: Boolean) {
        val uid = session?.uid ?: throw CloudException("no-auth")
        if (on) dbPut("siteSupport/$uid", "true") else dbDelete("siteSupport/$uid")
    }

    // ---------- AI assistant (same Cloudflare Worker as the website) ----------
    suspend fun askAi(lang: String, message: String, context: String, history: List<Pair<String, String>>): String {
        val h = JSONArray()
        history.takeLast(6).forEach { h.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val body = JSONObject().put("lang", lang).put("message", message).put("context", context).put("history", h)
        val r = http("POST", SamiCloudConfig.AI_ENDPOINT, body.toString())
        if (r.code !in 200..299) throw CloudException("http ${r.code}")
        val text = JSONObject(r.body).optString("text")
        if (text.isBlank()) throw CloudException("empty")
        return text
    }
}

/** Port of the website's cmChId so channel comments land in the very same database node. */
fun channelCommentPath(categoryKey: String, channelName: String): String {
    val str = "$categoryKey::$channelName"
    var h1 = 0xdeadbeef.toInt()
    var h2 = 0x41c6ce57
    for (ch in str) {
        val c = ch.code
        h1 = (h1 xor c) * 2654435761L.toInt()
        h2 = (h2 xor c) * 1597334677
    }
    h1 = ((h1 xor (h1 ushr 16)) * 2246822507L.toInt()) xor ((h2 xor (h2 ushr 13)) * 3266489909L.toInt())
    h2 = ((h2 xor (h2 ushr 16)) * 2246822507L.toInt()) xor ((h1 xor (h1 ushr 13)) * 3266489909L.toInt())
    val value = 4294967296.0 * (2097151 and h2) + (h1.toLong() and 0xFFFFFFFFL).toDouble()
    return "channelComments/c_" + value.toLong()
}
