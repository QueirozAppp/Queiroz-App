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

    private fun readPrefs(): android.content.SharedPreferences? {
        return try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                    fetchRemoteVersionFromNetwork(versionUrl, now)
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
