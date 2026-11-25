package com.yukino1111.xmsfnotichannelfork.hook

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import com.yukino1111.xmsfnotichannelfork.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "XMSF_GG"
        private const val RULES_FILE = "/data/system/xmsf_hook_rules.json"
        private const val BUFFER_FILE = "/data/system/xmsf_noti_buffer.json"
        private const val MAX_BUFFER_SIZE = 800
        private const val FLUSH_BATCH_LIMIT = 150
        private const val MAX_TITLE_LEN = 200
        private const val MAX_TEXT_LEN = 600
        private const val DEDUP_WINDOW_MS = 60_000L

        const val ACTION_PING = "${BuildConfig.APPLICATION_ID}.INTERNAL_PING"
        const val ACTION_RULES_SYNC = "${BuildConfig.APPLICATION_ID}.INTERNAL_RULES_SYNC"
        const val ACTION_FLUSH_HISTORY = "${BuildConfig.APPLICATION_ID}.INTERNAL_FLUSH_HISTORY"
        const val ACTION_FLUSH_ACK = "${BuildConfig.APPLICATION_ID}.INTERNAL_FLUSH_ACK"
    }

    @Volatile
    private var forceBlockEnabled = true

    @Volatile
    private var recordOngoing = false

    @Volatile
    private var retentionDays = 30

    private data class CompiledContentRule(val channelId: String, val regex: Regex)

    private val forceBlockKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val ignoredPkgs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val contentRulesByPkg = ConcurrentHashMap<String, List<CompiledContentRule>>()

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val bufferLock = Any()
    private val buffer = ArrayList<JSONObject>()
    private val seqGen = AtomicLong(0L)
    private val saveScheduled = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        loadPersistedState()

        val nmsClass = runCatching {
            XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", lpparam.classLoader)
        }.getOrElse { err ->
            XposedBridge.log("$TAG: failed to find NotificationManagerService: ${err.message}")
            return
        }

        XposedHelpers.findAndHookMethod(
            nmsClass,
            "onBootPhase",
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val phase = param.args.getOrNull(0) as? Int ?: return
                    if (phase >= 550) {
                        ensureReceiverFromService(param.thisObject)
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            nmsClass,
            "isCallerSystemOrPhone",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ctx = getNmsContext(param.thisObject) ?: return
                    ensureReceiverRegistered(ctx)
                    runCatching {
                        val targetUid = ctx.packageManager.getPackageUid(BuildConfig.APPLICATION_ID, PackageManager.MATCH_ALL)
                        if (targetUid == Binder.getCallingUid()) {
                            param.result = true
                        }
                    }
                }
            }
        )

        val enqueueMethod = nmsClass.declaredMethods
            .filter { it.name == "enqueueNotificationInternal" && it.parameterCount >= 8 }
            .maxByOrNull { it.parameterCount }
        val enqueueReturnsBoolean = enqueueMethod?.returnType == java.lang.Boolean.TYPE

        if (enqueueMethod != null) {
            XposedBridge.hookMethod(
                enqueueMethod,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching {
                            ensureReceiverFromService(param.thisObject)
                            val pkg = param.args.getOrNull(0) as? String ?: return
                            if (pkg == BuildConfig.APPLICATION_ID) return
                            val notification = param.args.getOrNull(6) as? Notification ?: return

                            val channelId = notification.channelId.orEmpty()
                            val blockedByChannel = forceBlockEnabled && forceBlockKeys.contains("$pkg|$channelId")
                            val contentRules = if (!blockedByChannel) contentRulesByPkg[pkg].orEmpty() else emptyList()
                            val (title, text, rawSub) = extractFields(notification)
                            val blockedByContent = contentRules.any { rule ->
                                (rule.channelId.isEmpty() || rule.channelId == channelId) &&
                                    (rule.regex.containsMatchIn(title) || rule.regex.containsMatchIn(text))
                            }
                            val blockTag = when {
                                blockedByChannel -> "[已强拦截]"
                                blockedByContent -> "[内容规则拦截]"
                                else -> null
                            }

                            val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                                (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
                            val shouldRecord = !ignoredPkgs.contains(pkg) && (recordOngoing || !isOngoing || blockTag != null)

                            if (shouldRecord) {
                                recordNotification(pkg, channelId, title, text, rawSub, blockTag)
                            }

                            if (blockTag != null) {
                                param.result = if (enqueueReturnsBoolean) false else null
                            }
                        }
                    }
                }
            )
        }
    }

    private fun ensureReceiverFromService(nmsInstance: Any?) {
        val ctx = getNmsContext(nmsInstance) ?: return
        ensureReceiverRegistered(ctx)
    }

    private fun getNmsContext(nmsInstance: Any?): Context? =
        runCatching {
            nmsInstance?.let { XposedHelpers.callMethod(it, "getContext") as? Context }
                ?: nmsInstance?.let { XposedHelpers.getObjectField(it, "mContext") as? Context }
        }.getOrNull()

    private fun ensureReceiverRegistered(context: Context) {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PING)
            addAction(ACTION_RULES_SYNC)
            addAction(ACTION_FLUSH_HISTORY)
            addAction(ACTION_FLUSH_ACK)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val caller = intent.getParcelableExtra<PendingIntent>("caller")
                if (caller?.creatorPackage != BuildConfig.APPLICATION_ID) return

                when (intent.action) {
                    ACTION_PING -> {
                        resultCode = BuildConfig.VERSION_CODE
                        resultData = "ok"
                    }
                    ACTION_RULES_SYNC -> {
                        val rulesJson = intent.getStringExtra("rulesJson") ?: return
                        applyRulesJson(rulesJson, persist = true)
                        resultCode = Activity.RESULT_OK
                        resultData = "synced"
                    }
                    ACTION_FLUSH_HISTORY -> {
                        val (seq, hasMore, arr) = snapshotBatch()
                        resultCode = Activity.RESULT_OK
                        resultData = JSONObject().apply {
                            put("seq", seq)
                            put("hasMore", hasMore)
                            put("records", arr)
                        }.toString()
                    }
                    ACTION_FLUSH_ACK -> {
                        val ackSeq = intent.getLongExtra("seq", 0L)
                        if (ackSeq > 0L) removeAckedRecords(ackSeq)
                        resultCode = Activity.RESULT_OK
                        resultData = "acked"
                    }
                }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }.onFailure {
            receiverRegistered.set(false)
            XposedBridge.log("$TAG: failed to register system receiver: ${it.message}")
        }
    }

    private fun extractFields(notification: Notification): Triple<String, String, String> {
        val extras = notification.extras ?: Bundle.EMPTY
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
            ?.toString()?.trim().orEmpty().take(MAX_TITLE_LEN)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
            ?.joinToString("\n")
            .orEmpty()
        val text = listOf(bigText, lines, normalText).maxByOrNull { it.length }.orEmpty().take(MAX_TEXT_LEN)
        val rawSub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            .take(MAX_TITLE_LEN)
        return Triple(title, text, rawSub)
    }

    private fun recordNotification(
        pkg: String,
        channelId: String,
        title: String,
        text: String,
        rawSub: String,
        blockTag: String?
    ) {
        val subText = if (blockTag != null) {
            if (rawSub.isBlank()) blockTag else "$rawSub · $blockTag"
        } else {
            rawSub
        }
        val now = System.currentTimeMillis()
        val seq = seqGen.incrementAndGet()

        val entry = JSONObject().apply {
            put("seq", seq)
            put("pkg", pkg)
            put("channelId", channelId)
            put("channelName", "")
            put("channelGroup", "")
            put("importance", if (blockTag != null) 0 else -1)
            put("title", title)
            put("text", text)
            put("subText", subText)
            put("postTime", now)
        }

        synchronized(bufferLock) {
            pruneBufferLocked(now)
            val cutoff = now - DEDUP_WINDOW_MS
            val isDup = buffer.asReversed().any { prev ->
                if (prev.optLong("postTime") < cutoff) return@any false
                prev.optString("pkg") == pkg &&
                    prev.optString("channelId") == channelId &&
                    prev.optString("title") == title &&
                    prev.optString("text") == text
            }
            if (isDup) return
            buffer.add(entry)
            while (buffer.size > MAX_BUFFER_SIZE) {
                buffer.removeAt(0)
            }
        }
        scheduleBufferPersist()
    }

    private fun pruneBufferLocked(now: Long = System.currentTimeMillis()) {
        if (retentionDays <= 0) return
        val expiredBefore = now - retentionDays.coerceIn(1, 365) * 86_400_000L
        buffer.removeAll { it.optLong("postTime", now) < expiredBefore }
    }

    private fun snapshotBatch(): Triple<Long, Boolean, JSONArray> {
        synchronized(bufferLock) {
            pruneBufferLocked()
            val arr = JSONArray()
            var maxSeq = 0L
            val count = minOf(buffer.size, FLUSH_BATCH_LIMIT)
            for (i in 0 until count) {
                val obj = buffer[i]
                arr.put(obj)
                val s = obj.optLong("seq", 0L)
                if (s > maxSeq) maxSeq = s
            }
            return Triple(maxSeq, buffer.size > count, arr)
        }
    }

    private fun removeAckedRecords(ackSeq: Long) {
        synchronized(bufferLock) {
            buffer.removeAll { it.optLong("seq", 0L) <= ackSeq }
        }
        scheduleBufferPersist()
    }

    private fun scheduleBufferPersist() {
        if (!saveScheduled.compareAndSet(false, true)) return
        ioExecutor.execute {
            runCatching {
                Thread.sleep(1000L)
                saveScheduled.set(false)
                val arr = synchronized(bufferLock) {
                    JSONArray().apply { buffer.forEach { put(it) } }
                }
                File(BUFFER_FILE).writeText(arr.toString())
            }.onFailure {
                saveScheduled.set(false)
            }
        }
    }

    private fun loadPersistedState() {
        runCatching {
            val rulesFile = File(RULES_FILE)
            if (rulesFile.exists()) {
                applyRulesJson(rulesFile.readText(), persist = false)
            }
        }
        runCatching {
            val bufFile = File(BUFFER_FILE)
            if (bufFile.exists()) {
                val arr = JSONArray(bufFile.readText())
                synchronized(bufferLock) {
                    var maxSeq = 0L
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val s = obj.optLong("seq", 0L)
                        if (s > maxSeq) maxSeq = s
                        buffer.add(obj)
                    }
                    if (maxSeq > seqGen.get()) seqGen.set(maxSeq)
                    pruneBufferLocked()
                }
            }
        }
    }

    private fun applyRulesJson(raw: String, persist: Boolean) {
        runCatching {
            val obj = JSONObject(raw)
            forceBlockEnabled = obj.optBoolean("forceBlockEnabled", true)
            recordOngoing = obj.optBoolean("recordOngoing", false)
            retentionDays = obj.optInt("retentionDays", 30).let { if (it <= 0) 0 else it.coerceIn(1, 365) }
            synchronized(bufferLock) { pruneBufferLocked() }

            val newForce = HashSet<String>()
            obj.optJSONArray("forceBlockKeys")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val key = arr.optString(i)
                    if (key.isNotBlank()) newForce.add(key)
                }
            }
            forceBlockKeys.clear()
            forceBlockKeys.addAll(newForce)

            val newIgnored = HashSet<String>()
            obj.optJSONArray("ignoredPkgs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i)
                    if (p.isNotBlank()) newIgnored.add(p)
                }
            }
            ignoredPkgs.clear()
            ignoredPkgs.addAll(newIgnored)

            val newContentRules = HashMap<String, MutableList<CompiledContentRule>>()
            obj.optJSONArray("contentRules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pkg = item.optString("pkg").trim()
                    val chId = item.optString("channelId").trim()
                    val pattern = item.optString("pattern")
                    if (pkg.isEmpty() || pattern.isBlank()) continue
                    val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: continue
                    newContentRules.getOrPut(pkg) { ArrayList() }.add(CompiledContentRule(chId, regex))
                }
            }
            contentRulesByPkg.clear()
            contentRulesByPkg.putAll(newContentRules)

            if (persist) {
                ioExecutor.execute {
                    runCatching { File(RULES_FILE).writeText(raw) }
                }
            }
        }
    }
}
