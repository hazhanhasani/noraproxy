package com.v2ray.ang.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

data class NoraUpdateState(
    val busy: Boolean = false,
    val progress: Int = 0,
    val available: Boolean = false,
    val downloaded: Boolean = false,
    val version: String = "",
    val notes: String = "",
    val message: String = "بررسی نسخه جدید"
)

/** Full download stays in NoraProxy. Only the final OS installation requires consent. */
class NoraUpdater(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val data = MutableStateFlow(NoraUpdateState())
    val state = data.asStateFlow()
    private data class Release(val version: String, val notes: String, val url: String, val sha: String, val size: Long)
    private var candidate: Release? = null
    private var downloaded: File? = null

    fun dispose() = scope.cancel()

    fun check() {
        if (data.value.busy) return
        scope.launch {
            data.value = NoraUpdateState(busy = true, message = "بررسی نسخه جدید...")
            try {
                val remote = withContext(Dispatchers.IO) { fetchRelease() }
                candidate = if (isNewer(remote.version)) remote else null
                downloaded = null
                data.value = NoraUpdateState(
                    available = candidate != null, version = candidate?.version.orEmpty(),
                    notes = candidate?.notes.orEmpty(),
                    message = if (candidate != null) "نسخه جدید برای دانلود آماده است."
                    else "NoraProxy به‌روز است."
                )
            } catch (e: Exception) {
                data.value = NoraUpdateState(message = "بررسی ناموفق: " + (e.message ?: "خطا").take(95))
            }
        }
    }

    fun download() {
        val release = candidate ?: return
        if (data.value.busy) return
        scope.launch {
            data.value = data.value.copy(busy = true, progress = 0, message = "در حال دانلود درون برنامه...")
            try {
                downloaded = withContext(Dispatchers.IO) { downloadAndVerify(release) }
                data.value = data.value.copy(busy = false, downloaded = true, progress = 100,
                    message = "APK و امضای آن تأیید شد. آماده نصب.")
            } catch (e: Exception) {
                downloaded = null
                data.value = data.value.copy(busy = false, downloaded = false,
                    message = "دانلود یا اعتبارسنجی ناموفق: " + (e.message ?: "خطا").take(85))
            }
        }
    }

    fun install() {
        val file = downloaded?.takeIf { it.isFile } ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26 &&
                !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                data.value = data.value.copy(message =
                    "اجازه نصب به NoraProxy بدهید؛ سپس دوباره دکمه نصب را لمس کنید.")
                return
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            data.value = data.value.copy(message = "باز کردن نصب‌کننده ممکن نشد: " +
                (e.message ?: "خطا").take(75))
        }
    }

    private fun fetchRelease(): Release {
        val response = JSONObject(readLimited(
            "https://api.github.com/repos/hazhanhasani/noraproxy/releases/latest", 524288
        ).toString(Charsets.UTF_8))
        require(!response.optBoolean("draft") && !response.optBoolean("prerelease"))
        val version = response.getString("tag_name").removePrefix("v")
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "نسخه نامعتبر است." }
        val assets = response.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull {
                it.optString("name").startsWith("NoraProxy-") &&
                it.optString("name").endsWith(".apk") &&
                !it.optString("name").contains("debug", ignoreCase = true)
            } ?: error("APK امضاشده NoraProxy هنوز منتشر نشده است.")
        // Some GitHub API responses omit asset.digest. In that case use our
        // separately published, matching .sha256 asset. Never bypass integrity checks.
        val expectedApkName = asset.getString("name")
        var sha = asset.optString("digest").removePrefix("sha256:").lowercase()
        if (!sha.matches(Regex("[a-f0-9]{64}"))) {
            val checksum = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == expectedApkName + ".sha256" }
                ?: error("فایل SHA-256 نسخه منتشر نشده است.")
            val checksumUrl = checksum.getString("browser_download_url")
            validateUrl(checksumUrl)
            val manifest = readLimited(checksumUrl, 4096)
                .toString(Charsets.UTF_8).trim().split(Regex("\\s+"))
            require(manifest.size >= 2 &&
                manifest[1].removePrefix("*") == expectedApkName) {
                "فایل SHA-256 به نسخه APK تعلق ندارد."
            }
            sha = manifest[0].lowercase()
        }
        require(sha.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 رسمی معتبر نیست." }
        val size = asset.getLong("size")
        require(size in 1_000_000L..MAX_APK) { "اندازه دانلود غیرمجاز است." }
        val url = asset.getString("browser_download_url")
        validateUrl(url)
        return Release(version, response.optString("body").take(2500), url, sha, size)
    }

    private fun isNewer(remote: String): Boolean {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            .orEmpty().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val next = remote.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val a = installed.getOrElse(i) { 0 }
            val b = next.getOrElse(i) { 0 }
            if (a != b) return b > a
        }
        return false
    }

    private fun downloadAndVerify(release: Release): File {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val part = File(dir, "nora-upgrade.part.apk")
        val target = File(dir, "nora-upgrade.apk")
        part.delete()
        target.delete()
        try {
            val conn = open(release.url)
            try {
                require(conn.responseCode == 200) { "خطای شبکه" }
                val length = conn.contentLengthLong
                require(length < 0 || length <= MAX_APK) { "فایل بیش از حد بزرگ است." }
                val hash = MessageDigest.getInstance("SHA-256")
                var total = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            total += n
                            require(total <= MAX_APK) { "دانلود بیش از حد بزرگ است." }
                            hash.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                            data.value = data.value.copy(progress =
                                (total * 100 / release.size).toInt().coerceIn(0, 99))
                        }
                        output.fd.sync()
                    }
                }
                require(total == release.size) { "دانلود ناقص است." }
                val actual = hash.digest().joinToString("") { "%02x".format(it) }
                require(actual == release.sha) { "هش فایل یکسان نیست." }
                verifyPackage(part)
                require(part.renameTo(target)) { "خطا در ذخیره APK" }
                return target
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            part.delete()
            target.delete()
            throw e
        }
    }

    private fun verifyPackage(file: File) {
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val apk = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) {
            "فایل APK قابل شناسایی نیست."
        }
        @Suppress("DEPRECATION")
        val own = manager.getPackageInfo(context.packageName, flags)
        require(apk.packageName == own.packageName) { "پکیج اشتباه است." }
        @Suppress("DEPRECATION")
        val oldCode = if (Build.VERSION.SDK_INT >= 28) own.longVersionCode else own.versionCode.toLong()
        @Suppress("DEPRECATION")
        val newCode = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        require(newCode > oldCode) { "نسخه جدیدتر نیست." }
        @Suppress("DEPRECATION")
        fun certs(pkg: android.content.pm.PackageInfo): Set<String> {
            val list = if (Build.VERSION.SDK_INT >= 28)
                pkg.signingInfo?.apkContentsSigners else pkg.signatures
            return requireNotNull(list) { "امضای APK پیدا نشد." }
                .map { sig ->
                    MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }.toSet()
        }
        require(certs(own) == certs(apk)) { "امضای برنامه تغییر کرده است." }
    }

    private fun readLimited(url: String, limit: Int): ByteArray {
        val conn = open(url)
        try {
            if (conn.responseCode == 404) {
                error("هنوز نسخه رسمی در GitHub Releases منتشر نشده است.")
            }
            require(conn.responseCode == 200) {
                "ارتباط با سرور به‌روزرسانی برقرار نشد (HTTP " + conn.responseCode + ")."
            }
            conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    output.write(buffer, 0, n)
                    require(output.size() <= limit) { "پاسخ بیش از حد بزرگ است." }
                }
                return output.toByteArray()
            }
        } finally { conn.disconnect() }
    }

    private fun open(raw: String): HttpURLConnection {
        var next = raw
        repeat(6) {
            validateUrl(next)
            val conn = (URL(next).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 45000
                setRequestProperty("User-Agent", "NoraProxy-Android")
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            }
            if (conn.responseCode in 300..399) {
                val redirect = conn.getHeaderField("Location")
                    ?: error("آدرس دانلود معتبر نیست.")
                conn.disconnect()
                next = URI(next).resolve(redirect).toString()
            } else return conn
        }
        error("تغییرمسیر بیش از حد است.")
    }

    private fun validateUrl(raw: String) {
        val uri = URI(raw)
        require(uri.scheme == "https" && uri.userInfo == null) {
            "لینک HTTPS معتبر نیست."
        }
        require(uri.host.orEmpty().lowercase() in setOf(
            "api.github.com", "github.com",
            "release-assets.githubusercontent.com", "objects.githubusercontent.com"
        )) { "دامنه دانلود مجاز نیست." }
    }

    private companion object {
        const val MAX_APK = 200L * 1024L * 1024L
    }
}
