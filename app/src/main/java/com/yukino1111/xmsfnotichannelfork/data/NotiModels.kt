package com.yukino1111.xmsfnotichannelfork.data

data class NotiRecord(
    val id: Long,
    val pkg: String,
    val channelId: String,
    val channelName: String,
    val channelGroup: String,
    val importance: Int,
    val title: String,
    val text: String,
    val subText: String,
    val postTime: Long
)

data class ChannelHistorySummary(
    val pkg: String,
    val channelId: String,
    val channelName: String,
    val channelGroup: String,
    val importance: Int,
    val count: Long,
    val lastPostTime: Long,
    val samples: List<NotiRecord>,
    val forceBlocked: Boolean = false
)

data class AppHistorySummary(
    val pkg: String,
    val appName: String,
    val channelCount: Int,
    val totalCount: Long,
    val lastPostTime: Long,
    val isIgnored: Boolean
)

data class ContentBlockRule(
    val id: Long,
    val pkg: String,
    val channelId: String,
    val pattern: String,
    val enabled: Boolean,
    val createdAt: Long = 0L
)
