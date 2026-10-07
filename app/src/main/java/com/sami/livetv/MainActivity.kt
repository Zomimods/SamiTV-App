package com.sami.livetv

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import android.app.PictureInPictureParams
import android.util.Rational
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.data.preferences.SettingsStore
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.domain.model.TvChannel
import com.sami.livetv.playback.PlaybackService
import com.sami.livetv.playback.StreamBundleSource
import com.sami.livetv.ui.localization.AppLanguageLayout
import com.sami.livetv.ui.theme.SamiTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var historyListener: Player.Listener? = null
    private var lastUsageEventKey: String? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val mainExecutor = Executor { command ->
        if (android.os.Looper.myLooper() == mainLooper) {
            command.run()
        } else {
            runOnUiThread(command)
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.i(TAG, "Notification permission was not granted; playback remains available.")
            }
        }

    private val vm: com.sami.livetv.ui.AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = vm.settings
        connectPlaybackController()

        setContent {
            val darkTheme by settingsStore.isDarkTheme.collectAsStateWithLifecycle(initialValue = true)
            val language by settingsStore.language.collectAsStateWithLifecycle(initialValue = AppLanguage.Arabic)
            vm.currentLang = language

            SamiTheme(darkTheme = darkTheme) {
                AppLanguageLayout(language = language) {
                    androidx.compose.runtime.CompositionLocalProvider(com.sami.livetv.ui.i18n.LocalLang provides language) {
                        com.sami.livetv.ui.SamiApp(
                            vm = vm,
                            controller = controllerState.value,
                            language = language,
                            onStream = { target, stream ->
                                startPlayback(target.channel, stream, if (language == AppLanguage.English) target.channel.name.english.ifBlank { target.channel.name.arabic } else target.channel.name.arabic, if (language == AppLanguage.English) "en" else "ar")
                            },
                            onPip = ::enterPictureInPicture,
                            onFullscreen = ::setFullscreen,
                            onToggleTheme = { lifecycleScope.launch { settingsStore.setDarkTheme(!darkTheme) } },
                            onToggleLang = {
                                lifecycleScope.launch {
                                    settingsStore.setLanguage(if (language == AppLanguage.Arabic) AppLanguage.English else AppLanguage.Arabic)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (on) { c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        mediaController?.let { controller ->
            historyListener?.let(controller::removeListener)
        }
        mediaController = null
        controllerState.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    private fun connectPlaybackController() {
        val token = SessionToken(
            applicationContext,
            ComponentName(applicationContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller == null || isFinishing || isDestroyed) {
                controller?.release()
                return@addListener
            }

            mediaController = controller
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        recordCurrentPlayback(controller)
                        recordPlaybackUsageIfNew(controller)
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    if (controller.isPlaying) {
                        recordCurrentPlayback(controller)
                        recordPlaybackUsageIfNew(controller)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_IDLE) {
                        lastUsageEventKey = null
                    }
                }
            }
            historyListener = listener
            controller.addListener(listener)
            controllerState.value = controller
            if (controller.isPlaying) {
                recordCurrentPlayback(controller)
                recordPlaybackUsageIfNew(controller)
            }
        }, mainExecutor)
    }

    private fun recordCurrentPlayback(controller: MediaController) {
        val channelId = controller.currentMediaMetadata.extras
            ?.getString(PlaybackService.EXTRA_CHANNEL_ID)
            .orEmpty()
        if (channelId.isBlank()) return
        lifecycleScope.launch {
            runCatching { settingsStore.recordWatch(channelId) }
                .onFailure { error ->
                    Log.w(TAG, "Could not save playback history.", error)
                }
        }
    }

    private fun recordPlaybackUsageIfNew(controller: MediaController) {
        val channelId = controller.currentMediaMetadata.extras
            ?.getString(PlaybackService.EXTRA_CHANNEL_ID)
            .orEmpty()
        val streamId = controller.currentMediaItem?.mediaId.orEmpty()
        if (channelId.isBlank() || streamId.isBlank()) return

        val eventKey = "$channelId:$streamId"
        if (eventKey == lastUsageEventKey) return
        lastUsageEventKey = eventKey
    }

    private fun startPlayback(
        channel: TvChannel,
        stream: StreamSource,
        channelName: String,
        languageTag: String,
    ) {
        val future = controllerFuture ?: return
        val sources = channel.streams.map { source ->
            StreamBundleSource(
                id = source.id,
                quality = source.quality.resolve(languageTag),
                videoUrl = source.url,
                audioUrl = source.audioUrl ?: channel.audioUrl,
                requiresProxy = source.requiresProxy || channel.requiresProxy,
            )
        }
        val args = PlaybackService.createPlayBundle(
            channelId = channel.id,
            channelName = channelName,
            selectedStreamId = stream.id,
            sources = sources,
        )
        future.addListener({
            runCatching {
                val controller = future.get()
                val commandFuture = controller.sendCustomCommand(
                    PlaybackService.PLAY_STREAM_COMMAND,
                    args,
                )
                commandFuture.addListener({
                    runCatching {
                        val result = commandFuture.get()
                        if (result.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            Log.w(TAG, "The playback service rejected the stream request.")
                        } else {
                            requestPlaybackNotificationsIfNeeded()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Playback could not be started.", error)
                    }
                }, mainExecutor)
            }.onFailure { error ->
                Log.w(TAG, "The playback service is unavailable.", error)
            }
        }, mainExecutor)
    }

    private fun requestPlaybackNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build(),
        )
    }

    private companion object {
        const val TAG = "SamiLiveTV"
    }
}
