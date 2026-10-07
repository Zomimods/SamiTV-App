package com.sami.livetv.playback

import android.net.Uri
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var hlsMediaSourceFactory: HlsMediaSource.Factory

    private var activeRequest: PlaybackRequest? = null
    private var attempts: List<Attempt> = emptyList()
    private var attemptCursor: Int = 0

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!startNextFallback()) {
                Log.w(TAG, "Playback failed; no untried stream sources remain.", error)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Util.getUserAgent(this, USER_AGENT))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(NETWORK_TIMEOUT_MS)
            .setReadTimeoutMs(NETWORK_TIMEOUT_MS)
        hlsMediaSourceFactory = HlsMediaSource.Factory(DefaultDataSource.Factory(this, httpDataSourceFactory))

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.addListener(playerListener)
            }
        val sessionActivity = PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            Intent(this, com.sami.livetv.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onDestroy() {
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }
        super.onDestroy()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PLAY_STREAM_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != PLAY_STREAM_ACTION) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return try {
                playStream(PlaybackRequest.fromBundle(args))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } catch (cause: IllegalArgumentException) {
                Log.w(TAG, "Rejected an invalid playback request.", cause)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
            } catch (cause: Exception) {
                Log.e(TAG, "Could not prepare the requested stream.", cause)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_IO))
            }
        }
    }

    private enum class Mode { PLAIN, MASTER, MERGE, VIDEO_ONLY }

    private data class Attempt(val sourceIndex: Int, val mode: Mode, val proxy: Boolean)

    private fun playStream(request: PlaybackRequest) {
        activeRequest = request
        val order = (request.selectedIndex until request.sources.size).toList() +
            (0 until request.selectedIndex).toList()
        // Per source: MASTER playlist (video+audio together) -> MergingMediaSource -> video only.
        // Per mode: proxy first for px channels (direct as fallback); direct first otherwise.
        attempts = order.flatMap { index ->
            val source = request.sources[index]
            val modes = if (source.audioUrl != null) {
                listOf(Mode.MASTER, Mode.MERGE, Mode.VIDEO_ONLY)
            } else {
                listOf(Mode.PLAIN)
            }
            val proxies = if (source.requiresProxy) listOf(true, false) else listOf(false, true)
            modes.flatMap { mode -> proxies.map { proxy -> Attempt(index, mode, proxy) } }
        }.take(MAX_ATTEMPTS)
        attemptCursor = 0
        if (!startNextFallback()) {
            throw IllegalStateException("No playable attempt could be prepared.")
        }
    }

    private fun startNextFallback(): Boolean {
        while (attemptCursor < attempts.size) {
            val attempt = attempts[attemptCursor++]
            try {
                Log.i(TAG, "Trying source #${attempt.sourceIndex} mode=${attempt.mode} proxy=${attempt.proxy}")
                startAttempt(attempt)
                return true
            } catch (cause: Exception) {
                Log.w(TAG, "Could not prepare an attempt.", cause)
            }
        }
        return false
    }

    private fun startAttempt(attempt: Attempt) {
        val request = checkNotNull(activeRequest)
        val source = request.sources[attempt.sourceIndex]
        val videoUrl = withProxy(source.videoUrl, attempt.proxy)
        val audioUrl = source.audioUrl?.let { withProxy(it, attempt.proxy) }

        val extras = Bundle().apply { putString(EXTRA_CHANNEL_ID, request.channelId) }
        val metadata = MediaMetadata.Builder()
            .setTitle(request.channelName)
            .setDisplayTitle(request.channelName)
            .setSubtitle(source.quality)
            .setExtras(extras)
            .build()
        fun item(id: String, uri: Uri): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        val mediaSource: MediaSource = when (attempt.mode) {
            Mode.MASTER -> {
                val playlist = buildMasterPlaylist(source.videoUrl, videoUrl, checkNotNull(audioUrl))
                val dataUri = "data:application/vnd.apple.mpegurl;base64," +
                    Base64.encodeToString(playlist.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                hlsMediaSourceFactory.createMediaSource(
                    MediaItem.Builder()
                        .setMediaId(source.id)
                        .setUri(Uri.parse(dataUri))
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setMediaMetadata(metadata)
                        .build(),
                )
            }
            Mode.MERGE -> MergingMediaSource(
                /* adjustPeriodTimeOffsets= */ true,
                /* clipDurations= */ false,
                hlsMediaSourceFactory.createMediaSource(item(source.id, Uri.parse(videoUrl))),
                hlsMediaSourceFactory.createMediaSource(
                    item("${source.id}::audio", Uri.parse(checkNotNull(audioUrl))),
                ),
            )
            Mode.VIDEO_ONLY, Mode.PLAIN ->
                hlsMediaSourceFactory.createMediaSource(item(source.id, Uri.parse(videoUrl)))
        }

        player.stop()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    /** Same master playlist as mergedMaster() in V20: declares the audio URI as an audio group. */
    private fun buildMasterPlaylist(originalVideoUrl: String, videoUrl: String, audioUrl: String): String {
        val kbps = Regex("(\\d+)kbps").find(originalVideoUrl)?.groupValues?.get(1)?.toLongOrNull() ?: 1500L
        val bandwidth = kbps * 1000 + 192000
        return "#EXTM3U\n#EXT-X-VERSION:4\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Audio\",LANGUAGE=\"ar\"," +
            "DEFAULT=YES,AUTOSELECT=YES,URI=\"$audioUrl\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,AUDIO=\"aud\"\n$videoUrl\n"
    }

    private fun withProxy(url: String, useProxy: Boolean): String {
        val uri = validateStreamUrl(url)
        if (!useProxy || isProxyEndpoint(uri)) return url
        return "$HLS_PROXY_BASE/?url=${Uri.encode(url)}"
    }

    private fun isProxyEndpoint(uri: Uri): Boolean {
        val proxyUri = Uri.parse(HLS_PROXY_BASE)
        return uri.scheme.equals(proxyUri.scheme, ignoreCase = true) &&
            uri.host.equals(proxyUri.host, ignoreCase = true) &&
            uri.port == proxyUri.port
    }

    private fun validateStreamUrl(url: String): Uri {
        val uri = Uri.parse(url)
        require(uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)
        ) { "Stream URLs must use HTTP or HTTPS." }
        require(!uri.host.isNullOrBlank()) { "Stream URLs must include a host." }
        return uri
    }

    private data class PlaybackRequest(
        val channelId: String,
        val channelName: String,
        val selectedIndex: Int,
        val sources: List<PlaybackSource>,
    ) {
        companion object {
            fun fromBundle(bundle: Bundle): PlaybackRequest {
                val channelId = bundle.getString(EXTRA_CHANNEL_ID).orEmpty()
                val channelName = bundle.getString(EXTRA_CHANNEL_NAME).orEmpty()
                val selectedId = bundle.getString(EXTRA_SELECTED_STREAM_ID).orEmpty()
                val json = bundle.getString(EXTRA_STREAMS_JSON).orEmpty()
                require(channelId.isNotBlank() && channelName.isNotBlank()) {
                    "A channel id and name are required."
                }
                val sourceArray = JSONArray(json)
                require(sourceArray.length() > 0) { "At least one stream source is required." }
                val sources = List(sourceArray.length()) { index ->
                    val item = sourceArray.getJSONObject(index)
                    PlaybackSource(
                        id = item.getString("id"),
                        quality = item.getString("quality"),
                        videoUrl = item.getString("videoUrl"),
                        audioUrl = if (item.has("audioUrl") && !item.isNull("audioUrl")) {
                            item.optString("audioUrl").takeIf {
                                it.isNotBlank() && it != JSONObject.NULL.toString()
                            }
                        } else {
                            null
                        },
                        requiresProxy = item.optBoolean("requiresProxy", false),
                    ).also {
                        require(it.id.isNotBlank() && it.quality.isNotBlank()) {
                            "Every source needs an id and quality label."
                        }
                        validateRequestUrl(it.videoUrl)
                        it.audioUrl?.let(::validateRequestUrl)
                    }
                }
                val selectedIndex = sources.indexOfFirst { it.id == selectedId }
                require(selectedIndex >= 0) { "The selected quality is not in the source list." }
                return PlaybackRequest(channelId, channelName, selectedIndex, sources)
            }

            private fun validateRequestUrl(url: String) {
                val uri = Uri.parse(url)
                require(
                    (uri.scheme.equals("https", ignoreCase = true) ||
                        uri.scheme.equals("http", ignoreCase = true)) &&
                        !uri.host.isNullOrBlank(),
                ) { "Stream URLs must be absolute HTTP or HTTPS URLs." }
            }
        }
    }

    private data class PlaybackSource(
        val id: String,
        val quality: String,
        val videoUrl: String,
        val audioUrl: String?,
        val requiresProxy: Boolean,
    )

    companion object {
        private const val TAG = "SamiPlaybackService"
        private const val USER_AGENT = "SAMI Live TV"
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val MAX_ATTEMPTS = 12
        private const val SESSION_ACTIVITY_REQUEST_CODE = 4
        // Kept in sync with HLS_PROXY in the supplied source XML; not asserted operational.
        private const val HLS_PROXY_BASE =
            "https://bitter-brook-6557.alhydrys220.workers.dev"

        const val EXTRA_CHANNEL_ID = "com.sami.livetv.playback.CHANNEL_ID"
        private const val EXTRA_CHANNEL_NAME = "com.sami.livetv.playback.CHANNEL_NAME"
        private const val EXTRA_SELECTED_STREAM_ID =
            "com.sami.livetv.playback.SELECTED_STREAM_ID"
        private const val EXTRA_STREAMS_JSON = "com.sami.livetv.playback.STREAMS_JSON"
        private const val PLAY_STREAM_ACTION = "com.sami.livetv.playback.PLAY_STREAM"

        val PLAY_STREAM_COMMAND = SessionCommand(PLAY_STREAM_ACTION, Bundle.EMPTY)

        fun createPlayBundle(
            channelId: String,
            channelName: String,
            selectedStreamId: String,
            sources: List<StreamBundleSource>,
        ): Bundle {
            require(channelId.isNotBlank() && channelName.isNotBlank()) {
                "A channel id and name are required."
            }
            require(sources.any { it.id == selectedStreamId }) {
                "The selected stream must be one of the channel's available sources."
            }
            val sourcesJson = JSONArray().apply {
                sources.forEach { source ->
                    put(
                        JSONObject()
                            .put("id", source.id)
                            .put("quality", source.quality)
                            .put("videoUrl", source.videoUrl)
                            .put("audioUrl", source.audioUrl ?: JSONObject.NULL)
                            .put("requiresProxy", source.requiresProxy),
                    )
                }
            }
            return Bundle().apply {
                putString(EXTRA_CHANNEL_ID, channelId)
                putString(EXTRA_CHANNEL_NAME, channelName)
                putString(EXTRA_SELECTED_STREAM_ID, selectedStreamId)
                putString(EXTRA_STREAMS_JSON, sourcesJson.toString())
            }
        }
    }
}

data class StreamBundleSource(
    val id: String,
    val quality: String,
    val videoUrl: String,
    val audioUrl: String?,
    val requiresProxy: Boolean,
)
