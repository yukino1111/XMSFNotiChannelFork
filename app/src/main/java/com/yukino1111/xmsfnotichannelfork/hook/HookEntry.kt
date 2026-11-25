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
import android.util.Log
import com.yukino1111.xmsfnotichannelfork.BuildConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class HookEntry : XposedModule() {

    companion object {
        private const val TAG = "XMSF_GG"
        private const val RULES_FILE = "/data/system/xmsf_hook_rules.json"
        private const val BUFFER_FILE = "/data/system/xmsf_noti_buffer.json"
        private const val MAX_BUFFER_SIZE = 800
        private const val FLUSH_BATCH_LIMIT = 150
        private const val MAX_TITLE_LEN = 200
        private const val MAX_TEXT_LEN = 600
        private const val DEDUP_WINDOW_MS = 60_000L
        private const val ONGOING_COOLDOWN_MS = 10 * 60_000L
        private val DYNAMIC_VALUE_REGEX = Regex("\\d+(?:[.,:]\\d+)*")
        private val SIZE_UNIT_REGEX = Regex("(?i)\\b(?:[KMGTPE]?i?B(?:/s)?)\\b")

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
    private data class OngoingSnapshot(val lastRecordedAt: Long, val signature: String)

    private val forceBlockKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val ignoredPkgs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val contentRulesByPkg = ConcurrentHashMap<String, List<CompiledContentRule>>()
    private val ongoingSnapshots = ConcurrentHashMap<String, OngoingSnapshot>()

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val bufferLock = Any()
    private val buffer = ArrayList<JSONObject>()
    private val seqGen = AtomicLong(0L)
    private val saveScheduled = AtomicBoolean(false)
    private val receiverRegistered = AtomicBoolean(false)

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        loadPersistedState()

        val nmsClass = runCatching {
            Class.forName("com.android.server.notification.NotificationManagerService", false, param.classLoader)
        }.getOrElse { err ->
            log(Log.ERROR, TAG, "failed to find NotificationManagerService", err)
            return
        }

        findMethodInHierarchy(nmsClass, "onBootPhase", Int::class.javaPrimitiveType!!)?.let { method ->
            hook(method).intercept { chain ->
                val result = chain.proceed()
                val phase = chain.getArg(0) as? Int
                if (phase != null && phase >= 550) {
                    ensureReceiverFromService(chain.thisObject)
                }
                result
            }
        } ?: log(Log.WARN, TAG, "failed to find onBootPhase")

        findMethodInHierarchy(nmsClass, "isCallerSystemOrPhone")?.let { method ->
            hook(method).intercept { chain ->
                val ctx = getNmsContext(chain.thisObject)
                if (ctx != null) {
                    ensureReceiverRegistered(ctx)
                    val isModuleCaller = runCatching {
                        val targetUid = ctx.packageManager.getPackageUid(BuildConfig.APPLICATION_ID, PackageManager.MATCH_ALL)
                        targetUid == Binder.getCallingUid()
                    }.getOrDefault(false)
                    if (isModuleCaller) return@intercept true
                }
                chain.proceed()
            }
        } ?: log(Log.WARN, TAG, "failed to find isCallerSystemOrPhone")

        val enqueueMethod = nmsClass.declaredMethods
            .filter { it.name == "enqueueNotificationInternal" && it.parameterCount >= 8 }
            .maxByOrNull { it.parameterCount }
        val enqueueReturnsBoolean = enqueueMethod?.returnType == java.lang.Boolean.TYPE

        if (enqueueMethod != null) {
            hook(enqueueMethod).intercept { chain ->
                val shouldBlock = runCatching {
                    ensureReceiverFromService(chain.thisObject)
                    val pkg = chain.getArg(0) as? String ?: return@runCatching false
                    if (pkg == BuildConfig.APPLICATION_ID) return@runCatching false
                    val notification = chain.getArg(6) as? Notification ?: return@runCatching false

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

                    val tag = (chain.getArg(4) as? String).orEmpty()
                    val notiId = (chain.getArg(5) as? Int) ?: 0
                    val userId = (chain.getArg(7) as? Int) ?: 0
                    val slotKey = "$userId|$tag|$notiId"
                    val isOngoing = isOngoingOrLiveNotification(notification)
                    val shouldRecord = !ignoredPkgs.contains(pkg) && (recordOngoing || !isOngoing || blockTag != null)

                    if (shouldRecord) {
                        recordNotification(pkg, channelId, title, text, rawSub, blockTag, slotKey, isOngoing)
                    }

                    blockTag != null
                }.getOrDefault(false)

                if (shouldBlock) {
                    if (enqueueReturnsBoolean) false else null
                } else {
                    chain.proceed()
                }
            }
        } else {
            log(Log.WARN, TAG, "failed to find enqueueNotificationInternal")
        }

        log(Log.INFO, TAG, "API $apiVersion hooked NotificationManagerService ($frameworkName $frameworkVersion)")
    }

    private fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        vararg paramTypes: Class<*>
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val found = runCatching {
                current.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
            }.getOrNull()
            if (found != null) return found
            current = current.superclass
        }
        return null
    }

    private fun findFieldInHierarchy(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val found = runCatching {
                current.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()
            if (found != null) return found
            current = current.superclass
        }
        return null
    }

    private fun ensureReceiverFromService(nmsInstance: Any?) {
        val ctx = getNmsContext(nmsInstance) ?: return
        ensureReceiverRegistered(ctx)
    }

    private fun getNmsContext(nmsInstance: Any?): Context? {
        if (nmsInstance == null) return null
        return runCatching {
            findMethodInHierarchy(nmsInstance.javaClass, "getContext")?.invoke(nmsInstance) as? Context
        }.getOrNull() ?: runCatching {
            findFieldInHierarchy(nmsInstance.javaClass, "mContext")?.get(nmsInstance) as? Context
        }.getOrNull()
    }

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
            log(Log.ERROR, TAG, "failed to register system receiver", it)
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

    private fun isOngoingOrLiveNotification(notification: Notification): Boolean {
        val ongoingFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_NO_CLEAR
        if ((notification.flags and ongoingFlags) != 0) return true
        if (notification.category == Notification.CATEGORY_PROGRESS ||
            notification.category == Notification.CATEGORY_STOPWATCH
        ) {
            return true
        }
        val extras = notification.extras ?: return false
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    }

    private fun normalizeDynamicText(value: String): String =
        value.replace(DYNAMIC_VALUE_REGEX, "#").replace(SIZE_UNIT_REGEX, "U")

    private fun recordNotification(
        pkg: String,
        channelId: String,
        title: String,
        text: String,
        rawSub: String,
        blockTag: String?,
        slotKey: String,
        isOngoing: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (isOngoing && blockTag == null) {
            val signature = "${normalizeDynamicText(title)}\u0000${normalizeDynamicText(text)}"
            val throttleKey = "$pkg|$channelId|$slotKey"
            val prev = ongoingSnapshots[throttleKey]
            if (prev != null && prev.signature == signature && now - prev.lastRecordedAt < ONGOING_COOLDOWN_MS) {
                return
            }
            if (ongoingSnapshots.size > 512) {
                ongoingSnapshots.entries.removeIf { now - it.value.lastRecordedAt >= ONGOING_COOLDOWN_MS }
            }
            ongoingSnapshots[throttleKey] = OngoingSnapshot(now, signature)
        }

        val subText = if (blockTag != null) {
            if (rawSub.isBlank()) blockTag else "$rawSub · $blockTag"
        } else {
            rawSub
        }
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
            put("slotKey", slotKey)
            put("isOngoing", isOngoing)
        }

        synchronized(bufferLock) {
            pruneBufferLocked(now)
            if (isOngoing && blockTag == null && slotKey.isNotEmpty()) {
                buffer.removeAll { prev ->
                    prev.optBoolean("isOngoing", false) &&
                        prev.optInt("importance", -1) != 0 &&
                        prev.optString("pkg") == pkg &&
                        prev.optString("channelId") == channelId &&
                        prev.optString("slotKey") == slotKey
                }
            } else {
                val cutoff = now - DEDUP_WINDOW_MS
                val isDup = buffer.asReversed().any { prev ->
                    if (prev.optLong("postTime") < cutoff) return@any false
                    prev.optString("pkg") == pkg &&
                        prev.optString("channelId") == channelId &&
                        prev.optString("title") == title &&
                        prev.optString("text") == text
                }
                if (isDup) return
            }
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
