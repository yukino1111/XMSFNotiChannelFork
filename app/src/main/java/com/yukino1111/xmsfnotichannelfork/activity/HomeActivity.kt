package com.yukino1111.xmsfnotichannelfork.activity

import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.yukino1111.xmsfnotichannelfork.data.ChannelHistorySummary
import com.yukino1111.xmsfnotichannelfork.data.NotiDatabaseHelper
import com.yukino1111.xmsfnotichannelfork.data.NotiRecord
import com.yukino1111.xmsfnotichannelfork.ui.AppDetailState
import com.yukino1111.xmsfnotichannelfork.ui.ChannelEntry
import com.yukino1111.xmsfnotichannelfork.ui.MainUiState
import com.yukino1111.xmsfnotichannelfork.ui.NotiChannelScreen
import com.yukino1111.xmsfnotichannelfork.ui.TimelineEntry
import com.yukino1111.xmsfnotichannelfork.ui.theme.NotiChannelTheme
import com.yukino1111.xmsfnotichannelfork.utils.AppInfoHelper
import com.yukino1111.xmsfnotichannelfork.utils.HookBridge
import com.yukino1111.xmsfnotichannelfork.utils.NCUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class HomeActivity : ComponentActivity() {

    companion object {
        private const val LAUNCHER_ALIAS_CLS = "com.yukino1111.xmsfnotichannelfork.activity.LauncherAlias"
    }

    private val db by lazy { NotiDatabaseHelper.getInstance(applicationContext) }
    private val nc by lazy { NCUtils(applicationContext) }
    private val appInfoHelper by lazy { AppInfoHelper(applicationContext) }

    private val state = MutableStateFlow(MainUiState())

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) saveReportToUri(uri)
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importRulesFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val launcherComponent = ComponentName(packageName, LAUNCHER_ALIAS_CLS)
        val isHidden = packageManager.getComponentEnabledSetting(launcherComponent) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        state.update {
            it.copy(
                forceBlockEnabled = HookBridge.isForceBlockEnabled(this),
                recordOngoing = HookBridge.isRecordOngoing(this),
                hideLauncherIcon = isHidden,
                retentionDays = HookBridge.getRetentionDays(this),
            )
        }

        setContent {
            val uiState by state.collectAsState()
            NotiChannelTheme {
                NotiChannelScreen(
                    state = uiState,
                    onRefreshAll = ::refreshAll,
                    onToggleForceBlockGlobal = ::toggleForceBlockGlobal,
                    onToggleRecordOngoing = ::toggleRecordOngoing,
                    onToggleHideIcon = ::toggleHideIcon,
                    onToggleChannel = ::toggleChannel,
                    onSetForceBlock = ::setForceBlock,
                    onBatchSetImportance = ::batchSetImportance,
                    onLoadAppDetail = ::loadAppDetail,
                    onLoadChannelRecords = ::loadChannelRecords,
                    onToggleIgnoreApp = ::toggleIgnoreApp,
                    onAddContentRule = ::addContentRule,
                    onToggleContentRule = ::toggleContentRule,
                    onDeleteContentRule = ::deleteContentRule,
                    onSetRetentionDays = ::setRetentionDays,
                    onClearHistory = ::clearHistory,
                    onExportReportFile = ::exportReportToFile,
                    onImportRules = ::importRulesFromFile,
                    onDismissMessage = { state.update { it.copy(statusMessage = null) } },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        HookBridge.checkHookStatus(this) { active, needsReboot ->
            state.update { it.copy(hookActive = active, needsReboot = needsReboot) }
            if (active) {
                loadAllChannels(forceRefresh = true)
                HookBridge.requestFlushHistory(this) { inserted ->
                    loadHistoryData()
                    if (inserted > 0) loadAllChannels(forceRefresh = true)
                }
            } else {
                loadHistoryData()
            }
        }
    }

    private fun loadAllChannels(forceRefresh: Boolean) {
        lifecycleScope.launch {
            state.update { it.copy(loadingChannels = true) }
            val entries = withContext(Dispatchers.IO) {
                appInfoHelper.getAllChannels(forceRefresh = forceRefresh).map { info ->
                    ChannelEntry(
                        appName = info.appName,
                        packageName = info.packageName,
                        icon = info.icon,
                        channelGroupName = info.ncInfo.channelGroupName,
                        channelName = info.ncInfo.channelName,
                        channelId = info.ncInfo.channelId,
                        importance = info.ncInfo.importance,
                        isSystemApp = info.isSystemApp,
                        historyCount = info.historyCount,
                        latestPreview = info.latestPreview,
                        forceBlocked = info.forceBlocked,
                    )
                }
            }
            state.update { it.copy(loadingChannels = false, channels = entries) }
        }
    }

    private fun loadHistoryData() {
        lifecycleScope.launch {
            state.update { it.copy(loadingHistory = true) }
            val (timeline, apps, iconMap) = withContext(Dispatchers.IO) {
                db.pruneExpired(HookBridge.getRetentionDays(this@HomeActivity))
                val pm = packageManager
                val records = db.getRecentRecords(query = "", limit = 800)
                val appSummaries = db.getAppSummaries("")
                val forceKeys = db.getForceBlockedKeys()

                val nameCache = mutableMapOf<String, String>()
                val iconCache = mutableMapOf<String, Drawable?>()
                val liveChannelMap = mutableMapOf<Pair<String, String>, Pair<String, Int>>()

                val allPkgs = HashSet<String>().apply {
                    records.forEach { add(it.pkg) }
                    appSummaries.forEach { add(it.pkg) }
                }
                for (pkg in allPkgs) {
                    nameCache[pkg] = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    iconCache[pkg] = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                    if (state.value.hookActive) {
                        nc.getAllChannels(pkg).forEach { ch ->
                            liveChannelMap[pkg to ch.channelId] = ch.channelName to ch.importance
                        }
                    }
                }

                val tl = records.map { rec ->
                    val live = liveChannelMap[rec.pkg to rec.channelId]
                    TimelineEntry(
                        record = rec,
                        appName = nameCache[rec.pkg] ?: rec.pkg,
                        icon = iconCache[rec.pkg],
                        liveChannelName = live?.first?.ifBlank { rec.channelName } ?: rec.channelName,
                        importance = live?.second ?: rec.importance,
                        forceBlocked = forceKeys.contains("${rec.pkg}|${rec.channelId}"),
                    )
                }
                Triple(tl, appSummaries, iconCache.toMap())
            }
            state.update {
                it.copy(
                    loadingHistory = false,
                    timeline = timeline,
                    appSummaries = apps,
                    appIcons = iconMap,
                )
            }
        }
    }

    private fun toggleForceBlockGlobal(enabled: Boolean) {
        HookBridge.setForceBlockEnabled(this, enabled)
        state.update {
            it.copy(
                forceBlockEnabled = enabled,
                statusMessage = if (enabled) "已开启入队强拦截总开关" else "已暂停入队强拦截总开关",
            )
        }
    }

    private fun toggleRecordOngoing(enabled: Boolean) {
        HookBridge.setRecordOngoing(this, enabled)
        state.update {
            it.copy(
                recordOngoing = enabled,
                statusMessage = if (enabled) "已开启记录常驻通知" else "已设为忽略常驻通知",
            )
        }
    }

    private fun setRetentionDays(days: Int) {
        lifecycleScope.launch {
            val pruned = withContext(Dispatchers.IO) {
                HookBridge.setRetentionDays(this@HomeActivity, days)
                db.pruneExpired(days)
            }
            loadHistoryData()
            loadAllChannels(forceRefresh = true)
            val msg = when {
                days <= 0 -> "已设为不自动清理历史消息"
                pruned > 0 -> {
                    val label = when (days) {
                        1 -> "1天前"
                        7 -> "7天前"
                        else -> "30天前"
                    }
                    "已设为自动清理 $label 消息（本次清理 $pruned 条）"
                }
                else -> {
                    val label = when (days) {
                        1 -> "1天前"
                        7 -> "7天前"
                        else -> "30天前"
                    }
                    "已设为自动清理 $label 消息"
                }
            }
            state.update {
                it.copy(
                    retentionDays = days,
                    statusMessage = msg,
                )
            }
        }
    }

    private fun toggleHideIcon(hide: Boolean) {
        val component = ComponentName(packageName, LAUNCHER_ALIAS_CLS)
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
        state.update { it.copy(hideLauncherIcon = hide) }
    }

    private fun toggleChannel(
        pkg: String,
        channelId: String,
        currentImportance: Int,
        forceBlocked: Boolean,
        onLocked: () -> Unit,
    ) {
        if (forceBlocked) {
            setForceBlock(pkg, channelId, false)
            return
        }
        val disabling = currentImportance != NotificationManager.IMPORTANCE_NONE
        val target = if (disabling) NotificationManager.IMPORTANCE_NONE else NotificationManager.IMPORTANCE_DEFAULT
        lifecycleScope.launch {
            val (ok, actual) = withContext(Dispatchers.IO) {
                val updated = nc.setChannelImportance(pkg, channelId, target)
                val imp = nc.getChannelImportance(pkg, channelId)
                updated to imp
            }
            if (ok && (!disabling || actual == 0)) {
                val finalImp = if (actual >= 0) actual else target
                updateChannelInState(pkg, channelId, importance = finalImp)
            } else if (disabling && actual > 0) {
                onLocked()
            } else {
                state.update { it.copy(statusMessage = "修改失败，请确认模块已激活并重启过系统") }
            }
        }
    }

    private fun setForceBlock(pkg: String, channelId: String, enabled: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.setForceBlocked(pkg, channelId, enabled)
                if (enabled && !HookBridge.isForceBlockEnabled(this@HomeActivity)) {
                    HookBridge.setForceBlockEnabled(this@HomeActivity, true)
                } else {
                    HookBridge.syncRulesToSystem(this@HomeActivity)
                }
            }
            updateChannelInState(pkg, channelId, forceBlocked = enabled)
            state.update {
                it.copy(
                    forceBlockEnabled = HookBridge.isForceBlockEnabled(this@HomeActivity),
                    statusMessage = if (enabled) "已启用入队强拦截（即时生效）" else "已取消入队强拦截",
                )
            }
        }
    }

    private fun batchSetImportance(targets: List<ChannelEntry>, enable: Boolean) {
        lifecycleScope.launch {
            val targetImp = if (enable) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_NONE
            val updatedMap = withContext(Dispatchers.IO) {
                val map = HashMap<Pair<String, String>, Int>()
                for (item in targets) {
                    nc.setChannelImportance(item.packageName, item.channelId, targetImp)
                    val actual = nc.getChannelImportance(item.packageName, item.channelId)
                    map[item.packageName to item.channelId] = if (actual >= 0) actual else targetImp
                }
                map
            }
            state.update { cur ->
                cur.copy(
                    channels = cur.channels.map { ch ->
                        val next = updatedMap[ch.packageName to ch.channelId]
                        if (next != null) ch.copy(importance = next) else ch
                    },
                    statusMessage = "已批量处理 ${targets.size} 个渠道",
                )
            }
        }
    }

    private fun updateChannelInState(
        pkg: String,
        channelId: String,
        importance: Int? = null,
        forceBlocked: Boolean? = null,
    ) {
        state.update { cur ->
            cur.copy(
                channels = cur.channels.map { ch ->
                    if (ch.packageName == pkg && ch.channelId == channelId) {
                        ch.copy(
                            importance = importance ?: ch.importance,
                            forceBlocked = forceBlocked ?: ch.forceBlocked,
                        )
                    } else ch
                },
                timeline = cur.timeline.map { tl ->
                    if (tl.record.pkg == pkg && tl.record.channelId == channelId) {
                        tl.copy(
                            importance = importance ?: tl.importance,
                            forceBlocked = forceBlocked ?: tl.forceBlocked,
                        )
                    } else tl
                },
                appDetail = cur.appDetail?.let { d ->
                    if (d.pkg != pkg) d else d.copy(
                        channels = d.channels.map { ch ->
                            if (ch.channelId == channelId) {
                                ch.copy(
                                    importance = importance ?: ch.importance,
                                    forceBlocked = forceBlocked ?: ch.forceBlocked,
                                )
                            } else ch
                        }
                    )
                },
            )
        }
    }

    private fun loadAppDetail(pkg: String, appName: String) {
        state.update {
            it.copy(
                appDetail = AppDetailState(
                    pkg = pkg,
                    appName = appName,
                    icon = it.appIcons[pkg],
                    isIgnored = false,
                    channels = emptyList(),
                    loading = true,
                )
            )
        }
        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val ignored = db.isPackageIgnored(pkg)
                val forceKeys = db.getForceBlockedKeys()
                val histList = db.getChannelSummariesForApp(pkg, sampleLimit = 3)
                val liveList = if (state.value.hookActive) nc.getAllChannels(pkg) else emptyList()
                val liveMap = liveList.associateBy { it.channelId }
                val seen = HashSet<String>()
                val merged = mutableListOf<ChannelHistorySummary>()

                for (h in histList) {
                    seen.add(h.channelId)
                    val live = liveMap[h.channelId]
                    merged.add(
                        h.copy(
                            channelName = live?.channelName?.ifBlank { h.channelName } ?: h.channelName,
                            channelGroup = live?.channelGroupName?.ifBlank { h.channelGroup } ?: h.channelGroup,
                            importance = live?.importance ?: h.importance,
                            forceBlocked = forceKeys.contains("$pkg|${h.channelId}"),
                        )
                    )
                }
                for (live in liveList) {
                    if (!seen.add(live.channelId)) continue
                    merged.add(
                        ChannelHistorySummary(
                            pkg = pkg,
                            channelId = live.channelId,
                            channelName = live.channelName,
                            channelGroup = live.channelGroupName,
                            importance = live.importance,
                            count = 0L,
                            lastPostTime = 0L,
                            samples = emptyList(),
                            forceBlocked = forceKeys.contains("$pkg|${live.channelId}"),
                        )
                    )
                }
                val icon = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
                val contentRules = db.getContentRules(pkg)
                AppDetailState(
                    pkg = pkg,
                    appName = appName,
                    icon = icon,
                    isIgnored = ignored,
                    channels = merged,
                    contentRules = contentRules,
                    loading = false,
                )
            }
            state.update { it.copy(appDetail = detail) }
        }
    }

    private fun loadChannelRecords(pkg: String, channelId: String, onLoaded: (List<NotiRecord>) -> Unit) {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                db.getRecordsForChannel(pkg, channelId, limit = 50)
            }
            onLoaded(records)
        }
    }

    private fun toggleIgnoreApp(pkg: String, ignored: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.setPackageIgnored(pkg, ignored)
                HookBridge.syncRulesToSystem(this@HomeActivity)
            }
            state.update { cur ->
                cur.copy(
                    appSummaries = cur.appSummaries.map {
                        if (it.pkg == pkg) it.copy(isIgnored = ignored) else it
                    },
                    appDetail = cur.appDetail?.let { d ->
                        if (d.pkg == pkg) d.copy(isIgnored = ignored) else d
                    },
                    statusMessage = if (ignored) "已停止记录此应用" else "已恢复记录此应用",
                )
            }
        }
    }

    private fun addContentRule(pkg: String, channelId: String, pattern: String) {
        lifecycleScope.launch {
            val (ruleId, updatedRules) = withContext(Dispatchers.IO) {
                val id = db.addContentRule(pkg, channelId, pattern)
                if (id > 0L) HookBridge.syncRulesToSystem(this@HomeActivity)
                id to db.getContentRules(pkg)
            }
            state.update { cur ->
                cur.copy(
                    appDetail = cur.appDetail?.let { d ->
                        if (d.pkg == pkg) d.copy(contentRules = updatedRules) else d
                    },
                    statusMessage = if (ruleId > 0L) "已添加内容拦截规则" else "正则语法无效或规则为空",
                )
            }
        }
    }

    private fun toggleContentRule(pkg: String, ruleId: Long, enabled: Boolean) {
        lifecycleScope.launch {
            val updatedRules = withContext(Dispatchers.IO) {
                db.setContentRuleEnabled(ruleId, enabled)
                HookBridge.syncRulesToSystem(this@HomeActivity)
                db.getContentRules(pkg)
            }
            state.update { cur ->
                cur.copy(
                    appDetail = cur.appDetail?.let { d ->
                        if (d.pkg == pkg) d.copy(contentRules = updatedRules) else d
                    },
                    statusMessage = if (enabled) "已启用内容规则" else "已停用内容规则",
                )
            }
        }
    }

    private fun deleteContentRule(pkg: String, ruleId: Long) {
        lifecycleScope.launch {
            val updatedRules = withContext(Dispatchers.IO) {
                db.deleteContentRule(ruleId)
                HookBridge.syncRulesToSystem(this@HomeActivity)
                db.getContentRules(pkg)
            }
            state.update { cur ->
                cur.copy(
                    appDetail = cur.appDetail?.let { d ->
                        if (d.pkg == pkg) d.copy(contentRules = updatedRules) else d
                    },
                    statusMessage = "已删除内容规则",
                )
            }
        }
    }

    private fun clearHistory(pkg: String?) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) { db.clearHistory(pkg) }
            loadHistoryData()
            loadAllChannels(forceRefresh = true)
            state.update { it.copy(statusMessage = "已清空 $deleted 条历史记录") }
        }
    }

    private suspend fun buildExportReport(): Pair<Int, String> {
        if (state.value.hookActive) {
            withTimeoutOrNull(1500L) {
                suspendCancellableCoroutine { cont ->
                    HookBridge.requestFlushHistory(this@HomeActivity) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
        return withContext(Dispatchers.IO) {
            val report = db.exportReportJson(nc, sampleLimit = 10, onlyEnabled = false)
            val formatted = report.toString(2)
            File(filesDir, "noti_report.json").writeText(formatted)
            report.optInt("channelCount") to formatted
        }
    }

    private fun exportReportToFile() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        exportDocumentLauncher.launch("xmsf_noti_report_$stamp.json")
    }

    private fun saveReportToUri(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                val (channelCount, jsonStr) = buildExportReport()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(jsonStr)
                    } ?: error("无法打开输出文件")
                }
                channelCount to jsonStr.toByteArray(Charsets.UTF_8).size
            }
            result.onSuccess { (channelCount, bytes) ->
                loadHistoryData()
                val sizeLabel = if (bytes < 1024) "$bytes B" else "${(bytes + 1023) / 1024} KB"
                state.update { it.copy(statusMessage = "已导出完整报告（$channelCount 个渠道，$sizeLabel）") }
            }.onFailure { err ->
                state.update { it.copy(statusMessage = "导出失败：${err.message ?: "写入文件出错"}") }
            }
        }
    }

    private fun importRulesFromFile() {
        importDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
    }

    private fun importRulesFromUri(uri: Uri) {
        lifecycleScope.launch {
            val rawResult = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("无法读取文件内容")
                }
            }
            rawResult.onSuccess { raw ->
                importRules(raw)
            }.onFailure { err ->
                state.update { it.copy(statusMessage = "导入失败：${err.message ?: "读取文件出错"}") }
            }
        }
    }

    private fun importRules(raw: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) throw IllegalArgumentException("内容为空")
                    val liveCache = mutableMapOf<String, Map<String, AppInfoHelper.NCInfo>>()
                    var okCount = 0
                    var failCount = 0

                    fun applyRuleArray(array: JSONArray) {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            if (applyImportedEntry(obj, liveCache)) okCount++ else failCount++
                        }
                    }

                    if (trimmed.startsWith("[")) {
                        applyRuleArray(JSONArray(trimmed))
                    } else {
                        val root = JSONObject(trimmed)
                        val rulesArray = root.optJSONArray("rules")
                        val channelsArray = root.optJSONArray("channels")
                        val contentRulesArray = root.optJSONArray("contentRules")
                        val ignoredPackagesArray = root.optJSONArray("ignoredPackages")
                        if (rulesArray == null && channelsArray == null && contentRulesArray == null && ignoredPackagesArray == null) {
                            throw IllegalArgumentException("未找到 rules、channels 或 contentRules 字段")
                        }
                        ignoredPackagesArray?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val pkg = arr.optString(i).trim()
                                if (pkg.isNotEmpty()) {
                                    db.setPackageIgnored(pkg, true)
                                    okCount++
                                }
                            }
                        }
                        rulesArray?.let { applyRuleArray(it) }
                        channelsArray?.let { applyRuleArray(it) }
                        contentRulesArray?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val pkg = obj.optString("pkg").trim()
                                val channelId = obj.optString("channelId").trim()
                                val pattern = obj.optString("pattern").trim()
                                val enabled = obj.optBoolean("enabled", true)
                                if (db.addContentRule(pkg, channelId, pattern, enabled) > 0L) okCount++ else failCount++
                            }
                        }
                    }
                    HookBridge.syncRulesToSystem(this@HomeActivity)
                    okCount to failCount
                }
            }
            result.onSuccess { (okCount, failCount) ->
                refreshAll()
                state.update { it.copy(statusMessage = "导入完成：成功 $okCount，失败 $failCount") }
            }.onFailure { err ->
                val detail = err.message?.takeIf { it.isNotBlank() } ?: "JSON 格式无效"
                state.update { it.copy(statusMessage = "导入失败：$detail") }
            }
        }
    }

    private fun applyImportedEntry(
        obj: JSONObject,
        liveCache: MutableMap<String, Map<String, AppInfoHelper.NCInfo>>,
    ): Boolean {
        val pkg = obj.optString("pkg").trim()
        val channelId = obj.optString("channelId").trim()
        val action = obj.optString("action").lowercase(Locale.ROOT)

        when (action) {
            "add_content_rule" -> {
                val pattern = obj.optString("pattern").trim()
                val enabled = obj.optBoolean("enabled", true)
                return pkg.isNotEmpty() && db.addContentRule(pkg, channelId, pattern, enabled) > 0L
            }
            "clear_content_rules" -> {
                db.clearContentRules(pkg.ifBlank { null })
                return true
            }
            "ignore_app", "unignore_app" -> {
                if (pkg.isEmpty()) return false
                db.setPackageIgnored(pkg, action == "ignore_app")
                return true
            }
            "force_block", "unblock" -> {
                if (pkg.isEmpty() || channelId.isEmpty()) return false
                db.setForceBlocked(pkg, channelId, action == "force_block")
                return true
            }
        }

        if (pkg.isEmpty() || channelId.isEmpty()) return false

        if (obj.has("forceBlock")) {
            db.setForceBlocked(pkg, channelId, obj.optBoolean("forceBlock"))
            return true
        }

        val hasForceBlocked = obj.optBoolean("forceBlocked", false)
        if (hasForceBlocked) {
            db.setForceBlocked(pkg, channelId, true)
        }

        val importance = when {
            obj.has("importance") -> obj.optInt("importance", -1)
            action in listOf("enable", "on", "allow") -> NotificationManager.IMPORTANCE_DEFAULT
            action in listOf("disable", "off", "block") -> NotificationManager.IMPORTANCE_NONE
            obj.has("enabled") -> if (obj.optBoolean("enabled")) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_NONE
            else -> -1
        }

        if (importance < 0) return hasForceBlocked

        val liveMap = liveCache.getOrPut(pkg) {
            nc.getAllChannels(pkg).associateBy { it.channelId }
        }
        val currentImp = liveMap[channelId]?.importance
        if (currentImp == importance) {
            if (importance > 0 && !hasForceBlocked) db.setForceBlocked(pkg, channelId, false)
            return true
        }

        val updated = nc.setChannelImportance(pkg, channelId, importance)
        val actual = nc.getChannelImportance(pkg, channelId)
        val locked = importance == 0 && (!updated || actual > 0)
        if (!locked && updated) {
            if (importance > 0 && !hasForceBlocked) db.setForceBlocked(pkg, channelId, false)
            return true
        }
        if (locked && (hasForceBlocked || obj.optBoolean("autoForceBlock", false))) {
            db.setForceBlocked(pkg, channelId, true)
            return true
        }
        return hasForceBlocked
    }
}
