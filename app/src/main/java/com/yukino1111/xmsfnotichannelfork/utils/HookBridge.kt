package com.yukino1111.xmsfnotichannelfork.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.yukino1111.xmsfnotichannelfork.BuildConfig
import com.yukino1111.xmsfnotichannelfork.data.NotiDatabaseHelper
import com.yukino1111.xmsfnotichannelfork.hook.HookEntry
import org.json.JSONArray
import org.json.JSONObject

import java.util.concurrent.Executors

object HookBridge {

    private const val PREF_FILE = "xmsf_prefs"
    const val PREF_FORCE_BLOCK_ENABLED = "force_block_enabled"
    const val PREF_RECORD_ONGOING = "record_ongoing_notifications"
    const val PREF_RETENTION_DAYS = "history_retention_days"
    const val DEFAULT_RETENTION_DAYS = 30

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private fun callerToken(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            0,
            Intent("com.yukino1111.xmsfnotichannelfork.CALLER_PROOF").setPackage(BuildConfig.APPLICATION_ID),
            PendingIntent.FLAG_IMMUTABLE
        )

    fun isForceBlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_FORCE_BLOCK_ENABLED, true)

    fun setForceBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_FORCE_BLOCK_ENABLED, enabled).apply()
        syncRulesToSystem(context)
    }

    fun isRecordOngoing(context: Context): Boolean =
        prefs(context).getBoolean(PREF_RECORD_ONGOING, false)

    fun setRecordOngoing(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_RECORD_ONGOING, enabled).apply()
        syncRulesToSystem(context)
    }

    fun getRetentionDays(context: Context): Int =
        prefs(context).getInt(PREF_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
            .let { if (it <= 0) 0 else it.coerceIn(1, 365) }

    fun setRetentionDays(context: Context, days: Int) {
        val normalized = if (days <= 0) 0 else days.coerceIn(1, 365)
        prefs(context).edit().putInt(PREF_RETENTION_DAYS, normalized).apply()
        syncRulesToSystem(context)
    }

    fun buildRulesJson(context: Context): String {
        val db = NotiDatabaseHelper.getInstance(context)
        val forceKeys = JSONArray()
        db.getForceBlockedKeys().forEach { forceKeys.put(it) }
        val ignoredPkgs = JSONArray()
        db.getIgnoredPackages().forEach { ignoredPkgs.put(it) }
        val contentRules = JSONArray()
        db.getContentRules(onlyEnabled = true).forEach { rule ->
            contentRules.put(
                JSONObject().apply {
                    put("pkg", rule.pkg)
                    put("channelId", rule.channelId)
                    put("pattern", rule.pattern)
                }
            )
        }
        return JSONObject().apply {
            put("forceBlockEnabled", isForceBlockEnabled(context))
            put("recordOngoing", isRecordOngoing(context))
            put("retentionDays", getRetentionDays(context))
            put("forceBlockKeys", forceKeys)
            put("ignoredPkgs", ignoredPkgs)
            put("contentRules", contentRules)
        }.toString()
    }

    fun checkHookStatus(context: Context, onResult: (active: Boolean, needsReboot: Boolean) -> Unit) {
        val appCtx = context.applicationContext
        val intent = Intent(HookEntry.ACTION_PING).putExtra("caller", callerToken(appCtx))
        appCtx.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val code = resultCode
                    when {
                        code == BuildConfig.VERSION_CODE -> onResult(true, false)
                        code > 0 -> onResult(true, true)
                        else -> onResult(false, false)
                    }
                }
            },
            mainHandler,
            0,
            null,
            null
        )
    }

    fun syncRulesToSystem(context: Context) {
        val appCtx = context.applicationContext
        val intent = Intent(HookEntry.ACTION_RULES_SYNC)
            .putExtra("caller", callerToken(appCtx))
            .putExtra("rulesJson", buildRulesJson(appCtx))
        appCtx.sendOrderedBroadcast(intent, null)
    }

    fun requestFlushHistory(context: Context, onSynced: ((Int) -> Unit)? = null) {
        val appCtx = context.applicationContext
        syncRulesToSystem(appCtx)
        flushNextBatch(appCtx, accumulated = 0, onSynced = onSynced)
    }

    private fun flushNextBatch(appCtx: Context, accumulated: Int, onSynced: ((Int) -> Unit)?) {
        val intent = Intent(HookEntry.ACTION_FLUSH_HISTORY).putExtra("caller", callerToken(appCtx))
        appCtx.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val raw = resultData
                    if (resultCode != Activity.RESULT_OK || raw.isNullOrBlank()) {
                        onSynced?.invoke(accumulated)
                        return
                    }
                    val parsed = runCatching { JSONObject(raw) }.getOrNull()
                    if (parsed == null) {
                        onSynced?.invoke(accumulated)
                        return
                    }
                    val seq = parsed.optLong("seq", 0L)
                    val hasMore = parsed.optBoolean("hasMore", false)
                    val arr = parsed.optJSONArray("records") ?: JSONArray()
                    ioExecutor.execute {
                        val db = NotiDatabaseHelper.getInstance(appCtx)
                        val inserted = db.insertHookBatch(arr, getRetentionDays(appCtx))
                        if (seq > 0L) {
                            val ackIntent = Intent(HookEntry.ACTION_FLUSH_ACK)
                                .putExtra("caller", callerToken(appCtx))
                                .putExtra("seq", seq)
                            appCtx.sendOrderedBroadcast(ackIntent, null)
                        }
                        val total = accumulated + inserted
                        mainHandler.post {
                            if (hasMore && arr.length() > 0) {
                                flushNextBatch(appCtx, total, onSynced)
                            } else {
                                onSynced?.invoke(total)
                            }
                        }
                    }
                }
            },
            mainHandler,
            0,
            null,
            null
        )
    }
}
