package com.downtify.android.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.downtify.android.R
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

object PlaybackController {
    private var future: ListenableFuture<MediaController>? = null
    private fun controller(context: Context, action: (MediaController) -> Unit) {
        val f = future ?: MediaController.Builder(context, SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))).buildAsync().also { future = it }
        f.addListener({ runCatching { action(f.get()) } }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    fun play(context: Context, items: List<MediaItem>, startIndex: Int) = controller(context) { c ->
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare(); c.play()
    }
    fun pause(context: Context) = controller(context) { it.pause() }
    fun seek(context: Context, position: Long) = controller(context) { it.seekTo(position) }
}
