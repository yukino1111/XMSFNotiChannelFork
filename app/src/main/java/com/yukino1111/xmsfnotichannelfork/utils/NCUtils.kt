package com.yukino1111.xmsfnotichannelfork.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

@SuppressLint("PrivateApi", "SoonBlockedPrivateApi", "DiscouragedPrivateApi")
class NCUtils(private val context: Context) {

    companion object {
        init {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
    }

    private val sINM: Any? by lazy {
        runCatching {
            val notificationService = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, "notification")
            Class.forName("android.app.INotificationManager\$Stub")
                .getDeclaredMethod("asInterface", IBinder::class.java)
                .invoke(null, notificationService)
        }.getOrNull()
    }

    private val notificationChannelGroupsM: Method? by lazy {
        runCatching {
            sINM?.javaClass?.getDeclaredMethod(
                "getNotificationChannelGroupsForPackage",
                String::class.java,
                Int::class.java,
                Boolean::class.java
            )
        }.getOrNull()
    }

    private val updateNotificationChannelForPackageM: Method? by lazy {
        runCatching {
            sINM?.javaClass?.getDeclaredMethod(
                "updateNotificationChannelForPackage",
                String::class.java,
                Int::class.java,
                NotificationChannel::class.java
            )
        }.getOrNull()
    }

    private val parceledListGetListM: Method? by lazy {
        runCatching {
            Class.forName("android.content.pm.ParceledListSlice").getDeclaredMethod("getList")
        }.getOrNull()
    }

    private fun getNotificationChannelGroups(pkgName: String): List<NotificationChannelGroup> =
        runCatching {
            val service = sINM ?: return emptyList()
            val method = notificationChannelGroupsM ?: return emptyList()
            val getList = parceledListGetListM ?: return emptyList()
            val uid = context.packageManager.getPackageUid(pkgName, 0)
            val slice = method.invoke(service, pkgName, uid, false) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            getList.invoke(slice) as? List<NotificationChannelGroup> ?: emptyList()
        }.getOrDefault(emptyList())

    private fun setNotificationChannel(pkgName: String, channel: NotificationChannel): Boolean =
        runCatching {
            val service = sINM ?: return false
            val method = updateNotificationChannelForPackageM ?: return false
            val uid = context.packageManager.getPackageUid(pkgName, 0)
            method.invoke(service, pkgName, uid, channel)
            true
        }.getOrDefault(false)

    private fun toNCInfo(channel: NotificationChannel, group: NotificationChannelGroup) =
        AppInfoHelper.NCInfo(
            group.name?.toString().orEmpty(),
            channel.name?.toString().orEmpty(),
            channel.id,
            channel.importance
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

    fun getAllChannels(pkgName: String): List<AppInfoHelper.NCInfo> {
        val list = mutableListOf<AppInfoHelper.NCInfo>()
        getNotificationChannelGroups(pkgName).forEach { group ->
            group.channels.forEach { channel ->
                list.add(toNCInfo(channel, group))
            }
        }
        return list
    }

    fun getNotificationChannelInfoByRegex(pkgName: String, channelNameRegex: String): List<AppInfoHelper.NCInfo> {
        val ncInfoList = mutableListOf<AppInfoHelper.NCInfo>()
        val channelPattern = buildChannelPattern(channelNameRegex)
        getNotificationChannelGroups(pkgName).forEach { notificationChannelGroup ->
            notificationChannelGroup.channels.forEach {
                if (matchChannel(it, notificationChannelGroup, channelPattern))
                    ncInfoList.add(toNCInfo(it, notificationChannelGroup))
            }
        }
        return ncInfoList
    }

    fun getChannelImportance(pkgName: String, channelId: String): Int {
        getNotificationChannelGroups(pkgName).forEach { group ->
            group.channels.forEach { channel ->
                if (channel.id == channelId) return channel.importance
            }
        }
        return -1
    }

    fun setChannelImportance(pkgName: String, channelId: String, importance: Int): Boolean {
        getNotificationChannelGroups(pkgName).forEach { group ->
            group.channels.forEach { channel ->
                if (channel.id == channelId) {
                    channel.importance = importance
                    return setNotificationChannel(pkgName, channel)
                }
            }
        }
        return false
    }

    fun enableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo) {
        setChannelImportance(appInfo.packageName, appInfo.ncInfo.channelId, NotificationManager.IMPORTANCE_DEFAULT)
    }

    fun disableSpecificNotification(appInfo: AppInfoHelper.MyAppInfo) {
        setChannelImportance(appInfo.packageName, appInfo.ncInfo.channelId, NotificationManager.IMPORTANCE_NONE)
    }
}
