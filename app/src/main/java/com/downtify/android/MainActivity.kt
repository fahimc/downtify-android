package com.downtify.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.downtify.android.data.AppDatabase
import com.downtify.android.downloads.TrackDownloadWorker
import com.downtify.android.playback.PlaybackController
import com.downtify.android.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private data class ServerConnection(val id: String, val token: String, val name: String, val legacyWeb: Boolean = false)
    private lateinit var config: ServerConfig
    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var pendingServerUrl = ""
    private var pendingPairCode = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ServerConfig(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101010.toInt()) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10, 8, 10, 8) }
        status = TextView(this).apply { text = "Downtify"; setTextColor(-1); textSize = 18f; gravity = android.view.Gravity.CENTER_VERTICAL }
        bar.addView(status, LinearLayout.LayoutParams(0, 48, 1f))
        bar.addView(button("Offline") { showOffline() })
        bar.addView(button("Server") { showConnect() })
        root.addView(bar)
        val assetLoader = WebViewAssetLoader.Builder().addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(NativeBridge(this@MainActivity), "DowntifyNative")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val appAssets = uri.host == "appassets.androidplatform.net"
                    val server = runCatching { android.net.Uri.parse(config.url) }.getOrNull()
                    val sameServer = server != null && uri.scheme == server.scheme && uri.host == server.host && uri.port == server.port
                    return !(appAssets || sameServer)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && config.legacyWeb && request.url.host == android.net.Uri.parse(config.url).host) {
                        view.stopLoading()
                        view.loadUrl("https://appassets.androidplatform.net/index.html")
                        Toast.makeText(this@MainActivity, "Server unavailable. Showing bundled offline UI.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        if (config.url.isNotBlank()) loadWeb() else showConnect()
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
    private fun showConnect(error: String? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0) }
        val server = EditText(this).apply { hint = "https://music.example.com"; setSingleLine(); setText(config.url.ifBlank { pendingServerUrl }) }
        val code = EditText(this).apply { hint = "Pairing code (optional)"; setSingleLine(); setText(pendingPairCode) }
        box.addView(TextView(this).apply { text = "Downtify Server"; textSize = 20f }); box.addView(server); box.addView(code)
        AlertDialog.Builder(this).setTitle("Connect to Downtify").apply { if (!error.isNullOrBlank()) setMessage(error) }.setView(box)
            .setPositiveButton("Connect") { _, _ -> connect(server.text.toString(), code.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }
    private fun connect(raw: String, pairCode: String) {
        val normalized = raw.trim().trimEnd('/')
        pendingServerUrl = normalized
        pendingPairCode = pairCode
        val parsed = runCatching { URL(normalized) }.getOrNull()
        if (parsed == null || parsed.protocol !in listOf("https", "http") || (parsed.protocol == "http" && parsed.host !in listOf("localhost", "127.0.0.1", "10.0.2.2") && !parsed.host.startsWith("192.168."))) {
            showConnect("Enter the Downtify server's root HTTPS URL. HTTP is accepted for localhost and 192.168.x.x addresses."); return
        }
        status.text = "Connecting…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
                val infoRequest = Request.Builder().url("$normalized/api/server/info").header("Accept", "application/json").build()
                val infoResponse = client.newCall(infoRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(httpError("Server check", response.code, body, response.header("Content-Type")))
                    try { JSONObject(body) } catch (_: Exception) {
                        null
                    }
                }
                if (infoResponse == null) {
                    val versionRequest = Request.Builder().url("$normalized/api/version").header("Accept", "application/json").build()
                    val version = client.newCall(versionRequest).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) error(htmlError("Server check", body, response.header("Content-Type")))
                        body.trim().removeSurrounding("\"").trim().takeIf { it.isNotBlank() }
                            ?: error(htmlError("Server check", body, response.header("Content-Type")))
                    }
                    if (!Regex("^\\d+\\.\\d+\\.\\d+").containsMatchIn(version)) error(htmlError("Server version check", version, "application/json"))
                    if (isMobileApiVersion(version)) error("Downtify $version did not return its mobile server-info response. Check that /api/server/info is forwarded by the reverse proxy.")
                    if (pairCode.isNotBlank()) error("Downtify $version predates mobile pairing. Leave the pairing code blank to open its web interface, or update the server to 3.2.0 or newer.")
                    return@runCatching ServerConnection("legacy:$normalized", "", "Downtify $version", true)
                }
                val serverInfo = infoResponse
                require(serverInfo.optString("product") == "Downtify") { "This URL is not a Downtify server." }
                require(serverInfo.optInt("api_version") <= 1) { "This server API is newer than this app supports." }
                val serverId = serverInfo.optString("server_id")
                var token = if (normalized == config.url && config.serverId == serverId) config.token else ""
                if (pairCode.isNotBlank()) {
                    val body = JSONObject().put("code", pairCode).put("device_name", android.os.Build.MODEL).put("platform", "android")
                    val req = Request.Builder().url("$normalized/api/auth/pair").post(body.toString().toRequestBody("application/json".toMediaType())).build()
                    token = client.newCall(req).execute().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) error(httpError("Pairing", response.code, bodyText, response.header("Content-Type")))
                        try { JSONObject(bodyText).getString("token") } catch (_: Exception) { error(htmlError("Pairing", bodyText, response.header("Content-Type"))) }
                    }
                }
                ServerConnection(serverId, token, serverInfo.optString("name", "Downtify"))
            } }
            result.onSuccess { connection -> config.url = normalized; config.serverId = connection.id; config.token = connection.token; config.legacyWeb = connection.legacyWeb; pendingServerUrl = ""; pendingPairCode = ""; status.text = connection.name; loadWeb(); if (connection.legacyWeb) Toast.makeText(this@MainActivity,"Connected in web mode. Update Downtify to 3.2.0+ for native pairing and offline playback.",Toast.LENGTH_LONG).show() }
                .onFailure { status.text = "Downtify"; showConnect(it.message ?: "Couldn't reach this server. Check the address and network, then try again.") }
        }
    }
    private fun httpError(operation: String, code: Int, body: String, contentType: String?): String {
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrDefault("")
        if (contentType?.contains("text/html", ignoreCase = true) == true || body.trimStart().startsWith("<!doctype", true) || body.trimStart().startsWith("<html", true)) {
            return "$operation returned an HTML page (HTTP $code), not Downtify's API response. Check that you entered the server root URL and that any Cloudflare Access or reverse proxy allows /api/server/info."
        }
        return "$operation failed (HTTP $code)${if (detail.isNotBlank()) ": $detail" else ". Check the server URL and try again."}"
    }
    private fun htmlError(operation: String, body: String, contentType: String?): String {
        if (contentType?.contains("text/html", ignoreCase = true) == true || body.trimStart().startsWith("<!doctype", true) || body.trimStart().startsWith("<html", true)) {
            return "$operation received an HTML page instead of Downtify JSON. Check the server root URL and whether Cloudflare Access or a reverse proxy is intercepting /api requests."
        }
        return "$operation returned an invalid response. Confirm this is a compatible Downtify server."
    }
    private fun isMobileApiVersion(version: String): Boolean {
        val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(version) ?: return false
        val found = match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
        val required = listOf(3, 2, 0)
        for (i in required.indices) if (found[i] != required[i]) return found[i] > required[i]
        return true
    }
    private fun loadWeb() {
        status.text = if (config.legacyWeb) "Downtify · web mode" else "Downtify"
        if (config.legacyWeb) web.loadUrl(config.url) else web.loadUrl("https://appassets.androidplatform.net/index.html")
    }
    private fun showOffline() {
        lifecycleScope.launch {
            val tracks = AppDatabase.get(this@MainActivity).tracks().all()
            val labels = if (tracks.isEmpty()) arrayOf("No offline tracks yet") else tracks.map { "${it.title}  •  ${it.artist}" }.toTypedArray()
            AlertDialog.Builder(this@MainActivity).setTitle("Downloaded music (${tracks.size})")
                .setItems(labels) { _, index -> tracks.getOrNull(index)?.let { NativeBridge(this@MainActivity).play(JSONObject().put("id", it.trackId).put("title", it.title).put("artist", it.artist).put("album", it.album).toString()) } }
                .setNeutralButton("Settings") { _, _ -> downloadSettings() }.setPositiveButton("Done", null).show()
        }
    }
    private fun downloadSettings() {
        val options = arrayOf(if (config.wifiOnly) "☑ Wi-Fi only" else "☐ Wi-Fi only", "Offline quality: ${config.quality}", "Clear downloaded music")
        AlertDialog.Builder(this).setTitle("Downloads").setItems(options) { _, i -> when(i) {
            0 -> { config.wifiOnly = !config.wifiOnly; downloadSettings() }
            1 -> AlertDialog.Builder(this).setTitle("Offline quality").setItems(arrayOf("Original", "Opus 96", "Opus 160", "Opus 256")) { _, q -> config.quality = listOf("original", "opus/96", "opus/160", "opus/256")[q] }.show()
            2 -> AlertDialog.Builder(this).setMessage("Delete all downloaded tracks?").setPositiveButton("Clear") { _, _ -> lifecycleScope.launch { AppDatabase.get(this@MainActivity).tracks().all().forEach { java.io.File(it.localFilePath).delete(); AppDatabase.get(this@MainActivity).tracks().remove(it.serverId,it.trackId) }; Toast.makeText(this@MainActivity,"Downloads cleared",Toast.LENGTH_SHORT).show() } }.setNegativeButton("Cancel",null).show()
        } }.setPositiveButton("Done", null).show()
    }

    inner class NativeBridge(private val activity: MainActivity) {
        @JavascriptInterface fun getServerUrl() = config.url
        @JavascriptInterface fun getToken() = if (config.legacyWeb) "" else config.token
        @JavascriptInterface fun supportsNativePlayback() = !config.legacyWeb
        @JavascriptInterface fun setServerUrl(url: String) { runOnUiThread { showConnect() } }
        @JavascriptInterface fun downloadTrack(trackJson: String) {
            val t = runCatching { JSONObject(trackJson) }.getOrNull() ?: return
            val id = t.optString("id", t.optString("track_id")); if (id.isBlank()) { runOnUiThread { Toast.makeText(activity,"Track ID unavailable",Toast.LENGTH_SHORT).show() }; return }
            TrackDownloadWorker.enqueue(activity,id,config.serverId,t.optString("title",id),t.optString("artist"),t.optString("album"),t.optDouble("duration").toLong())
            runOnUiThread { Toast.makeText(activity,"Download queued",Toast.LENGTH_SHORT).show() }
        }
        @JavascriptInterface fun downloadAlbum(albumJson: String) = downloadCollection(albumJson)
        @JavascriptInterface fun downloadPlaylist(playlistJson: String) = downloadCollection(playlistJson)
        private fun downloadCollection(json: String) {
            val row = runCatching { JSONObject(json) }.getOrNull() ?: return
            val tracks = row.optJSONArray("tracks") ?: row.optJSONArray("track_ids")
            if (tracks == null) { runOnUiThread { Toast.makeText(activity,"No track list was supplied",Toast.LENGTH_SHORT).show() }; return }
            var count = 0
            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i) ?: JSONObject().put("id",tracks.optString(i))
                val id = track.optString("id").ifBlank { track.optString("track_id").ifBlank { resolveTrackId(track.optString("file")) } }
                if (id.isNotBlank()) { TrackDownloadWorker.enqueue(activity,id,config.serverId,track.optString("title",id),track.optString("artist"),track.optString("album"),track.optDouble("duration").toLong()); count++ }
            }
            runOnUiThread { Toast.makeText(activity,"$count downloads queued",Toast.LENGTH_SHORT).show() }
        }
        @JavascriptInterface fun resolveTrackId(file: String): String {
            if (config.legacyWeb) return ""
            return runCatching {
                val cached = trackIds[file]; if (cached != null) return cached
                val request = Request.Builder().url("${config.url}/api/v1/library?since=0").header("Authorization","Bearer ${config.token}").build()
                OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return ""
                    val rows = JSONObject(response.body!!.string()).optJSONArray("tracks") ?: return ""
                    for (i in 0 until rows.length()) { val item = rows.getJSONObject(i); val key = item.optString("file"); val value = item.optString("id"); if (key.isNotBlank() && value.isNotBlank()) trackIds[key] = value }
                }
                trackIds[file] ?: ""
            }.getOrDefault("")
        }
        @JavascriptInterface fun isDownloaded(trackId: String): Boolean = kotlinx.coroutines.runBlocking { AppDatabase.get(activity).tracks().find(config.serverId,trackId) != null }
        @JavascriptInterface fun getDownloadStatus(trackId: String): String = if (isDownloaded(trackId)) "downloaded" else "available"
        @JavascriptInterface fun play(trackJson: String) {
            val t = runCatching { JSONObject(trackJson) }.getOrNull() ?: return
            activity.lifecycleScope.launch {
                val tracks = t.optJSONArray("queue") ?: org.json.JSONArray().put(t)
                val items = ArrayList<androidx.media3.common.MediaItem>()
                for (i in 0 until tracks.length()) {
                    val row = tracks.optJSONObject(i) ?: continue
                    val file = row.optString("file")
                    val id = row.optString("id").ifBlank { row.optString("track_id").ifBlank { withContext(Dispatchers.IO) { resolveTrackId(file) } } }
                    if (id.isBlank()) continue
                    val local = AppDatabase.get(activity).tracks().find(config.serverId,id)?.takeIf { java.io.File(it.localFilePath).exists() }
                    val quality = config.quality
                    val suffix = if (quality == "original") "" else "?format=${quality.substringBefore('/')}&bitrate=${quality.substringAfter('/', "160")}"
                    val uri = local?.localFilePath ?: "${config.url}/api/v1/tracks/$id/stream$suffix"
                    val metadata = androidx.media3.common.MediaMetadata.Builder().setTitle(row.optString("title",id)).setArtist(row.optString("artist")).setAlbumTitle(row.optString("album")).build()
                    items.add(androidx.media3.common.MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build())
                }
                if (items.isEmpty()) { runOnUiThread { Toast.makeText(activity,"Track is not in the synced library",Toast.LENGTH_SHORT).show() }; return@launch }
                PlaybackController.play(activity,items,t.optInt("startIndex",0))
            }
        }
        @JavascriptInterface fun pause() = PlaybackController.pause(activity)
        @JavascriptInterface fun seek(position: Long) = PlaybackController.seek(activity,position)
        @JavascriptInterface fun next() { web.evaluateJavascript("window.DowntifyAndroidNext && window.DowntifyAndroidNext()", null) }
        @JavascriptInterface fun previous() { web.evaluateJavascript("window.DowntifyAndroidPrevious && window.DowntifyAndroidPrevious()", null) }
    }

    companion object { val trackIds = java.util.concurrent.ConcurrentHashMap<String,String>() }
}
