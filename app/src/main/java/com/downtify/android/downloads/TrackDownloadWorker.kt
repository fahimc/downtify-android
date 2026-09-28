package com.downtify.android.downloads

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.*
import com.downtify.android.ServerConfig
import com.downtify.android.data.AppDatabase
import com.downtify.android.data.OfflineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

class TrackDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("trackId") ?: return@withContext Result.failure()
        val config = ServerConfig(applicationContext)
        val directUrl = inputData.getString("directUrl").orEmpty()
        if (config.url.isBlank() || (directUrl.isBlank() && config.token.isBlank())) return@withContext Result.failure()
        val serverId = inputData.getString("serverId") ?: config.serverId
        val safeServer = serverId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(applicationContext.filesDir, "music/$safeServer").apply { mkdirs() }
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val part = File(dir, "$safe.part")
        val quality = config.quality
        val formatQuery = if (quality == "original") "" else "&format=${quality.substringBefore('/')}&bitrate=${quality.substringAfter('/', "160")}"
        val request = Request.Builder().url(directUrl.ifBlank { "${config.url}/api/v1/tracks/${id}/stream?download=true$formatQuery" })
            .apply { if (config.token.isNotBlank() && directUrl.isBlank()) header("Authorization", "Bearer ${config.token}") }
            .apply { inputData.getString("cookies")?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) } }
            .apply { inputData.getString("userAgent")?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) } }
            .apply { if (part.exists() && part.length() > 0) header("Range", "bytes=${part.length()}-") }.build()
        try {
            val response = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build().newCall(request).execute()
            response.use { res ->
                if (res.code == 401) return@withContext Result.failure()
                if (!res.isSuccessful) return@withContext if (runAttemptCount < 4) Result.retry() else Result.failure()
                val append = res.code == 206
                RandomAccessFile(part, "rw").use { output ->
                    if (!append) output.setLength(0) else output.seek(output.length())
                    res.body!!.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024); var n: Int
                        while (input.read(buf).also { n = it } >= 0) { if (isStopped) return@withContext Result.retry(); output.write(buf, 0, n) }
                    }
                }
                val file = File(dir, "$safe.audio"); if (!part.renameTo(file)) return@withContext Result.retry()
                val fallbackTitle = (inputData.getString("title") ?: id).substringBeforeLast('.')
                var title = fallbackTitle; var artist = inputData.getString("artist") ?: ""; var album = inputData.getString("album") ?: ""; var duration = inputData.getLong("duration", 0)
                runCatching {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(file.absolutePath)
                        title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
                        artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
                        album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: album
                        duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: duration
                    } finally { metadata.release() }
                }
                val db = AppDatabase.get(applicationContext).tracks()
                db.put(OfflineTrack(id, serverId, config.url, title, artist, album,
                    inputData.getString("artwork") ?: "", duration, file.absolutePath))
                Result.success()
            }
        } catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, id: String, serverId: String, title: String, artist: String, album: String, duration: Long) {
            val data = workDataOf("trackId" to id, "serverId" to serverId, "title" to title, "artist" to artist, "album" to album, "duration" to duration)
            val constraints = Constraints.Builder().setRequiredNetworkType(if (ServerConfig(context).wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            val work = OneTimeWorkRequestBuilder<TrackDownloadWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("download-$serverId-$id", ExistingWorkPolicy.KEEP, work)
        }
        fun enqueueDirect(context: Context, url: String, serverId: String, title: String, userAgent: String, cookies: String) {
            val id = "legacy-" + MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            val data = workDataOf("trackId" to id, "serverId" to serverId, "title" to title, "directUrl" to url, "userAgent" to userAgent, "cookies" to cookies)
            val constraints = Constraints.Builder().setRequiredNetworkType(if (ServerConfig(context).wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            val work = OneTimeWorkRequestBuilder<TrackDownloadWorker>().setInputData(data).setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("download-$serverId-$id", ExistingWorkPolicy.KEEP, work)
        }
    }
}
