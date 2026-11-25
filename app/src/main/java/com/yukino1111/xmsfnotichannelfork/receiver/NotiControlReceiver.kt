package com.yukino1111.xmsfnotichannelfork.receiver

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yukino1111.xmsfnotichannelfork.data.NotiDatabaseHelper
import com.yukino1111.xmsfnotichannelfork.utils.HookBridge
import com.yukino1111.xmsfnotichannelfork.utils.NCUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotiControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXPORT_REPORT = "com.yukino1111.xmsfnotichannelfork.ACTION_EXPORT_REPORT"
        const val ACTION_SET_CHANNELS = "com.yukino1111.xmsfnotichannelfork.ACTION_SET_CHANNELS"
        const val ACTION_CLEAR_HISTORY = "com.yukino1111.xmsfnotichannelfork.ACTION_CLEAR_HISTORY"

        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        executor.execute {
            val resultJson = runCatching {
                when (intent.action) {
                    ACTION_EXPORT_REPORT -> handleExport(appContext, intent)
                    ACTION_SET_CHANNELS -> handleSetChannels(appContext, intent)
                    ACTION_CLEAR_HISTORY -> handleClear(appContext, intent)
                    else -> JSONObject().put("ok", false).put("error", "unknown_action")
                }
            }.getOrElse { err ->
                JSONObject().put("ok", false).put("error", err.message ?: err.javaClass.simpleName)
            }

            pending.resultCode = Activity.RESULT_OK
            pending.resultData = resultJson.toString()
            pending.finish()
        }
    }

    private fun handleExport(context: Context, intent: Intent): JSONObject {
        val latch = CountDownLatch(1)
        HookBridge.requestFlushHistory(context) { latch.countDown() }
        latch.await(1500, TimeUnit.MILLISECONDS)

        val sampleLimit = intent.getIntExtra("sampleLimit", 10).coerceIn(1, 50)
        val onlyEnabled = intent.getBooleanExtra("onlyEnabled", false)
        val report = NotiDatabaseHelper.getInstance(context)
            .exportReportJson(NCUtils(context), sampleLimit, onlyEnabled)

        val defaultFile = File(context.filesDir, "agent_report.json")
        defaultFile.writeText(report.toString(2))

        val outPath = intent.getStringExtra("out")
        if (!outPath.isNullOrBlank()) {
            runCatching { File(outPath).writeText(report.toString(2)) }
        }

        return JSONObject().apply {
            put("ok", true)
            put("path", defaultFile.absolutePath)
            if (!outPath.isNullOrBlank()) put("out", outPath)
            put("channelCount", report.optInt("channelCount"))
        }
    }

    private fun handleSetChannels(context: Context, intent: Intent): JSONObject {
        val nc = NCUtils(context)
        val db = NotiDatabaseHelper.getInstance(context)
        val autoForceBlock = intent.getBooleanExtra("autoForceBlock", false)
        val rules = parseRules(intent)
        val results = JSONArray()
        var succeeded = 0
        var failed = 0

        for (i in 0 until rules.length()) {
            val item = rules.optJSONObject(i) ?: continue
            val pkg = item.optString("pkg").trim()
            val channelId = item.optString("channelId").trim()
            val action = item.optString("action").lowercase()

            if (action == "add_content_rule") {
                val pattern = item.optString("pattern").trim()
                val ruleId = if (pkg.isNotEmpty() && pattern.isNotEmpty()) {
                    db.addContentRule(pkg, channelId, pattern)
                } else -1L
                val okRule = ruleId > 0L
                if (okRule) succeeded++ else failed++
                results.put(
                    JSONObject().apply {
                        put("pkg", pkg)
                        put("channelId", channelId)
                        put("pattern", pattern)
                        put("ruleId", ruleId)
                        put("ok", okRule)
                    }
                )
                continue
            }

            if (action == "clear_content_rules") {
                val removed = db.clearContentRules(pkg.ifBlank { null })
                succeeded++
                results.put(
                    JSONObject().apply {
                        put("pkg", pkg)
                        put("clearedContentRules", removed)
                        put("ok", true)
                    }
                )
                continue
            }

            if (pkg.isEmpty() || channelId.isEmpty()) {
                failed++
                results.put(item.apply { put("ok", false); put("error", "invalid_rule") })
                continue
            }

            if (action == "force_block" || action == "unblock" || item.has("forceBlock")) {
                val forceBlock = when {
                    item.has("forceBlock") -> item.optBoolean("forceBlock")
                    action == "force_block" -> true
                    else -> false
                }
                db.setForceBlocked(pkg, channelId, forceBlock)
                succeeded++
                results.put(
                    JSONObject().apply {
                        put("pkg", pkg)
                        put("channelId", channelId)
                        put("forceBlock", forceBlock)
                        put("ok", true)
                    }
                )
                continue
            }

            val importance = resolveImportance(item)
            if (importance < 0) {
                failed++
                results.put(item.apply { put("ok", false); put("error", "invalid_importance") })
                continue
            }
            val updated = nc.setChannelImportance(pkg, channelId, importance)
            val actual = nc.getChannelImportance(pkg, channelId)
            val locked = importance == 0 && (!updated || actual > 0)
            var forceApplied = false
            val finalOk = if (!locked) {
                if (importance > 0) db.setForceBlocked(pkg, channelId, false)
                updated
            } else if (autoForceBlock) {
                db.setForceBlocked(pkg, channelId, true)
                forceApplied = true
                true
            } else {
                false
            }
            if (finalOk) succeeded++ else failed++
            results.put(
                JSONObject().apply {
                    put("pkg", pkg)
                    put("channelId", channelId)
                    put("importance", importance)
                    put("actualImportance", actual)
                    put("forceBlocked", forceApplied)
                    put("ok", finalOk)
                }
            )
        }

        HookBridge.syncRulesToSystem(context)

        val summary = JSONObject().apply {
            put("ok", failed == 0)
            put("succeeded", succeeded)
            put("failed", failed)
            put("results", results)
        }
        runCatching { File(context.filesDir, "agent_last_apply.json").writeText(summary.toString(2)) }
        return summary
    }

    private fun handleClear(context: Context, intent: Intent): JSONObject {
        val pkg = intent.getStringExtra("pkg")
        val deleted = NotiDatabaseHelper.getInstance(context).clearHistory(pkg)
        return JSONObject().apply {
            put("ok", true)
            put("deleted", deleted)
        }
    }

    private fun parseRules(intent: Intent): JSONArray {
        val rulesJson = intent.getStringExtra("rulesJson")
        if (!rulesJson.isNullOrBlank()) {
            return JSONArray(rulesJson)
        }
        val rulesFile = intent.getStringExtra("rulesFile")
        if (!rulesFile.isNullOrBlank()) {
            return JSONArray(File(rulesFile).readText())
        }
        val pkg = intent.getStringExtra("pkg")
        val channelId = intent.getStringExtra("channelId")
        val importance = intent.getIntExtra("importance", NotificationManager.IMPORTANCE_NONE)
        if (!pkg.isNullOrBlank() && !channelId.isNullOrBlank()) {
            return JSONArray().put(
                JSONObject().apply {
                    put("pkg", pkg)
                    put("channelId", channelId)
                    put("importance", importance)
                }
            )
        }
        return JSONArray()
    }

    private fun resolveImportance(item: JSONObject): Int {
        if (item.has("importance")) return item.optInt("importance", -1)
        return when (item.optString("action").lowercase()) {
            "disable", "off", "block" -> NotificationManager.IMPORTANCE_NONE
            "enable", "on", "allow" -> NotificationManager.IMPORTANCE_DEFAULT
            else -> -1
        }
    }
}
