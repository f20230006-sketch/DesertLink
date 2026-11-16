package com.bitchat.android.desertlink

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.bitchat.android.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateOffer(val version: String, val url: String, val bytes: Long, val digest: String?)
data class UpdateState(val checking: Boolean = false, val downloading: Boolean = false, val progress: Int = 0,
    val message: String = "Internet is used only for optional app updates.", val offer: UpdateOffer? = null,
    val readyFile: File? = null, val automaticChecks: Boolean = true, val downloadOnWifi: Boolean = false)

object UpdateVerification {
    fun version(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
    fun newer(candidate: String, current: String): Boolean {
        val a = version(candidate) ?: return false
        val b = version(current) ?: return false
        return (0..2).firstOrNull { a[it] != b[it] }?.let { a[it] > b[it] } ?: false
    }
    fun permitted(currentPackage: String, candidatePackage: String, currentCode: Long, candidateCode: Long,
        currentSigners: Set<String>, candidateSigners: Set<String>) = currentPackage == candidatePackage &&
        candidateCode > currentCode && currentSigners.isNotEmpty() && currentSigners == candidateSigners
}

/** No incident data, peer identifiers, or location is ever sent to the update endpoint. */
class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("desertlink.updates", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(UpdateState(automaticChecks = prefs.getBoolean("automatic", true), downloadOnWifi = prefs.getBoolean("wifi", false)))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private val endpoint = "https://api.github.com/repos/f20230006-sketch/DesertLink/releases?per_page=20"
    init { scope.launch(Dispatchers.IO) {
        while (isActive) {
            if (mutable.value.automaticChecks && System.currentTimeMillis() - prefs.getLong("checked", 0) > 6 * 3_600_000L) check(false)
            delay(60_000)
        }
    } }
    private fun network(wifi: Boolean = false): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (!wifi || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    }.getOrDefault(false)
    private fun open(value: String): HttpURLConnection {
        var url = URL(value)
        repeat(6) {
            require(url.protocol == "https" && url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 20_000
                setRequestProperty("User-Agent", "DESERTLINK/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location") ?: error("Missing redirect")
                url = URL(url, next); connection.disconnect()
            } else { check(connection.responseCode == 200); return connection }
        }
        error("Too many redirects")
    }
    @Synchronized fun check(manual: Boolean = true) {
        if (work?.isActive == true) return
        work = scope.launch(Dispatchers.IO) {
            if (!network()) {
                if (manual) mutable.value = mutable.value.copy(message = "You’re offline. Bluetooth rescue messaging is unaffected.")
                return@launch
            }
            mutable.value = mutable.value.copy(checking = true, message = "Checking GitHub for a newer app…")
            var shouldDownload = false
            runCatching {
                val connection = open(endpoint)
                val content = try { connection.inputStream.use { input -> val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); while (bytes.size() <= 2_000_000) { val n = input.read(buffer); if (n < 0) break; bytes.write(buffer, 0, n) }; bytes.toByteArray() } } finally { connection.disconnect() }
                require(content.size <= 2_000_000)
                val releases = JsonParser.parseString(content.toString(Charsets.UTF_8)).asJsonArray
                val offers = releases.mapNotNull { item ->
                    val release = item.asJsonObject
                    val tag = release["tag_name"]?.asString ?: return@mapNotNull null
                    if (release["draft"]?.asBoolean == true || !UpdateVerification.newer(tag, BuildConfig.VERSION_NAME)) return@mapNotNull null
                    val asset = release["assets"]?.asJsonArray?.firstOrNull { it.asJsonObject["name"]?.asString == "DesertLink-debug.apk" }?.asJsonObject ?: return@mapNotNull null
                    val url = asset["browser_download_url"]?.asString ?: return@mapNotNull null
                    require(url.startsWith("https://github.com/f20230006-sketch/DesertLink/releases/download/"))
                    val digest = asset["digest"]?.takeUnless { it.isJsonNull }?.asString
                    UpdateOffer(tag, url, asset["size"]?.asLong ?: 0, digest)
                }
                val offer = offers.reduceOrNull { a, b -> if (UpdateVerification.newer(b.version, a.version)) b else a }
                prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                mutable.value = mutable.value.copy(offer = offer, message = if (offer == null) "You’re running the latest available version." else "${offer.version} is available. Installation always asks for your approval.")
                shouldDownload = offer != null && mutable.value.downloadOnWifi && network(true)
            }.onFailure { mutable.value = mutable.value.copy(message = "Update check could not finish. Rescue messaging continues over Bluetooth.") }
            mutable.value = mutable.value.copy(checking = false)
            if (shouldDownload) { work = null; download() }
        }
    }
    @Synchronized fun download() {
        if (work?.isActive == true) return
        val offer = mutable.value.offer ?: return
        work = scope.launch(Dispatchers.IO) {
            if (!network()) { mutable.value = mutable.value.copy(message = "Internet is needed to download this update. Your incidents remain available offline."); return@launch }
            mutable.value = mutable.value.copy(downloading = true, progress = 0, readyFile = null, message = "Downloading update. Bluetooth messaging continues.")
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            val partial = File(folder, "candidate.part.apk")
            try {
                val connection = open(offer.url)
                try {
                    require(offer.bytes in 1..100_000_000)
                    var total = 0L
                    connection.inputStream.use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count; require(total <= 100_000_000 && total <= offer.bytes)
                            output.write(buffer, 0, count)
                            mutable.value = mutable.value.copy(progress = (total * 100 / offer.bytes).toInt().coerceAtMost(99))
                        }
                    } }
                    require(total == offer.bytes)
                } finally { connection.disconnect() }
                if (offer.digest != null) {
                    require(offer.digest.startsWith("sha256:"))
                    val actual = partial.inputStream().use { input -> val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(32768); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }; digest.digest().joinToString("") { "%02x".format(it) } }
                    require(actual == offer.digest.removePrefix("sha256:"))
                }
                require(verifyApk(partial))
                val ready = File(folder, "verified-update.apk")
                if (ready.exists()) check(ready.delete())
                check(partial.renameTo(ready))
                mutable.value = mutable.value.copy(progress = 100, readyFile = ready, message = "Update verified against this app’s signing certificate. Tap Install when convenient.")
            } catch (e: CancellationException) {
                partial.delete(); mutable.value = mutable.value.copy(message = "Update download cancelled. Bluetooth messaging is unchanged."); throw e
            } catch (_: Exception) {
                partial.delete(); mutable.value = mutable.value.copy(message = "Update was not accepted. Package, version, size, or signature verification failed—or the connection dropped.")
            } finally { mutable.value = mutable.value.copy(downloading = false) }
        }
    }
    @Suppress("DEPRECATION") fun verifyApk(file: File): Boolean = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } }.toSet()
        }
        val installed = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
        val proposed = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        UpdateVerification.permitted(context.packageName, candidate.packageName, installed, proposed, signers(current), signers(candidate))
    }.getOrDefault(false)
    fun cancelDownload() { work?.cancel() }
    fun configure(automatic: Boolean, wifi: Boolean) {
        prefs.edit().putBoolean("automatic", automatic).putBoolean("wifi", wifi).apply()
        mutable.value = mutable.value.copy(automaticChecks = automatic, downloadOnWifi = wifi)
    }
}
