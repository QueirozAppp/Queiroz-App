package com.example.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import kotlinx.coroutines.tasks.await

class AppUpdateManager(
    context: Context,
    private val currentBuild: Int = AppUpdateConfig.APP_BUILD,
    private val versionUrl: String = AppUpdateConfig.VERSION_JSON_URL,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val fetcher: (suspend (String) -> UpdateCheckOutcome)? = null
) {
    private val appContext = context.applicationContext ?: context

    companion object {
        private const val PREFS_NAME = "qrz_app_update_prefs"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_DISMISSED_BUILD = "dismissed_build"
        private const val KEY_CACHED_VERSION = "cached_version"
        private const val KEY_CACHED_BUILD = "cached_build"
        private const val KEY_CACHED_NOTES = "cached_notes"
        private const val KEY_CACHED_URL = "cached_url"
    }

    private fun getUserId(): String {
        return try {
            com.example.core.firebase.FirebaseProvider.getAuthOrNull()?.currentUser?.uid ?: "guest"
        } catch (_: Exception) {
            "guest"
        }
    }

    private fun readPrefs(): android.content.SharedPreferences? {
        return try {
            val uid = getUserId()
            appContext.getSharedPreferences("${PREFS_NAME}_$uid", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    fun getLastCheckTimestamp(): Long {
        return try {
            readPrefs()?.getLong(KEY_LAST_CHECK_AT, 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun saveLastCheckTimestamp(timestamp: Long) {
        try {
            readPrefs()?.edit()?.putLong(KEY_LAST_CHECK_AT, timestamp)?.apply()
        } catch (_: Exception) {}
    }

    fun getDismissedBuild(): Int {
        return try {
            readPrefs()?.getInt(KEY_DISMISSED_BUILD, -1) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    fun dismissVersion(build: Int) {
        try {
            readPrefs()?.edit()?.putInt(KEY_DISMISSED_BUILD, build)?.apply()
        } catch (_: Exception) {}
    }

    fun getCachedUpdate(): RemoteVersionInfo? {
        return try {
            val prefs = readPrefs() ?: return null
            val cachedBuild = prefs.getInt(KEY_CACHED_BUILD, -1)
            val cachedVersion = prefs.getString(KEY_CACHED_VERSION, null) ?: return null
            if (cachedBuild <= currentBuild) {
                clearCachedUpdate()
                return null
            }
            RemoteVersionInfo(
                version = cachedVersion,
                build = cachedBuild,
                notes = prefs.getString(KEY_CACHED_NOTES, "") ?: "",
                url = prefs.getString(KEY_CACHED_URL, AppUpdateConfig.FALLBACK_DOWNLOAD_URL)
                    ?: AppUpdateConfig.FALLBACK_DOWNLOAD_URL
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCachedUpdate(info: RemoteVersionInfo) {
        try {
            readPrefs()?.edit()
                ?.putString(KEY_CACHED_VERSION, info.version)
                ?.putInt(KEY_CACHED_BUILD, info.build)
                ?.putString(KEY_CACHED_NOTES, info.notes)
                ?.putString(KEY_CACHED_URL, info.url)
                ?.apply()
        } catch (_: Exception) {}
    }

    private fun clearCachedUpdate() {
        try {
            readPrefs()?.edit()
                ?.remove(KEY_CACHED_VERSION)
                ?.remove(KEY_CACHED_BUILD)
                ?.remove(KEY_CACHED_NOTES)
                ?.remove(KEY_CACHED_URL)
                ?.apply()
        } catch (_: Exception) {}
    }

    fun shouldDisplayBanner(info: RemoteVersionInfo, forceShow: Boolean = false): Boolean {
        if (info.build <= currentBuild) return false
        if (forceShow) return true
        return getDismissedBuild() != info.build
    }

    suspend fun fetchRemoteVersionFromFirestore(): RemoteVersionInfo? = withContext(Dispatchers.IO) {
        try {
            val db = com.example.core.firebase.FirebaseProvider.getFirestoreOrNull() ?: return@withContext null
            val doc = db.collection("app_updates").document("latest").get().await()
            if (doc.exists()) {
                val data = doc.data ?: return@withContext null
                val version = (data["version"] as? String) ?: ""
                val build = when (val b = data["build"]) {
                    is Number -> b.toInt()
                    is String -> b.toIntOrNull() ?: 0
                    else -> 0
                }
                val notes = (data["notes"] as? String) ?: ""
                val url = (data["url"] as? String) ?: AppUpdateConfig.FALLBACK_DOWNLOAD_URL
                if (version.isNotBlank() && build > 0) {
                    return@withContext RemoteVersionInfo(
                        version = version,
                        build = build,
                        notes = notes,
                        url = url.ifBlank { AppUpdateConfig.FALLBACK_DOWNLOAD_URL }
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun publishVersionToFirestore(info: RemoteVersionInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = com.example.core.firebase.FirebaseProvider.getFirestoreOrNull() ?: return@withContext false
            val map = hashMapOf(
                "version" to info.version,
                "build" to info.build,
                "notes" to info.notes,
                "url" to info.url,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("app_updates").document("latest").set(map).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckOutcome {
        val now = nowProvider()
        if (!force) {
            val lastCheck = getLastCheckTimestamp()
            if (lastCheck > 0L && (now - lastCheck) < AppUpdateConfig.AUTO_CHECK_INTERVAL_MS) {
                val cached = getCachedUpdate()
                return if (cached != null) {
                    UpdateCheckOutcome.UpdateAvailable(cached)
                } else {
                    UpdateCheckOutcome.SkippedInterval
                }
            }
        }

        val outcome = try {
            withTimeout(AppUpdateConfig.REQUEST_TIMEOUT_MS.toLong()) {
                if (fetcher != null) {
                    fetcher.invoke(versionUrl)
                } else {
                    // 1. Verificar Firestore primeiro (funciona independente do GitHub ser público ou privado)
                    val firestoreInfo = fetchRemoteVersionFromFirestore()
                    if (firestoreInfo != null && firestoreInfo.build > currentBuild) {
                        UpdateCheckOutcome.UpdateAvailable(firestoreInfo)
                    } else {
                        // 2. Verificar HTTP (version.json no GitHub)
                        var netOutcome = fetchRemoteVersionFromNetwork(versionUrl, now)
                        if (netOutcome is UpdateCheckOutcome.HttpNotFound && versionUrl.contains("/main/")) {
                            val masterUrl = versionUrl.replace("/main/", "/master/")
                            netOutcome = fetchRemoteVersionFromNetwork(masterUrl, now)
                        }

                        // 3. Fallback para GitHub Releases API caso version.json não esteja atualizado/público
                        if (netOutcome !is UpdateCheckOutcome.UpdateAvailable) {
                            val releaseOutcome = fetchLatestGitHubRelease(now)
                            if (releaseOutcome is UpdateCheckOutcome.UpdateAvailable) {
                                netOutcome = releaseOutcome
                            } else if (netOutcome is UpdateCheckOutcome.HttpNotFound && releaseOutcome is UpdateCheckOutcome.UpToDate) {
                                netOutcome = releaseOutcome
                            }
                        }

                        if (netOutcome is UpdateCheckOutcome.UpdateAvailable) {
                            netOutcome
                        } else if (firestoreInfo != null) {
                            // Se Firestore já está configurado e não há build superior no GitHub, respeita status do Firestore
                            if (firestoreInfo.build > currentBuild) {
                                UpdateCheckOutcome.UpdateAvailable(firestoreInfo)
                            } else {
                                UpdateCheckOutcome.UpToDate(firestoreInfo)
                            }
                        } else {
                            netOutcome
                        }
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            UpdateCheckOutcome.Timeout
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            classifyException(e)
        }

        if (outcome is UpdateCheckOutcome.UpdateAvailable) {
            saveLastCheckTimestamp(now)
            saveCachedUpdate(outcome.info)
        } else if (outcome is UpdateCheckOutcome.UpToDate) {
            saveLastCheckTimestamp(now)
            clearCachedUpdate()
        }

        return outcome
    }

    private suspend fun fetchLatestGitHubRelease(timestamp: Long): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val apiUrl = "https://api.github.com/repos/QueirozAppp/Queiroz-App/releases/latest?_ts=$timestamp"
            connection = (URL(apiUrl).openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "GET"
                connectTimeout = AppUpdateConfig.REQUEST_TIMEOUT_MS
                readTimeout = AppUpdateConfig.REQUEST_TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", "QRZ-Stock-Android-App")
                setRequestProperty("Accept", "application/vnd.github+json")
            } ?: return@withContext UpdateCheckOutcome.Offline

            val code = connection.responseCode
            if (code == 404) return@withContext UpdateCheckOutcome.HttpNotFound(apiUrl)
            if (code !in 200..299) return@withContext UpdateCheckOutcome.HttpError(code)

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val obj = JSONObject(body)
            val tagName = obj.optString("tag_name", "").trim()
            val releaseName = obj.optString("name", "").trim()
            val notes = obj.optString("body", "").trim().take(300)
            val assets = obj.optJSONArray("assets")
            var downloadUrl = AppUpdateConfig.FALLBACK_DOWNLOAD_URL
            if (assets != null && assets.length() > 0) {
                val apkAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                val assetUrl = apkAsset?.optString("browser_download_url", "")?.trim()
                if (!assetUrl.isNullOrBlank()) downloadUrl = assetUrl
            }

            // Extrai o número de build de "(Build 13)", "build: 13" ou sufixo numérico da tag
            val buildRegex = Regex("""build[:\s#]*([0-9]+)""", RegexOption.IGNORE_CASE)
            val parsedBuild = buildRegex.find(releaseName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: buildRegex.find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: tagName.removePrefix("v").removePrefix("V")
                    .split('.', '-', '_')
                    .lastOrNull()
                    ?.toIntOrNull()
                ?: 0

            val cleanVersion = tagName.removePrefix("v").removePrefix("V").ifBlank { releaseName }
            if (cleanVersion.isBlank() || parsedBuild <= 0) {
                return@withContext UpdateCheckOutcome.InvalidJson
            }

            val remoteInfo = RemoteVersionInfo(
                version = cleanVersion,
                build = parsedBuild,
                notes = notes.ifBlank { releaseName },
                url = downloadUrl
            )
            if (remoteInfo.build > currentBuild) {
                UpdateCheckOutcome.UpdateAvailable(remoteInfo)
            } else {
                UpdateCheckOutcome.UpToDate(remoteInfo)
            }
        } catch (_: Exception) {
            UpdateCheckOutcome.Offline
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun classifyException(e: Exception): UpdateCheckOutcome {
        return when (e) {
            is SocketTimeoutException -> UpdateCheckOutcome.Timeout
            is UnknownHostException, is ConnectException -> UpdateCheckOutcome.Offline
            else -> {
                val msg = e.message?.lowercase() ?: ""
                if (msg.contains("timeout") || msg.contains("timed out")) {
                    UpdateCheckOutcome.Timeout
                } else if (msg.contains("host") || msg.contains("connection") || msg.contains("network") || msg.contains("unreachable")) {
                    UpdateCheckOutcome.Offline
                } else {
                    UpdateCheckOutcome.Offline
                }
            }
        }
    }

    private suspend fun fetchRemoteVersionFromNetwork(
        baseUrl: String,
        timestamp: Long
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val separator = if (baseUrl.contains("?")) "&" else "?"
            val noCacheUrl = "${baseUrl}${separator}_ts=$timestamp"
            connection = (URL(noCacheUrl).openConnection() as? HttpURLConnection)?.apply {
                requestMethod = "GET"
                connectTimeout = AppUpdateConfig.REQUEST_TIMEOUT_MS
                readTimeout = AppUpdateConfig.REQUEST_TIMEOUT_MS
                useCaches = false
                defaultUseCaches = false
                setRequestProperty("User-Agent", "QRZ-Stock-Android-App")
                setRequestProperty("Cache-Control", "no-store, no-cache, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
            } ?: return@withContext UpdateCheckOutcome.Offline

            val responseCode = try {
                connection.responseCode
            } catch (e: Exception) {
                return@withContext classifyException(e)
            }

            if (responseCode == 404) {
                return@withContext UpdateCheckOutcome.HttpNotFound(noCacheUrl)
            }
            if (responseCode !in 200..299) {
                return@withContext UpdateCheckOutcome.HttpError(responseCode)
            }

            val contentType = connection.contentType?.lowercase() ?: ""
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            val trimmed = body.trim().lowercase()
            if (contentType.contains("text/html") || trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
                return@withContext UpdateCheckOutcome.InvalidJson
            }

            val remoteInfo = parseVersionJson(body)
            if (remoteInfo == null) {
                UpdateCheckOutcome.InvalidJson
            } else {
                if (remoteInfo.build > currentBuild) {
                    UpdateCheckOutcome.UpdateAvailable(remoteInfo)
                } else {
                    UpdateCheckOutcome.UpToDate(remoteInfo)
                }
            }
        } catch (e: SocketTimeoutException) {
            UpdateCheckOutcome.Timeout
        } catch (e: UnknownHostException) {
            UpdateCheckOutcome.Offline
        } catch (e: ConnectException) {
            UpdateCheckOutcome.Offline
        } catch (e: Exception) {
            classifyException(e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    internal fun parseVersionJson(rawJson: String): RemoteVersionInfo? {
        return try {
            val obj = JSONObject(rawJson)
            val version = obj.optString("version", "").trim()
            val build = obj.optInt("build", -1)
            val notes = obj.optString("notes", "").trim()
            val url = obj.optString("url", "").trim()
                .ifBlank { AppUpdateConfig.FALLBACK_DOWNLOAD_URL }

            if (version.isBlank() || build <= 0) {
                null
            } else {
                RemoteVersionInfo(
                    version = version,
                    build = build,
                    notes = notes,
                    url = url
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openUpdateUrl(context: Context, url: String): Boolean {
        return try {
            val targetUrl = url.trim().ifBlank { AppUpdateConfig.FALLBACK_DOWNLOAD_URL }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
