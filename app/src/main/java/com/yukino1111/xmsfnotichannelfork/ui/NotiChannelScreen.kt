package com.yukino1111.xmsfnotichannelfork.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.yukino1111.xmsfnotichannelfork.BuildConfig
import com.yukino1111.xmsfnotichannelfork.R
import com.yukino1111.xmsfnotichannelfork.data.AppHistorySummary
import com.yukino1111.xmsfnotichannelfork.data.ChannelHistorySummary
import com.yukino1111.xmsfnotichannelfork.data.ContentBlockRule
import com.yukino1111.xmsfnotichannelfork.data.NotiRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AD_REGEX_PRESET =
    "运营|广告|推广|营销|优惠|福利|促销|商业化|有奖|抽奖|限时活动|热门内容|热门视频推荐|精彩推荐|猜你喜欢|精选推荐|热点推荐|内容推荐|社交推荐|\\bads?\\b|promo|upsell"
private const val FORK_REPO_URL = "https://github.com/yukino1111/XMSFNotiChannelFork"

private enum class ChannelScopeFilter(val label: String) {
    ALL("全部状态"),
    ENABLED("仅已开启"),
    HAS_HISTORY("有历史记录"),
    FORCE_BLOCKED("已强拦截"),
}

private enum class HistorySubTab(val label: String) {
    TIMELINE("消息时间线"),
    BY_APP("按软件排查"),
}

private sealed interface ScreenRoute {
    data object Home : ScreenRoute
    data class AppDetail(val pkg: String, val appName: String) : ScreenRoute
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun NotiChannelScreen(
    state: MainUiState,
    onRefreshAll: () -> Unit,
    onToggleForceBlockGlobal: (Boolean) -> Unit,
    onToggleRecordOngoing: (Boolean) -> Unit,
    onToggleHideIcon: (Boolean) -> Unit,
    onToggleChannel: (pkg: String, channelId: String, currentImportance: Int, forceBlocked: Boolean, onLocked: () -> Unit) -> Unit,
    onSetForceBlock: (pkg: String, channelId: String, enabled: Boolean) -> Unit,
    onBatchSetImportance: (targets: List<ChannelEntry>, enable: Boolean) -> Unit,
    onLoadAppDetail: (pkg: String, appName: String) -> Unit,
    onLoadChannelRecords: (pkg: String, channelId: String, onLoaded: (List<NotiRecord>) -> Unit) -> Unit,
    onToggleIgnoreApp: (pkg: String, ignored: Boolean) -> Unit,
    onAddContentRule: (pkg: String, channelId: String, pattern: String) -> Unit,
    onToggleContentRule: (pkg: String, ruleId: Long, enabled: Boolean) -> Unit,
    onDeleteContentRule: (pkg: String, ruleId: Long) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onClearHistory: (pkg: String?) -> Unit,
    onExportReport: () -> Unit,
    onImportRules: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var route by remember { mutableStateOf<ScreenRoute>(ScreenRoute.Home) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var channelQuery by remember { mutableStateOf("") }
    var channelScope by remember { mutableStateOf(ChannelScopeFilter.ALL) }
    var hideSystemApps by remember { mutableStateOf(false) }

    var historySubTab by remember { mutableStateOf(HistorySubTab.TIMELINE) }
    var historyQuery by remember { mutableStateOf("") }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var lockedChannelPrompt by remember { mutableStateOf<Pair<String, String>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onDismissMessage()
    }

    val openAppDetail: (String, String) -> Unit = { pkg, appName ->
        onLoadAppDetail(pkg, appName)
        route = ScreenRoute.AppDetail(pkg, appName)
    }

    BackHandler(enabled = route !is ScreenRoute.Home) {
        route = ScreenRoute.Home
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (val currentRoute = route) {
            ScreenRoute.Home -> {
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        containerColor = MaterialTheme.colorScheme.surface,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                scrollBehavior = scrollBehavior,
                                expandedHeight = 76.dp,
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                title = {
                                    Column(
                                        modifier = Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {},
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showAboutDialog = true
                                            },
                                        ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val subtitle = when {
                                            !state.hookActive -> "LSPosed 未激活或未重启"
                                            state.needsReboot -> "模块已更新，重启后加载最新 Hook"
                                            else -> "已激活 · ${state.channels.size} 个渠道 · ${state.timeline.size} 条历史"
                                        }
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
                                    label = { Text("概览") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                                    label = { Text("全部渠道") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                    label = { Text("历史排查") },
                                )
                            }
                        },
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                        ) {
                            when (selectedTab) {
                                0 -> OverviewTab(
                                    state = state,
                                    onRefreshAll = onRefreshAll,
                                    onJumpChannels = { scope ->
                                        channelScope = scope
                                        selectedTab = 1
                                    },
                                    onJumpHistory = { selectedTab = 2 },
                                    onToggleForceBlockGlobal = onToggleForceBlockGlobal,
                                    onToggleRecordOngoing = onToggleRecordOngoing,
                                    onToggleHideIcon = onToggleHideIcon,
                                    onExportReport = onExportReport,
                                    onOpenImport = { showImportDialog = true },
                                )
                                1 -> ChannelsTab(
                                    state = state,
                                    query = channelQuery,
                                    onQueryChange = { channelQuery = it },
                                    scopeFilter = channelScope,
                                    onScopeChange = { channelScope = it },
                                    hideSystemApps = hideSystemApps,
                                    onHideSystemChange = { hideSystemApps = it },
                                    onBatchSetImportance = onBatchSetImportance,
                                    onOpenDetail = openAppDetail,
                                )
                                else -> HistoryTab(
                                    state = state,
                                    subTab = historySubTab,
                                    onSubTabChange = { historySubTab = it },
                                    query = historyQuery,
                                    onQueryChange = { historyQuery = it },
                                    onToggleChannel = { pkg, chId, imp, forceBlocked ->
                                        onToggleChannel(pkg, chId, imp, forceBlocked) {
                                            lockedChannelPrompt = pkg to chId
                                        }
                                    },
                                    onSetForceBlock = onSetForceBlock,
                                    onOpenDetail = openAppDetail,
                                    onToggleIgnoreApp = onToggleIgnoreApp,
                                    onClearAppHistory = { pkg -> onClearHistory(pkg) },
                                    onSetRetentionDays = onSetRetentionDays,
                                    onOpenClearAll = { showClearAllDialog = true },
                                )
                            }
                        }
                    }
                }

                is ScreenRoute.AppDetail -> {
                    AppDetailPage(
                        detail = state.appDetail,
                        fallbackPkg = currentRoute.pkg,
                        fallbackAppName = currentRoute.appName,
                        forceBlockGlobal = state.forceBlockEnabled,
                        snackbarHostState = snackbarHostState,
                        onBack = { route = ScreenRoute.Home },
                        onToggleIgnore = { ignored -> onToggleIgnoreApp(currentRoute.pkg, ignored) },
                        onToggleChannel = { ch ->
                            onToggleChannel(
                                ch.pkg,
                                ch.channelId,
                                ch.importance,
                                ch.forceBlocked,
                            ) {
                                lockedChannelPrompt = ch.pkg to ch.channelId
                            }
                        },
                        onSetForceBlock = { chId, enabled -> onSetForceBlock(currentRoute.pkg, chId, enabled) },
                        onLoadChannelRecords = onLoadChannelRecords,
                        onAddContentRule = { chId, pattern -> onAddContentRule(currentRoute.pkg, chId, pattern) },
                        onToggleContentRule = { ruleId, enabled -> onToggleContentRule(currentRoute.pkg, ruleId, enabled) },
                        onDeleteContentRule = { ruleId -> onDeleteContentRule(currentRoute.pkg, ruleId) },
                    )
                }
        }
    }

    lockedChannelPrompt?.let { (pkg, channelId) ->
        AlertDialog(
            onDismissRequest = { lockedChannelPrompt = null },
            title = { Text("系统锁死了此渠道") },
            text = {
                Text("该渠道重要度被系统锁定，常规关闭无效。\n是否将其加入「入队强拦截」，在系统通知入队时直接丢弃？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetForceBlock(pkg, channelId, true)
                        lockedChannelPrompt = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("加入入队强拦截")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockedChannelPrompt = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showImportDialog) {
        ImportRulesDialog(
            onDismiss = { showImportDialog = false },
            onApply = { raw ->
                showImportDialog = false
                onImportRules(raw)
            },
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空全部历史记录") },
            text = { Text("确定清空所有已采集到的通知历史吗？渠道开关与强拦截规则会保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onClearHistory(null)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showAboutDialog) {
        NotiChannelAboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverviewTab(
    state: MainUiState,
    onRefreshAll: () -> Unit,
    onJumpChannels: (ChannelScopeFilter) -> Unit,
    onJumpHistory: () -> Unit,
    onToggleForceBlockGlobal: (Boolean) -> Unit,
    onToggleRecordOngoing: (Boolean) -> Unit,
    onToggleHideIcon: (Boolean) -> Unit,
    onExportReport: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val listState = rememberLazyListState()
    val enabledCount = remember(state.channels) {
        state.channels.count { it.importance > 0 && !it.forceBlocked }
    }
    val forceCount = remember(state.channels) {
        state.channels.count { it.forceBlocked }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val isOk = state.hookActive && !state.needsReboot
            Card(
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isOk -> MaterialTheme.colorScheme.primaryContainer
                        state.needsReboot -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when {
                                isOk -> "系统框架 Hook 运行中"
                                state.needsReboot -> "模块已更新，需重启设备"
                                else -> "模块未激活或尚未重启"
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Text(
                            text = if (isOk) {
                                "NotificationManagerService 被动采集中 · 无需后台保活"
                            } else {
                                "请在 LSPosed 勾选「系统框架 (android)」并重启设备"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!isOk) {
                        Button(
                            onClick = onRefreshAll,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("重检")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "已开启渠道",
                    value = "$enabledCount",
                    subtitle = "共 ${state.channels.size} 个",
                    modifier = Modifier.weight(1f),
                    onClick = { onJumpChannels(ChannelScopeFilter.ENABLED) },
                )
                MetricCard(
                    title = "入队强拦截",
                    value = "$forceCount",
                    subtitle = if (state.forceBlockEnabled) "总开关已开" else "总开关已关",
                    modifier = Modifier.weight(1f),
                    onClick = { onJumpChannels(ChannelScopeFilter.FORCE_BLOCKED) },
                )
                MetricCard(
                    title = "历史记录",
                    value = "${state.timeline.size}",
                    subtitle = "${state.appSummaries.size} 个软件",
                    modifier = Modifier.weight(1f),
                    onClick = onJumpHistory,
                )
            }
        }

        item {
            Text(
                text = "全局策略",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlobalToggleRow(
                        title = "入队强拦截（可关锁死渠道）",
                        subtitle = "关闭后保留黑名单但不执行系统入队拦截",
                        checked = state.forceBlockEnabled,
                        onCheckedChange = onToggleForceBlockGlobal,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    GlobalToggleRow(
                        title = "记录常驻 / 前台服务通知",
                        subtitle = "关闭时跳过常驻状态通知；开启后按同一通知槽位合并更新",
                        checked = state.recordOngoing,
                        onCheckedChange = onToggleRecordOngoing,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    GlobalToggleRow(
                        title = "隐藏桌面图标",
                        subtitle = "隐藏后仍可从 LSPosed 模块管理页进入",
                        checked = state.hideLauncherIcon,
                        onCheckedChange = onToggleHideIcon,
                    )
                }
            }
        }

        item {
            Text(
                text = "数据导入导出",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ActionListRow(
                    icon = Icons.Rounded.FileUpload,
                    title = "导出历史排查 JSON 报告",
                    subtitle = "复制报告 JSON 并保存到应用数据目录",
                    index = 0,
                    count = 2,
                    onClick = onExportReport,
                )
                ActionListRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "粘贴规则 JSON 批量应用",
                    subtitle = "支持开关渠道、入队强拦截与内容正则规则",
                    index = 1,
                    count = 2,
                    onClick = onOpenImport,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = if (danger) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (danger) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

private data class AppChannelGroup(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val channels: List<ChannelEntry>,
    val enabledCount: Int,
    val blockedCount: Int,
    val historyTotal: Long,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ChannelsTab(
    state: MainUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    scopeFilter: ChannelScopeFilter,
    onScopeChange: (ChannelScopeFilter) -> Unit,
    hideSystemApps: Boolean,
    onHideSystemChange: (Boolean) -> Unit,
    onBatchSetImportance: (List<ChannelEntry>, Boolean) -> Unit,
    onOpenDetail: (String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    var batchConfirmEnable by remember { mutableStateOf<Boolean?>(null) }
    var longPressedGroup by remember { mutableStateOf<AppChannelGroup?>(null) }

    val filtered = remember(state.channels, query, scopeFilter, hideSystemApps) {
        val trimmed = query.trim()
        val regex = if (trimmed.isEmpty()) {
            null
        } else {
            runCatching { Regex(trimmed, RegexOption.IGNORE_CASE) }
                .getOrElse { Regex(Regex.escape(trimmed), RegexOption.IGNORE_CASE) }
        }
        state.channels.filter { item ->
            val scopeOk = when (scopeFilter) {
                ChannelScopeFilter.ALL -> true
                ChannelScopeFilter.ENABLED -> item.importance > 0 && !item.forceBlocked
                ChannelScopeFilter.HAS_HISTORY -> item.historyCount > 0
                ChannelScopeFilter.FORCE_BLOCKED -> item.forceBlocked
            }
            val sysOk = !hideSystemApps || !item.isSystemApp
            val queryOk = regex == null ||
                regex.containsMatchIn(item.appName) ||
                regex.containsMatchIn(item.packageName) ||
                regex.containsMatchIn(item.channelGroupName) ||
                regex.containsMatchIn(item.channelName) ||
                regex.containsMatchIn(item.channelId) ||
                (item.latestPreview.isNotBlank() && regex.containsMatchIn(item.latestPreview))
            scopeOk && sysOk && queryOk
        }
    }

    val groupedApps = remember(filtered) {
        filtered.groupBy { it.packageName }.values.map { list ->
            val first = list.first()
            AppChannelGroup(
                packageName = first.packageName,
                appName = first.appName,
                icon = first.icon,
                channels = list,
                enabledCount = list.count { it.importance > 0 && !it.forceBlocked },
                blockedCount = list.count { it.forceBlocked },
                historyTotal = list.sumOf { it.historyCount },
            )
        }.sortedWith(
            compareByDescending<AppChannelGroup> { it.historyTotal }
                .thenByDescending { it.enabledCount }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Md3eSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "正则筛选：软件 / 包名 / 渠道 / ID",
                    modifier = Modifier.weight(1f),
                )
                PopupFilterButton(
                    selectedLabel = scopeFilter.label,
                    icon = Icons.Rounded.FilterList,
                    items = ChannelScopeFilter.entries,
                    itemLabel = { it.label },
                    isSelected = { it == scopeFilter },
                    onSelect = onScopeChange,
                    showTrailingChevron = false,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isAllDefault = query.isBlank() && scopeFilter == ChannelScopeFilter.ALL && !hideSystemApps
                FilterChip(
                    selected = isAllDefault,
                    onClick = {
                        onQueryChange("")
                        onScopeChange(ChannelScopeFilter.ALL)
                        onHideSystemChange(false)
                    },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = query == AD_REGEX_PRESET,
                    onClick = {
                        onQueryChange(if (query == AD_REGEX_PRESET) "" else AD_REGEX_PRESET)
                    },
                    label = { Text("运营/广告正则") },
                )
                FilterChip(
                    selected = hideSystemApps,
                    onClick = { onHideSystemChange(!hideSystemApps) },
                    leadingIcon = if (hideSystemApps) {
                        { Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    label = { Text("隐藏系统应用") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val enabledInFilter = filtered.count { it.importance > 0 && !it.forceBlocked }
                Text(
                    text = "${groupedApps.size} 个应用 · ${filtered.size} 个渠道（开启 $enabledInFilter）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = { batchConfirmEnable = true },
                    enabled = filtered.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("批量开", style = MaterialTheme.typography.labelMediumEmphasized)
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = { batchConfirmEnable = false },
                    enabled = filtered.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("批量关", style = MaterialTheme.typography.labelMediumEmphasized)
                }
            }
        }

        if (state.loadingChannels && state.channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (groupedApps.isEmpty()) {
                    item(key = "empty_channels") {
                        EmptyStateCard(
                            icon = Icons.Rounded.NotificationsOff,
                            text = if (!state.hookActive) "模块未激活或尚未加载应用" else "没有匹配的应用或通知渠道",
                        )
                    }
                } else {
                    val isFiltered = query.isNotBlank() || scopeFilter != ChannelScopeFilter.ALL
                    itemsIndexed(
                        items = groupedApps,
                        key = { _, group -> group.packageName },
                    ) { idx, group ->
                        SegmentedListItem(
                            onClick = { onOpenDetail(group.packageName, group.appName) },
                            onLongClick = { longPressedGroup = group },
                            shapes = ListItemDefaults.segmentedShapes(index = idx, count = groupedApps.size),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            leadingContent = {
                                DrawableIcon(group.icon, modifier = Modifier.size(40.dp))
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    StatusPill(
                                        text = "${group.enabledCount}/${group.channels.size} 开",
                                        active = group.enabledCount > 0,
                                    )
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = group.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val statParts = buildList {
                                        add("${group.channels.size} 个渠道")
                                        if (group.blockedCount > 0) add("强拦 ${group.blockedCount}")
                                        if (group.historyTotal > 0) add("历史 ${group.historyTotal} 条")
                                    }
                                    Text(
                                        text = statParts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isFiltered) {
                                        val preview = group.channels.take(3).joinToString("、") {
                                            it.channelName.ifBlank { it.channelId }
                                        } + if (group.channels.size > 3) " 等" else ""
                                        Text(
                                            text = "命中：$preview",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            content = {
                                Text(
                                    text = group.appName,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    batchConfirmEnable?.let { enable ->
        val targets = filtered.filter { if (enable) it.importance == 0 else it.importance > 0 }
        val appCount = targets.mapTo(HashSet()) { it.packageName }.size
        AlertDialog(
            onDismissRequest = { batchConfirmEnable = null },
            title = { Text(if (enable) "批量开启渠道" else "批量关闭渠道") },
            text = {
                Text(
                    if (targets.isEmpty()) {
                        "当前筛选结果中没有可变更的渠道。"
                    } else {
                        "确定要${if (enable) "开启" else "关闭"}当前筛选出的 ${targets.size} 个通知渠道（涉及 $appCount 个应用）吗？"
                    }
                )
            },
            confirmButton = {
                if (targets.isNotEmpty()) {
                    Button(
                        onClick = {
                            onBatchSetImportance(targets, enable)
                            batchConfirmEnable = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { batchConfirmEnable = null }) {
                    Text("取消")
                }
            },
        )
    }

    longPressedGroup?.let { group ->
        val toDisable = group.channels.filter { it.importance > 0 }
        val toEnable = group.channels.filter { it.importance == 0 }
        AlertDialog(
            onDismissRequest = { longPressedGroup = null },
            title = { Text(group.appName) },
            text = {
                Text("包名：${group.packageName}\n当前显示 ${group.channels.size} 个渠道（开启 ${group.enabledCount} 个）。")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (toEnable.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                onBatchSetImportance(toEnable, true)
                                longPressedGroup = null
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("全开 (${toEnable.size})")
                        }
                    }
                    if (toDisable.isNotEmpty()) {
                        Button(
                            onClick = {
                                onBatchSetImportance(toDisable, false)
                                longPressedGroup = null
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text("全关 (${toDisable.size})")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        longPressedGroup = null
                        onOpenDetail(group.packageName, group.appName)
                    },
                ) {
                    Text("查看详情")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryTab(
    state: MainUiState,
    subTab: HistorySubTab,
    onSubTabChange: (HistorySubTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleChannel: (pkg: String, channelId: String, importance: Int, forceBlocked: Boolean) -> Unit,
    onSetForceBlock: (pkg: String, channelId: String, enabled: Boolean) -> Unit,
    onOpenDetail: (pkg: String, appName: String) -> Unit,
    onToggleIgnoreApp: (pkg: String, ignored: Boolean) -> Unit,
    onClearAppHistory: (pkg: String) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onOpenClearAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    val detailTimeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val shortTimeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    var selectedRecord by remember { mutableStateOf<TimelineEntry?>(null) }
    var longPressedApp by remember { mutableStateOf<AppHistorySummary?>(null) }

    val regex = remember(query) {
        val t = query.trim()
        if (t.isEmpty()) null
        else runCatching { Regex(t, RegexOption.IGNORE_CASE) }
            .getOrElse { Regex(Regex.escape(t), RegexOption.IGNORE_CASE) }
    }

    val filteredTimeline = remember(state.timeline, regex) {
        if (regex == null) state.timeline
        else state.timeline.filter { item ->
            regex.containsMatchIn(item.appName) ||
                regex.containsMatchIn(item.record.pkg) ||
                regex.containsMatchIn(item.liveChannelName) ||
                regex.containsMatchIn(item.record.channelName) ||
                regex.containsMatchIn(item.record.channelId) ||
                regex.containsMatchIn(item.record.title) ||
                regex.containsMatchIn(item.record.text)
        }
    }

    val filteredApps = remember(state.appSummaries, state.timeline, regex) {
        if (regex == null) state.appSummaries
        else {
            val hitPkgs = state.timeline.asSequence()
                .filter {
                    regex.containsMatchIn(it.liveChannelName) ||
                        regex.containsMatchIn(it.record.channelId) ||
                        regex.containsMatchIn(it.record.title) ||
                        regex.containsMatchIn(it.record.text)
                }
                .map { it.record.pkg }
                .toSet()
            state.appSummaries.filter { app ->
                regex.containsMatchIn(app.appName) || regex.containsMatchIn(app.pkg) || app.pkg in hitPkgs
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectedToggleRow(
                items = HistorySubTab.entries,
                isSelected = { it == subTab },
                label = { it.label },
                onSelect = onSubTabChange,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Md3eSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "搜索：标题 / 正文 / 软件 / 渠道",
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onOpenClearAll,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清空", maxLines = 1, softWrap = false)
                }
            }
            Text(
                text = "自动清理过期记录（无后台，打开即清）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectedToggleRow(
                items = listOf(
                    1 to "1天前",
                    7 to "7天前",
                    30 to "30天前",
                    0 to "不清理",
                ),
                isSelected = { state.retentionDays == it.first },
                label = { it.second },
                onSelect = { onSetRetentionDays(it.first) },
            )
        }

        if (state.loadingHistory && state.timeline.isEmpty() && state.appSummaries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                if (subTab == HistorySubTab.TIMELINE) {
                    if (filteredTimeline.isEmpty()) {
                        item(key = "empty_timeline") {
                            EmptyStateCard(
                                icon = Icons.Rounded.History,
                                text = "暂无历史消息\n系统框架 Hook 激活后，收到的通知会自动在此记录",
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredTimeline,
                            key = { _, it -> "tl_${it.record.id}_${it.record.postTime}" },
                        ) { idx, item ->
                            val rec = item.record
                            val chLabel = item.liveChannelName.ifBlank { rec.channelName }.ifBlank { rec.channelId }
                            val subSuffix = if (rec.subText.isNotBlank()) " · ${rec.subText}" else ""
                            SegmentedListItem(
                                onClick = { selectedRecord = item },
                                shapes = ListItemDefaults.segmentedShapes(index = idx, count = filteredTimeline.size),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                leadingContent = {
                                    DrawableIcon(item.icon, modifier = Modifier.size(36.dp))
                                },
                                trailingContent = {
                                    ChannelStatusPill(
                                        importance = item.importance,
                                        forceBlocked = item.forceBlocked,
                                        forceBlockGlobal = state.forceBlockEnabled,
                                        onClick = {
                                            onToggleChannel(rec.pkg, rec.channelId, item.importance, item.forceBlocked)
                                        },
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "${detailTimeFmt.format(Date(rec.postTime))} · ${rec.channelId}$subSuffix",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (rec.title.isNotBlank()) {
                                            Text(
                                                text = rec.title,
                                                style = MaterialTheme.typography.labelLargeEmphasized,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = rec.text.ifBlank { "(空内容通知)" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                content = {
                                    Text(
                                        text = "${item.appName} · $chLabel",
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                } else {
                    if (filteredApps.isEmpty()) {
                        item(key = "empty_apps") {
                            EmptyStateCard(
                                icon = Icons.Rounded.Apps,
                                text = "暂无按软件汇总的记录",
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredApps,
                            key = { _, it -> "app_${it.pkg}" },
                        ) { idx, app ->
                            SegmentedListItem(
                                onClick = { onOpenDetail(app.pkg, app.appName) },
                                onLongClick = { longPressedApp = app },
                                shapes = ListItemDefaults.segmentedShapes(index = idx, count = filteredApps.size),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                leadingContent = {
                                    DrawableIcon(state.appIcons[app.pkg], modifier = Modifier.size(40.dp))
                                },
                                trailingContent = {
                                    if (app.isIgnored) {
                                        StatusPill(text = "已忽略", active = false)
                                    } else {
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = app.pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${app.channelCount} 个渠道 · ${app.totalCount} 条 · ${shortTimeFmt.format(Date(app.lastPostTime))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                content = {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedRecord?.let { item ->
        val rec = item.record
        val chDisplay = item.liveChannelName.ifBlank { rec.channelName }.ifBlank { rec.channelId }
        val currentStateItem = state.timeline.firstOrNull {
            it.record.pkg == rec.pkg && it.record.channelId == rec.channelId
        } ?: item
        AlertDialog(
            onDismissRequest = { selectedRecord = null },
            title = { Text("${item.appName} · $chDisplay") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "包名：${rec.pkg}\n渠道 ID：${rec.channelId}\n时间：${detailTimeFmt.format(Date(rec.postTime))}" +
                            if (rec.subText.isNotBlank()) "\n标记：${rec.subText}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text(
                        text = rec.title.ifBlank { "(无标题)" },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        text = rec.text.ifBlank { "(无正文)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onSetForceBlock(rec.pkg, rec.channelId, !currentStateItem.forceBlocked)
                            selectedRecord = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(if (currentStateItem.forceBlocked) "取消强拦截" else "强拦截")
                    }
                    Button(
                        onClick = {
                            onToggleChannel(rec.pkg, rec.channelId, currentStateItem.importance, currentStateItem.forceBlocked)
                            selectedRecord = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(if (currentStateItem.importance == NotificationManager.IMPORTANCE_NONE) "开启渠道" else "关闭渠道")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedRecord = null
                        onOpenDetail(rec.pkg, item.appName)
                    },
                ) {
                    Text("软件全部渠道")
                }
            },
        )
    }

    longPressedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { longPressedApp = null },
            title = { Text(app.appName) },
            text = { Text(app.pkg) },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleIgnoreApp(app.pkg, !app.isIgnored)
                        longPressedApp = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (app.isIgnored) "恢复记录" else "停止记录")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onClearAppHistory(app.pkg)
                        longPressedApp = null
                    },
                ) {
                    Text("清空记录")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppDetailPage(
    detail: AppDetailState?,
    fallbackPkg: String,
    fallbackAppName: String,
    forceBlockGlobal: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleIgnore: (Boolean) -> Unit,
    onToggleChannel: (ChannelHistorySummary) -> Unit,
    onSetForceBlock: (channelId: String, enabled: Boolean) -> Unit,
    onLoadChannelRecords: (pkg: String, channelId: String, onLoaded: (List<NotiRecord>) -> Unit) -> Unit,
    onAddContentRule: (channelId: String, pattern: String) -> Unit,
    onToggleContentRule: (ruleId: Long, enabled: Boolean) -> Unit,
    onDeleteContentRule: (ruleId: Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fullTimeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val shortTimeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    var dialogChannel by remember { mutableStateOf<ChannelHistorySummary?>(null) }
    var dialogRecords by remember { mutableStateOf<List<NotiRecord>>(emptyList()) }
    var addRuleChannelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        detail?.icon?.let {
                            DrawableIcon(it, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = detail?.appName ?: fallbackAppName,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = detail?.pkg ?: fallbackPkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    val ignored = detail?.isIgnored == true
                    FilterChip(
                        selected = ignored,
                        onClick = { onToggleIgnore(!ignored) },
                        modifier = Modifier.padding(end = 12.dp),
                        label = { Text(if (ignored) "已忽略" else "忽略记录") },
                    )
                },
            )
        },
    ) { padding ->
        if (detail == null || detail.loading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "content_rules_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "内容正则拦截规则 (${detail.contentRules.size})",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                            Text(
                                text = "按标题/正文正则在入队前拦截，适合混杂推送与正常通知的渠道",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = { addRuleChannelId = "" },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加规则", style = MaterialTheme.typography.labelMediumEmphasized)
                        }
                    }
                }

                if (detail.contentRules.isNotEmpty()) {
                    itemsIndexed(
                        items = detail.contentRules,
                        key = { _, rule -> "rule_${rule.id}" },
                    ) { idx, rule ->
                        val scopeName = if (rule.channelId.isBlank()) {
                            "全部渠道"
                        } else {
                            detail.channels.firstOrNull { it.channelId == rule.channelId }
                                ?.channelName?.ifBlank { rule.channelId } ?: rule.channelId
                        }
                        SegmentedListItem(
                            onClick = { onToggleContentRule(rule.id, !rule.enabled) },
                            shapes = ListItemDefaults.segmentedShapes(index = idx, count = detail.contentRules.size),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { onToggleContentRule(rule.id, it) },
                                    )
                                    IconButton(onClick = { onDeleteContentRule(rule.id) }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除规则",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = "作用渠道：$scopeName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            content = {
                                Text(
                                    text = rule.pattern,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                item(key = "channels_section_header") {
                    Text(
                        text = "通知渠道 (${detail.channels.size})",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }

                if (detail.channels.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.NotificationsOff,
                            text = "该应用暂无通知渠道或记录",
                        )
                    }
                } else {
                    itemsIndexed(
                        items = detail.channels,
                        key = { _, ch -> "${ch.pkg}|${ch.channelId}" },
                    ) { idx, ch ->
                        val groupLabel = ch.channelGroup.ifBlank { "无分组" }
                        val stats = if (ch.count > 0) {
                            "共 ${ch.count} 条 · 最近 ${shortTimeFmt.format(Date(ch.lastPostTime))}"
                        } else {
                            "暂无发送记录"
                        }
                        SegmentedListItem(
                            onClick = {
                                dialogChannel = ch
                                dialogRecords = ch.samples
                                onLoadChannelRecords(ch.pkg, ch.channelId) { loaded ->
                                    dialogRecords = loaded
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = idx, count = detail.channels.size),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            trailingContent = {
                                ChannelStatusPill(
                                    importance = ch.importance,
                                    forceBlocked = ch.forceBlocked,
                                    forceBlockGlobal = forceBlockGlobal,
                                    onClick = { onToggleChannel(ch) },
                                )
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "$groupLabel · ${ch.channelId}\n$stats",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (ch.samples.isNotEmpty()) {
                                        val preview = ch.samples.take(3).joinToString("\n") { rec ->
                                            val line = listOf(rec.title, rec.text).filter { it.isNotBlank() }.joinToString("：")
                                            "• ${line.ifBlank { "(空内容通知)" }}"
                                        }
                                        Text(
                                            text = preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            content = {
                                Text(
                                    text = ch.channelName.ifBlank { ch.channelId.ifBlank { "未命名渠道" } },
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    dialogChannel?.let { ch ->
        val currentCh = detail?.channels?.firstOrNull { it.channelId == ch.channelId } ?: ch
        AlertDialog(
            onDismissRequest = { dialogChannel = null },
            title = { Text("${currentCh.channelName.ifBlank { currentCh.channelId }}\n(${currentCh.channelId})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dialogRecords.isEmpty()) {
                        Text("暂无历史内容样本", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        dialogRecords.forEachIndexed { idx, rec ->
                            if (idx > 0) HorizontalDivider()
                            val tag = if (rec.subText.isNotBlank()) " [${rec.subText}]" else ""
                            Text(
                                text = "${fullTimeFmt.format(Date(rec.postTime))}$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = rec.title.ifBlank { "(无标题)" },
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                            Text(
                                text = rec.text.ifBlank { "(无正文)" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            addRuleChannelId = currentCh.channelId
                            dialogChannel = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("内容规则", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            onSetForceBlock(currentCh.channelId, !currentCh.forceBlocked)
                            dialogChannel = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(if (currentCh.forceBlocked) "取消强拦" else "强拦截", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            onToggleChannel(currentCh)
                            dialogChannel = null
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(if (currentCh.importance == NotificationManager.IMPORTANCE_NONE) "开启" else "关闭", maxLines = 1)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogChannel = null }) {
                    Text("关闭")
                }
            },
        )
    }

    addRuleChannelId?.let { initialChId ->
        AddContentRuleDialog(
            channels = detail?.channels.orEmpty(),
            initialChannelId = initialChId,
            onDismiss = { addRuleChannelId = null },
            onSave = { chId, pattern ->
                onAddContentRule(chId, pattern)
                addRuleChannelId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddContentRuleDialog(
    channels: List<ChannelHistorySummary>,
    initialChannelId: String,
    onDismiss: () -> Unit,
    onSave: (channelId: String, pattern: String) -> Unit,
) {
    var pattern by remember { mutableStateOf("") }
    var selectedChannelId by remember { mutableStateOf(initialChannelId) }
    val regexValid = remember(pattern) {
        pattern.isNotBlank() && runCatching { Regex(pattern.trim(), RegexOption.IGNORE_CASE) }.isSuccess
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加内容正则拦截规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "忽略大小写匹配通知标题或正文，命中后直接丢弃入队并记为 [内容规则拦截]。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("正则表达式") },
                    placeholder = { Text("如：前程无忧|51Job|招聘|优惠券") },
                    isError = pattern.isNotBlank() && !regexValid,
                    supportingText = if (pattern.isNotBlank() && !regexValid) {
                        { Text("正则表达式语法无效") }
                    } else null,
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("作用范围", style = MaterialTheme.typography.labelMediumEmphasized)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedChannelId.isBlank(),
                        onClick = { selectedChannelId = "" },
                        label = { Text("全部渠道") },
                    )
                    channels.forEach { ch ->
                        val label = ch.channelName.ifBlank { ch.channelId }.take(18)
                        FilterChip(
                            selected = selectedChannelId == ch.channelId,
                            onClick = { selectedChannelId = ch.channelId },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedChannelId, pattern.trim()) },
                enabled = regexValid,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("保存并启用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelStatusPill(
    importance: Int,
    forceBlocked: Boolean,
    forceBlockGlobal: Boolean,
    onClick: () -> Unit,
) {
    val (bg, fg, icon, label) = when {
        forceBlocked && forceBlockGlobal -> Quadruple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            Icons.Rounded.Shield,
            "已强拦截",
        )
        forceBlocked -> Quadruple(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary,
            Icons.Rounded.Shield,
            "强拦截(总关)",
        )
        importance == NotificationManager.IMPORTANCE_NONE -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.error,
            Icons.Rounded.Block,
            "已关闭",
        )
        importance > NotificationManager.IMPORTANCE_NONE -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.Check,
            "已开启",
        )
        else -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.WarningAmber,
            "未知",
        )
    }
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun EmptyStateCard(icon: ImageVector, text: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportRulesDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴规则 JSON 批量应用") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                placeholder = { Text("[{\"pkg\":\"...\",\"channelId\":\"...\",\"action\":\"disable\"}]") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onApply(text) },
                enabled = text.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotiChannelAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appIconBitmap = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo)
            .toBitmap(width = 144, height = 144)
            .asImageBitmap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于模块") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "基于 GSWXXN/XMSFNotiChannel 二次开发，采用 system_server 被动历史采集、全渠道正则筛选与可选入队强拦截。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            onClick = {
                                onDismiss()
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FORK_REPO_URL)))
                                }
                            },
                            onLongClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Repo", FORK_REPO_URL))
                                Toast.makeText(context, "已复制仓库地址", Toast.LENGTH_SHORT).show()
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开源仓库", style = MaterialTheme.typography.labelLargeEmphasized)
                            Text(
                                text = FORK_REPO_URL,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlobalToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Md3eSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            }
        },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> PopupFilterButton(
    selectedLabel: String,
    icon: ImageVector,
    items: List<T>,
    itemLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    showTrailingChevron: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            contentPadding = contentPadding,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(selectedLabel, maxLines = 1)
            if (showTrailingChevron) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    leadingIcon = {
                        if (isSelected(item)) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedToggleRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.forEachIndexed { idx, item ->
            val shapes = when {
                items.size <= 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                idx == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                idx == items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = isSelected(item),
                onCheckedChange = { onSelect(item) },
                modifier = Modifier.weight(1f),
                shapes = shapes,
                colors = toggleColors,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusPill(text: String, active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        return
    }
    val bitmap = remember(drawable) { drawable.toBitmap(width = 96, height = 96).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.clip(MaterialTheme.shapes.medium),
    )
}
