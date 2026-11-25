package com.yukino1111.xmsfnotichannelfork.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yukino1111.xmsfnotichannelfork.utils.HookBridge
import com.yukino1111.xmsfnotichannelfork.utils.NCUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class NotiDatabaseHelper private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "noti_history.db"
        private const val DB_VERSION = 3
        private const val TABLE_HISTORY = "noti_history"
        private const val TABLE_IGNORED = "ignored_pkgs"
        private const val TABLE_FORCE_BLOCK = "force_block_channels"
        private const val TABLE_CONTENT_RULES = "content_block_rules"
        private const val MAX_ROWS = 10000L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val DEDUP_WINDOW_MS = 60_000L
        private const val PRUNE_INTERVAL = 50

        @Volatile
        private var instance: NotiDatabaseHelper? = null

        fun getInstance(context: Context): NotiDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: NotiDatabaseHelper(context.applicationContext).also { instance = it }
            }
    }

    private val insertCounter = AtomicInteger(0)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Single-file journal so root pulls get a self-contained database without -wal/.shm sidecars.
        db.disableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                channel_name TEXT NOT NULL DEFAULT '',
                channel_group TEXT NOT NULL DEFAULT '',
                importance INTEGER NOT NULL DEFAULT -1,
                title TEXT NOT NULL DEFAULT '',
                text TEXT NOT NULL DEFAULT '',
                sub_text TEXT NOT NULL DEFAULT '',
                post_time INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_noti_pkg_chan_time ON $TABLE_HISTORY(pkg, channel_id, post_time DESC)")
        db.execSQL("CREATE INDEX idx_noti_time ON $TABLE_HISTORY(post_time DESC)")
        db.execSQL(
            """
            CREATE TABLE $TABLE_IGNORED (
                pkg TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createForceBlockTable(db)
        createContentRulesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createForceBlockTable(db)
        }
        if (oldVersion < 3) {
            createContentRulesTable(db)
        }
    }

    private fun createForceBlockTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FORCE_BLOCK (
                pkg TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (pkg, channel_id)
            )
            """.trimIndent()
        )
    }

    private fun createContentRulesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CONTENT_RULES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg TEXT NOT NULL,
                channel_id TEXT NOT NULL DEFAULT '',
                pattern TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_content_rules_pkg ON $TABLE_CONTENT_RULES(pkg)")
    }

    @Synchronized
    fun insertNotification(
        pkg: String,
        channelId: String,
        channelName: String,
        channelGroup: String,
        importance: Int,
        title: String,
        text: String,
        subText: String,
        postTime: Long
    ): Boolean {
        if (isPackageIgnored(pkg)) return false
        val db = writableDatabase
        val cutoff = postTime - DEDUP_WINDOW_MS
        db.rawQuery(
            """
            SELECT 1 FROM $TABLE_HISTORY
            WHERE pkg = ? AND channel_id = ? AND title = ? AND text = ? AND post_time >= ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(pkg, channelId, title, text, cutoff.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) return false
        }

        val values = ContentValues().apply {
            put("pkg", pkg)
            put("channel_id", channelId)
            put("channel_name", channelName)
            put("channel_group", channelGroup)
            put("importance", importance)
            put("title", title)
            put("text", text)
            put("sub_text", subText)
            put("post_time", postTime)
        }
        val rowId = db.insert(TABLE_HISTORY, null, values)
        if (rowId != -1L && insertCounter.incrementAndGet() % PRUNE_INTERVAL == 0) {
            pruneOldRecords(db, HookBridge.getRetentionDays(appContext))
        }
        return rowId != -1L
    }

    @Synchronized
    fun pruneExpired(retentionDays: Int): Int =
        pruneOldRecords(writableDatabase, retentionDays)

    private fun pruneOldRecords(db: SQLiteDatabase, retentionDays: Int): Int {
        var deletedByAge = 0
        if (retentionDays > 0) {
            val safeDays = retentionDays.coerceIn(1, 365)
            val expiredBefore = System.currentTimeMillis() - safeDays * DAY_MS
            deletedByAge = db.delete(TABLE_HISTORY, "post_time < ?", arrayOf(expiredBefore.toString()))
        }
        db.execSQL(
            """
            DELETE FROM $TABLE_HISTORY
            WHERE id NOT IN (
                SELECT id FROM $TABLE_HISTORY ORDER BY post_time DESC, id DESC LIMIT $MAX_ROWS
            )
            """.trimIndent()
        )
        return deletedByAge
    }

    @Synchronized
    fun isPackageIgnored(pkg: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_IGNORED WHERE pkg = ? LIMIT 1",
            arrayOf(pkg)
        ).use { return it.moveToFirst() }
    }

    @Synchronized
    fun setPackageIgnored(pkg: String, ignored: Boolean) {
        val db = writableDatabase
        if (ignored) {
            val values = ContentValues().apply {
                put("pkg", pkg)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_IGNORED, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete(TABLE_IGNORED, "pkg = ?", arrayOf(pkg))
        }
    }

    @Synchronized
    fun getIgnoredPackages(): Set<String> {
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT pkg FROM $TABLE_IGNORED", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
        return result
    }

    @Synchronized
    fun setForceBlocked(pkg: String, channelId: String, blocked: Boolean) {
        val db = writableDatabase
        if (blocked) {
            val values = ContentValues().apply {
                put("pkg", pkg)
                put("channel_id", channelId)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_FORCE_BLOCK, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete(TABLE_FORCE_BLOCK, "pkg = ? AND channel_id = ?", arrayOf(pkg, channelId))
        }
    }

    @Synchronized
    fun isForceBlocked(pkg: String, channelId: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_FORCE_BLOCK WHERE pkg = ? AND channel_id = ? LIMIT 1",
            arrayOf(pkg, channelId)
        ).use { return it.moveToFirst() }
    }

    @Synchronized
    fun getForceBlockedKeys(): Set<String> {
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT pkg, channel_id FROM $TABLE_FORCE_BLOCK", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add("${cursor.getString(0)}|${cursor.getString(1)}")
            }
        }
        return result
    }

    @Synchronized
    fun getContentRules(pkg: String? = null, onlyEnabled: Boolean = false): List<ContentBlockRule> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!pkg.isNullOrBlank()) {
            clauses.add("pkg = ?")
            args.add(pkg)
        }
        if (onlyEnabled) {
            clauses.add("enabled = 1")
        }
        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val list = mutableListOf<ContentBlockRule>()
        readableDatabase.rawQuery(
            "SELECT id, pkg, channel_id, pattern, enabled, created_at FROM $TABLE_CONTENT_RULES $where ORDER BY created_at DESC, id DESC",
            args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ContentBlockRule(
                        id = c.getLong(0),
                        pkg = c.getString(1),
                        channelId = c.getString(2),
                        pattern = c.getString(3),
                        enabled = c.getInt(4) == 1,
                        createdAt = c.getLong(5),
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun addContentRule(pkg: String, channelId: String, pattern: String): Long {
        val cleanPkg = pkg.trim()
        val cleanChannel = channelId.trim()
        val cleanPattern = pattern.trim()
        if (cleanPkg.isEmpty() || cleanPattern.isEmpty()) return -1L
        runCatching { Regex(cleanPattern, RegexOption.IGNORE_CASE) }.onFailure { return -1L }

        val db = writableDatabase
        db.rawQuery(
            "SELECT id FROM $TABLE_CONTENT_RULES WHERE pkg = ? AND channel_id = ? AND pattern = ? LIMIT 1",
            arrayOf(cleanPkg, cleanChannel, cleanPattern)
        ).use { c ->
            if (c.moveToFirst()) {
                val existingId = c.getLong(0)
                val cv = ContentValues().apply { put("enabled", 1) }
                db.update(TABLE_CONTENT_RULES, cv, "id = ?", arrayOf(existingId.toString()))
                return existingId
            }
        }
        val values = ContentValues().apply {
            put("pkg", cleanPkg)
            put("channel_id", cleanChannel)
            put("pattern", cleanPattern)
            put("enabled", 1)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert(TABLE_CONTENT_RULES, null, values)
    }

    @Synchronized
    fun setContentRuleEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update(TABLE_CONTENT_RULES, values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteContentRule(id: Long) {
        writableDatabase.delete(TABLE_CONTENT_RULES, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clearContentRules(pkg: String? = null): Int {
        val db = writableDatabase
        return if (pkg.isNullOrBlank()) {
            db.delete(TABLE_CONTENT_RULES, null, null)
        } else {
            db.delete(TABLE_CONTENT_RULES, "pkg = ?", arrayOf(pkg))
        }
    }

    @Synchronized
    fun insertHookBatch(array: JSONArray, retentionDays: Int = DEFAULT_RETENTION_DAYS): Int {
        var inserted = 0
        val cutoff = if (retentionDays > 0) {
            System.currentTimeMillis() - retentionDays.coerceIn(1, 365) * DAY_MS
        } else {
            Long.MIN_VALUE
        }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val pkg = item.optString("pkg").trim()
            if (pkg.isEmpty()) continue
            val postTime = item.optLong("postTime", System.currentTimeMillis())
            if (postTime < cutoff) continue
            val channelId = item.optString("channelId")
            val channelName = item.optString("channelName")
            val channelGroup = item.optString("channelGroup")
            val importance = item.optInt("importance", -1)
            val title = item.optString("title")
            val text = item.optString("text")
            val subText = item.optString("subText")
            if (insertNotification(pkg, channelId, channelName, channelGroup, importance, title, text, subText, postTime)) {
                inserted++
            }
        }
        pruneOldRecords(writableDatabase, retentionDays)
        return inserted
    }

    @Synchronized
    fun getAppSummaries(query: String = ""): List<AppHistorySummary> {
        val ignored = getIgnoredPackages()
        val pm = appContext.packageManager
        val matchedByText = if (query.isNotBlank()) {
            val like = "%${query.trim()}%"
            mutableSetOf<String>().apply {
                readableDatabase.rawQuery(
                    """
                    SELECT DISTINCT pkg FROM $TABLE_HISTORY
                    WHERE pkg LIKE ? OR channel_name LIKE ? OR channel_id LIKE ? OR title LIKE ? OR text LIKE ?
                    """.trimIndent(),
                    arrayOf(like, like, like, like, like)
                ).use { c ->
                    while (c.moveToNext()) add(c.getString(0))
                }
            }
        } else null

        val list = mutableListOf<AppHistorySummary>()
        readableDatabase.rawQuery(
            """
            SELECT pkg, COUNT(DISTINCT channel_id) AS ch_cnt, COUNT(*) AS total_cnt, MAX(post_time) AS last_ts
            FROM $TABLE_HISTORY
            GROUP BY pkg
            ORDER BY last_ts DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val pkg = cursor.getString(0)
                val label = resolveAppName(pm, pkg)
                if (query.isNotBlank()) {
                    val q = query.trim().lowercase(Locale.getDefault())
                    val nameHit = label.lowercase(Locale.getDefault()).contains(q) ||
                        pkg.lowercase(Locale.getDefault()).contains(q)
                    if (!nameHit && matchedByText?.contains(pkg) != true) continue
                }
                list.add(
                    AppHistorySummary(
                        pkg = pkg,
                        appName = label,
                        channelCount = cursor.getInt(1),
                        totalCount = cursor.getLong(2),
                        lastPostTime = cursor.getLong(3),
                        isIgnored = ignored.contains(pkg)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getChannelSummariesForApp(pkg: String, sampleLimit: Int = 3): List<ChannelHistorySummary> {
        val list = mutableListOf<ChannelHistorySummary>()
        readableDatabase.rawQuery(
            """
            SELECT channel_id, COUNT(*) AS cnt, MAX(post_time) AS last_ts
            FROM $TABLE_HISTORY
            WHERE pkg = ?
            GROUP BY channel_id
            ORDER BY last_ts DESC
            """.trimIndent(),
            arrayOf(pkg)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val channelId = cursor.getString(0)
                val count = cursor.getLong(1)
                val lastTs = cursor.getLong(2)
                val samples = getRecordsForChannel(pkg, channelId, sampleLimit)
                val latest = samples.firstOrNull()
                list.add(
                    ChannelHistorySummary(
                        pkg = pkg,
                        channelId = channelId,
                        channelName = latest?.channelName.orEmpty(),
                        channelGroup = latest?.channelGroup.orEmpty(),
                        importance = latest?.importance ?: -1,
                        count = count,
                        lastPostTime = lastTs,
                        samples = samples
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getRecordsForChannel(pkg: String, channelId: String, limit: Int = 50): List<NotiRecord> {
        val list = mutableListOf<NotiRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, pkg, channel_id, channel_name, channel_group, importance, title, text, sub_text, post_time
            FROM $TABLE_HISTORY
            WHERE pkg = ? AND channel_id = ?
            ORDER BY post_time DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(pkg, channelId, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    NotiRecord(
                        id = cursor.getLong(0),
                        pkg = cursor.getString(1),
                        channelId = cursor.getString(2),
                        channelName = cursor.getString(3),
                        channelGroup = cursor.getString(4),
                        importance = cursor.getInt(5),
                        title = cursor.getString(6),
                        text = cursor.getString(7),
                        subText = cursor.getString(8),
                        postTime = cursor.getLong(9)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getChannelQuickStats(): Map<Pair<String, String>, Pair<Long, String>> {
        val counts = mutableMapOf<Pair<String, String>, Long>()
        readableDatabase.rawQuery(
            "SELECT pkg, channel_id, COUNT(*) FROM $TABLE_HISTORY GROUP BY pkg, channel_id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                counts[c.getString(0) to c.getString(1)] = c.getLong(2)
            }
        }
        val latest = mutableMapOf<Pair<String, String>, String>()
        readableDatabase.rawQuery(
            """
            SELECT pkg, channel_id, title, text FROM $TABLE_HISTORY
            ORDER BY post_time DESC, id DESC
            LIMIT 2000
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) to c.getString(1)
                if (latest.containsKey(key)) continue
                val preview = listOf(c.getString(2), c.getString(3))
                    .filter { it.isNotBlank() }
                    .joinToString("：")
                latest[key] = preview
            }
        }
        return counts.mapValues { (key, count) -> count to latest[key].orEmpty() }
    }

    @Synchronized
    fun getRecentRecords(query: String = "", limit: Int = 300): List<NotiRecord> {
        val raw = mutableListOf<NotiRecord>()
        val fetchLimit = if (query.isBlank()) limit else 2000
        readableDatabase.rawQuery(
            """
            SELECT id, pkg, channel_id, channel_name, channel_group, importance, title, text, sub_text, post_time
            FROM $TABLE_HISTORY
            ORDER BY post_time DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(fetchLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                raw.add(
                    NotiRecord(
                        id = cursor.getLong(0),
                        pkg = cursor.getString(1),
                        channelId = cursor.getString(2),
                        channelName = cursor.getString(3),
                        channelGroup = cursor.getString(4),
                        importance = cursor.getInt(5),
                        title = cursor.getString(6),
                        text = cursor.getString(7),
                        subText = cursor.getString(8),
                        postTime = cursor.getLong(9)
                    )
                )
            }
        }
        if (query.isBlank()) return raw

        val regex = runCatching { Regex(query.trim(), RegexOption.IGNORE_CASE) }
            .getOrElse { Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE) }
        val pm = appContext.packageManager
        val appNameCache = mutableMapOf<String, String>()
        val filtered = mutableListOf<NotiRecord>()
        for (rec in raw) {
            val appName = appNameCache.getOrPut(rec.pkg) { resolveAppName(pm, rec.pkg) }
            val hit = regex.containsMatchIn(appName) ||
                regex.containsMatchIn(rec.pkg) ||
                regex.containsMatchIn(rec.channelName) ||
                regex.containsMatchIn(rec.channelId) ||
                regex.containsMatchIn(rec.title) ||
                regex.containsMatchIn(rec.text)
            if (hit) {
                filtered.add(rec)
                if (filtered.size >= limit) break
            }
        }
        return filtered
    }

    @Synchronized
    fun clearHistory(pkg: String? = null): Int {
        val db = writableDatabase
        return if (pkg.isNullOrBlank()) {
            db.delete(TABLE_HISTORY, null, null)
        } else {
            db.delete(TABLE_HISTORY, "pkg = ?", arrayOf(pkg))
        }
    }

    @Synchronized
    fun exportReportJson(
        ncUtils: NCUtils?,
        sampleLimit: Int = 10,
        onlyEnabled: Boolean = false
    ): JSONObject {
        val pm = appContext.packageManager
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val apps = getAppSummaries()
        val forceBlockedKeys = getForceBlockedKeys()
        val channelArray = JSONArray()
        val liveCache = mutableMapOf<String, Map<String, com.yukino1111.xmsfnotichannelfork.utils.AppInfoHelper.NCInfo>>()

        for (app in apps) {
            val liveMap = liveCache.getOrPut(app.pkg) {
                ncUtils?.getAllChannels(app.pkg)?.associateBy { it.channelId }.orEmpty()
            }
            val channels = getChannelSummariesForApp(app.pkg, sampleLimit)
            for (ch in channels) {
                val live = liveMap[ch.channelId]
                val effectiveImportance = live?.importance ?: ch.importance
                val isForceBlocked = forceBlockedKeys.contains("${app.pkg}|${ch.channelId}")
                if (onlyEnabled && effectiveImportance == 0 && !isForceBlocked) continue

                val sampleArray = JSONArray()
                val seenSamples = HashSet<String>()
                for (rec in ch.samples) {
                    val dedupKey = "${rec.title}\u0000${rec.text}"
                    if (!seenSamples.add(dedupKey)) continue
                    sampleArray.put(
                        JSONObject().apply {
                            put("title", rec.title)
                            put("text", rec.text)
                            if (rec.subText.isNotBlank()) put("subText", rec.subText)
                            put("postTime", rec.postTime)
                            put("timeStr", timeFmt.format(Date(rec.postTime)))
                        }
                    )
                }

                channelArray.put(
                    JSONObject().apply {
                        put("pkg", app.pkg)
                        put("appName", app.appName.ifBlank { resolveAppName(pm, app.pkg) })
                        put("channelId", ch.channelId)
                        put("channelName", live?.channelName?.ifBlank { ch.channelName } ?: ch.channelName)
                        put("channelGroup", live?.channelGroupName?.ifBlank { ch.channelGroup } ?: ch.channelGroup)
                        put("importance", effectiveImportance)
                        put("enabled", effectiveImportance != 0)
                        put("forceBlocked", isForceBlocked)
                        put("count", ch.count)
                        put("lastPostTime", ch.lastPostTime)
                        put("lastTimeStr", timeFmt.format(Date(ch.lastPostTime)))
                        put("samples", sampleArray)
                    }
                )
            }
        }

        val contentRuleArray = JSONArray()
        getContentRules().forEach { rule ->
            contentRuleArray.put(
                JSONObject().apply {
                    put("id", rule.id)
                    put("pkg", rule.pkg)
                    put("channelId", rule.channelId)
                    put("pattern", rule.pattern)
                    put("enabled", rule.enabled)
                }
            )
        }

        return JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("generatedAtStr", timeFmt.format(Date()))
            put("channelCount", channelArray.length())
            put("channels", channelArray)
            put("contentRuleCount", contentRuleArray.length())
            put("contentRules", contentRuleArray)
        }
    }

    private fun resolveAppName(pm: android.content.pm.PackageManager, pkg: String): String =
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
}
