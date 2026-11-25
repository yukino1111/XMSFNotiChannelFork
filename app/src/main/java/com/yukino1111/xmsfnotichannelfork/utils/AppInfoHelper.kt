package com.yukino1111.xmsfnotichannelfork.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.yukino1111.xmsfnotichannelfork.data.NotiDatabaseHelper

class AppInfoHelper(private val context: Context) {
    private var allChannelsCache: List<MyAppInfo>? = null
    var pattern: String = ""

    data class MyAppInfo(
        val appName: String,
        val packageName: String,
        val icon: Drawable,
        var ncInfo: NCInfo,
        val isSystemApp: Boolean,
        var historyCount: Long = 0L,
        var latestPreview: String = "",
        var forceBlocked: Boolean = false
    )

    data class NCInfo(
        val channelGroupName: String,
        val channelName: String,
        val channelId: String,
        var importance: Int
    )

    fun getAllChannels(forceRefresh: Boolean = false): List<MyAppInfo> {
        if (!forceRefresh) {
            allChannelsCache?.let { return it }
        }
        return loadAllChannelsFromSystem()
    }

    fun getAppInfoList(): MutableList<MyAppInfo> =
        filterByPattern(getAllChannels(false), pattern).toMutableList()

    fun getAppInfoListForNew(): MutableList<MyAppInfo> =
        filterByPattern(loadAllChannelsFromSystem(), pattern).toMutableList()

    private fun loadAllChannelsFromSystem(): List<MyAppInfo> {
        val pm = context.packageManager
        val ncUtils = NCUtils(context)
        val db = NotiDatabaseHelper.getInstance(context)
        val statsMap = runCatching { db.getChannelQuickStats() }.getOrDefault(emptyMap())
        val forceBlockedKeys = runCatching { db.getForceBlockedKeys() }.getOrDefault(emptySet())

        val result = mutableListOf<MyAppInfo>()
        for (appInfo in pm.getInstalledApplications(0)) {
            val channels = ncUtils.getAllChannels(appInfo.packageName)
            if (channels.isEmpty()) continue
            val label = appInfo.loadLabel(pm).toString()
            val icon = appInfo.loadIcon(pm)
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            for (ch in channels) {
                val stat = statsMap[appInfo.packageName to ch.channelId]
                result.add(
                    MyAppInfo(
                        appName = label,
                        packageName = appInfo.packageName,
                        icon = icon,
                        ncInfo = ch,
                        isSystemApp = isSys,
                        historyCount = stat?.first ?: 0L,
                        latestPreview = stat?.second.orEmpty(),
                        forceBlocked = forceBlockedKeys.contains("${appInfo.packageName}|${ch.channelId}")
                    )
                )
            }
        }
        val sorted = result.sortedWith(
            compareByDescending<MyAppInfo> { it.historyCount > 0 }
                .thenByDescending { it.ncInfo.importance > 0 }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.ncInfo.channelName.lowercase() }
        )
        allChannelsCache = sorted
        return sorted
    }

    fun filterByPattern(source: List<MyAppInfo>, rawPattern: String): List<MyAppInfo> {
        if (rawPattern.isBlank()) return source
        val regex = runCatching { Regex(rawPattern.trim(), RegexOption.IGNORE_CASE) }
            .getOrElse { Regex(Regex.escape(rawPattern.trim()), RegexOption.IGNORE_CASE) }
        return source.filter { item ->
            regex.containsMatchIn(item.appName) ||
                regex.containsMatchIn(item.packageName) ||
                regex.containsMatchIn(item.ncInfo.channelGroupName) ||
                regex.containsMatchIn(item.ncInfo.channelName) ||
                regex.containsMatchIn(item.ncInfo.channelId) ||
                (item.latestPreview.isNotBlank() && regex.containsMatchIn(item.latestPreview))
        }
    }
}
