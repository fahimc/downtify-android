package com.downtify.android.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.downtify.android.ServerConfig

class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${ServerConfig(this).token}"))
        val data = DefaultDataSource.Factory(this, http)
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(data)).build().apply { setAudioAttributes(audio, true); setHandleAudioBecomingNoisy(true) }
        session = MediaSession.Builder(this, player!!).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() { session?.release(); player?.release(); session = null; player = null; super.onDestroy() }
}
