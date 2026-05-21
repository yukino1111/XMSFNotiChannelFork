package com.yukino1111.xmsfnotichannelfork.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.IBinder
import java.lang.reflect.Method

@SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
class NCUtils(private val context : Context) {

    private val sINM by lazy {
        val notificationService = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService",
            String::class.java
        ).invoke(null, "notification")

        Class.forName("android.app.INotificationManager\$Stub").getDeclaredMethod("asInterface", IBinder::class.java)
            .invoke(null, notificationService)
    }

    private val notificationChannelGroupsM = sINM.javaClass.getDeclaredMethod(
        "getNotificationChannelGroupsForPackage",
        String::class.java,
        Int::class.java,
        Boolean::class.java) as Method

    private val updateNotificationChannelForPackageM = sINM.javaClass.getDeclaredMethod(
        "updateNotificationChannelForPackage",
        String::class.java,
        Int::class.java,
        NotificationChannel::class.java) as Method

    private fun getNotificationChannelGroups(pkgName: String) =
        notificationChannelGroupsM.invoke(sINM, pkgName, context.packageManager.getPackageUid(pkgName, 0), false).let {
            Class.forName("android.content.pm.ParceledListSlice").getDeclaredMethod("getList")
                .invoke(it) as List<NotificationChannelGroup>
        }

    private fun setNotificationChannel(pkgName: String, channel: NotificationChannel): Any? =
        updateNotificationChannelForPackageM.invoke(
            sINM, pkgName, context.packageManager.getPackageUid(pkgName, 0), channel
        )

    private fun buildChannelPattern(channelPattern: String) =
        runCatching { Regex(channelPattern) }
            .getOrElse { Regex(Regex.escape(channelPattern)) }

    private fun matchChannel(
        channel: NotificationChannel,
        notificationChannelGroup: NotificationChannelGroup,
        channelPattern: Regex
    ): Boolean {
        val channelName = channel.name?.toString().orEmpty()
        val channelGroupName = notificationChannelGroup.name?.toString().orEmpty()
        return channelPattern.containsMatchIn(channel.id) ||
            channelPattern.containsMatchIn(channelName) ||
            channelPattern.containsMatchIn(channelGroupName)
    }

    // 获取通知通道信息
    fun getNotificationChannelInfoByRegex(pkgName: String, channelNameRegex: String): List<AppInfoHelper.NCInfo> {
        val ncInfoList = mutableListOf<AppInfoHelper.NCInfo>()
        val channelPattern = buildChannelPattern(channelNameRegex)
        getNotificationChannelGroups(pkgName).forEach{ notificationChannelGroup ->
            notificationChannelGroup.channels.forEach {
                if (matchChannel(it, notificationChannelGroup, channelPattern))
                    ncInfoList.add(
                        AppInfoHelper.NCInfo(
                            notificationChannelGroup.name?.toString() ?: "",
                            it.name?.toString().orEmpty(),
                            it.id,
                            it.importance
                        )
                    )
            }
        }
        return ncInfoList
    }

    fun enableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo) {
        getNotificationChannelGroups(appInfo.packageName).forEach { group ->
            group.channels.forEach { channel ->
                if (channel.id == appInfo.ncInfo.channelId) {
                    channel.importance = NotificationManager.IMPORTANCE_DEFAULT
                    setNotificationChannel(appInfo.packageName, channel)
                }
            }
        }
    }

    fun disableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo) {
        getNotificationChannelGroups(appInfo.packageName).forEach { group ->
            group.channels.forEach { channel ->
                if (channel.id == appInfo.ncInfo.channelId) {
                    channel.importance = NotificationManager.IMPORTANCE_NONE
                    setNotificationChannel(appInfo.packageName, channel)
                }
            }
        }
    }
}
