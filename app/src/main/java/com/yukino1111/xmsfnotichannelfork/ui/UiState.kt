package com.yukino1111.xmsfnotichannelfork.ui

import android.graphics.drawable.Drawable
import com.yukino1111.xmsfnotichannelfork.data.AppHistorySummary
import com.yukino1111.xmsfnotichannelfork.data.ChannelHistorySummary
import com.yukino1111.xmsfnotichannelfork.data.ContentBlockRule
import com.yukino1111.xmsfnotichannelfork.data.NotiRecord

data class ChannelEntry(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val channelGroupName: String,
    val channelName: String,
    val channelId: String,
    val importance: Int,
    val isSystemApp: Boolean,
    val historyCount: Long,
    val latestPreview: String,
    val forceBlocked: Boolean,
)

data class TimelineEntry(
    val record: NotiRecord,
    val appName: String,
    val icon: Drawable?,
    val liveChannelName: String,
    val importance: Int,
    val forceBlocked: Boolean,
)

data class AppDetailState(
    val pkg: String,
    val appName: String,
    val icon: Drawable?,
    val isIgnored: Boolean,
    val channels: List<ChannelHistorySummary>,
    val contentRules: List<ContentBlockRule> = emptyList(),
    val loading: Boolean = false,
)

data class MainUiState(
    val hookActive: Boolean = false,
    val needsReboot: Boolean = false,
    val loadingChannels: Boolean = false,
    val loadingHistory: Boolean = false,
    val channels: List<ChannelEntry> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val appSummaries: List<AppHistorySummary> = emptyList(),
    val appIcons: Map<String, Drawable?> = emptyMap(),
    val forceBlockEnabled: Boolean = true,
    val recordOngoing: Boolean = false,
    val hideLauncherIcon: Boolean = false,
    val retentionDays: Int = 30,
    val statusMessage: String? = null,
    val appDetail: AppDetailState? = null,
)
