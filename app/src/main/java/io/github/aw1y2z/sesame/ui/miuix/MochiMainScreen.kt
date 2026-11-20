package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelFields
import io.github.aw1y2z.sesame.data.ModelGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.Agreement
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.miuix.tabs.ConfigTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.HomeTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.LogsTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.SettingsTab
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesamePermissionNotice
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.ui.theme.sesameFlingBehavior
import io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant
import io.github.aw1y2z.sesame.ui.theme.sesamePrimary
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabBarDefaults
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState

/**
 * 方案 A（彻底 mochi 风格）的 MD 主界面，按官方 MochiAppStarter 骨架实现：
 *
 * - `MochiNavHost` 管页面栈（根页 main ↔ 关于页 about），支持卡片一镜到底 / 侧推 / 返回手势；
 * - 根页用 `MochiTabScaffold` 承载 4 个一级 Tab（自带 iOS 27 底栏 + 景深切换 + 左右滑）；
 * - 4 个 Tab 直接复用现有 composable（HomeTab/LogsTab/ConfigTab/SettingsTab），它们自带
 *   verticalScroll；MochiTabScaffold 的 content 不额外包滚动，不会双重滚动。
 *
 * 本文件只被 MD 风格使用；MIUIX 仍走原 `MainScreen`（原 Activity 路径，本次不动）。
 * 页面里不写外观分支：这里全是 mochi 原生组件。
 */

private const val ROUTE_MAIN = "main"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_FRIEND_STATS = "friendStats"
private const val ROUTE_EXTENSIONS = "extensions"
private const val ROUTE_BLACKLIST = "blacklist"
private const val ROUTE_BLACKLIST_LIST = "blacklistList"
private const val ROUTE_LOG = "log"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_GROUP_FIELDS = "groupFields"
private const val ROUTE_SELECTION_EDIT = "selectionEdit"
private const val ROUTE_SEP = "|"

@Composable
fun MochiMainScreen(activity: MiuixMainActivity) {
    val nav = rememberMochiNavigator(ROUTE_MAIN)
    // 黑名单主页/明细的数据快照：从明细 pop 返回时递增，让主页重读（等价旧 Activity 的 onResume）。
    var blacklistRefresh by remember { mutableStateOf(0) }
    MochiNavHost(nav, modifier = Modifier.fillMaxSize()) { route ->
        when {
            route == ROUTE_ABOUT -> MochiAboutRoute(onBack = { nav.pop() })
            route == ROUTE_FRIEND_STATS -> FriendStatsScreen(
                onBack = { nav.pop() },
                onOpenProfile = { userId -> openFriendProfile(activity, userId) },
            )
            route == ROUTE_EXTENSIONS -> ExtensionsScreen(
                onBack = { nav.pop() },
                onSend = { type, method, data -> sendItemsBroadcast(activity, type, method, data) },
            )
            route == ROUTE_BLACKLIST -> BlacklistScreen(
                refreshTick = blacklistRefresh,
                onBack = { nav.pop() },
                onOpenList = { fieldCode, listName ->
                    nav.push("$ROUTE_BLACKLIST_LIST$ROUTE_SEP${Uri.encode(fieldCode)}$ROUTE_SEP${Uri.encode(listName)}")
                },
            )
            route.startsWith("$ROUTE_BLACKLIST_LIST$ROUTE_SEP") -> {
                val parts = route.split(ROUTE_SEP, limit = 3)
                val fieldCode = parts.getOrNull(1)?.let { Uri.decode(it) }.orEmpty()
                val listName = parts.getOrNull(2)?.let { Uri.decode(it) }.orEmpty().ifEmpty { "黑名单" }
                ListScreen(
                    fieldCode = fieldCode,
                    listName = listName,
                    onBack = { blacklistRefresh++; nav.pop() },
                )
            }
            route.startsWith("$ROUTE_LOG$ROUTE_SEP") -> {
                val name = route.removePrefix("$ROUTE_LOG$ROUTE_SEP")
                val logType = LogType.fromName(name)
                LogScreen(
                    logType = logType,
                    isActive = { true }, // MochiNavHost 在页面离开时取消 LaunchedEffect，无需手动判前台
                    onBack = { nav.pop() },
                    onShare = { shareIntent -> activity.startActivity(Intent.createChooser(shareIntent, "分享日志")) },
                )
            }
            route.startsWith("$ROUTE_SETTINGS$ROUTE_SEP") -> {
                val uid = route.removePrefix("$ROUTE_SETTINGS$ROUTE_SEP").let { Uri.decode(it) }.ifEmpty { null }
                SettingsRoute(
                    activity = activity,
                    userId = uid,
                    onBack = { nav.pop() },
                    onOpenFieldHit = { hit ->
                        if (hit.field.type in SELECTION_FIELD_TYPES_FOR_NAV) {
                            nav.push(
                                "$ROUTE_SELECTION_EDIT$ROUTE_SEP${Uri.encode(uid ?: "")}$ROUTE_SEP" +
                                    "${Uri.encode(hit.groupCode)}$ROUTE_SEP${Uri.encode(hit.field.code)}$ROUTE_SEP" +
                                    Uri.encode(hit.modelCode)
                            )
                        } else {
                            nav.push(
                                "$ROUTE_GROUP_FIELDS$ROUTE_SEP${Uri.encode(uid ?: "")}$ROUTE_SEP" +
                                    "${Uri.encode(hit.groupCode)}$ROUTE_SEP${Uri.encode(hit.field.name ?: hit.field.code)}"
                            )
                        }
                    },
                    onOpenGroup = { groupCode ->
                        nav.push("$ROUTE_GROUP_FIELDS$ROUTE_SEP${Uri.encode(uid ?: "")}$ROUTE_SEP${Uri.encode(groupCode)}$ROUTE_SEP")
                    },
                )
            }
            route.startsWith("$ROUTE_GROUP_FIELDS$ROUTE_SEP") -> {
                val parts = route.split(ROUTE_SEP, limit = 4)
                val uid = parts.getOrNull(1)?.let { Uri.decode(it) }?.ifEmpty { null }
                val groupCode = parts.getOrNull(2)?.let { Uri.decode(it) }.orEmpty()
                val initialQuery = parts.getOrNull(3)?.let { Uri.decode(it) }?.ifEmpty { null }
                GroupFieldsRoute(
                    userId = uid,
                    groupCode = groupCode,
                    initialQuery = initialQuery,
                    onBack = { nav.pop() },
                    onOpenSelection = { fieldCode, modelCode ->
                        nav.push(
                            "$ROUTE_SELECTION_EDIT$ROUTE_SEP${Uri.encode(uid ?: "")}$ROUTE_SEP" +
                                "${Uri.encode(groupCode)}$ROUTE_SEP${Uri.encode(fieldCode)}$ROUTE_SEP" +
                                Uri.encode(modelCode)
                        )
                    },
                )
            }
            route.startsWith("$ROUTE_SELECTION_EDIT$ROUTE_SEP") -> {
                val parts = route.split(ROUTE_SEP, limit = 5)
                val uid = parts.getOrNull(1)?.let { Uri.decode(it) }?.ifEmpty { null }
                val fieldCode = parts.getOrNull(3)?.let { Uri.decode(it) }.orEmpty()
                val modelCode = parts.getOrNull(4)?.let { Uri.decode(it) }.orEmpty()
                SelectionEditRoute(
                    userId = uid,
                    fieldCode = fieldCode,
                    modelCode = modelCode,
                    onBack = { nav.pop() },
                )
            }
            else -> MochiHomeRoute(
                activity,
                onOpenAbout = { nav.push(ROUTE_ABOUT) },
                onOpenFriendStats = { nav.push(ROUTE_FRIEND_STATS) },
                onOpenExtensions = { nav.push(ROUTE_EXTENSIONS) },
                onOpenBlacklist = { nav.push(ROUTE_BLACKLIST) },
                onOpenLog = { logType -> nav.push("$ROUTE_LOG$ROUTE_SEP${logType.name}") },
                onOpenSettings = { uid -> nav.push("$ROUTE_SETTINGS$ROUTE_SEP${Uri.encode(uid ?: "")}") },
            )
        }
    }
}

/** 根页：4 个一级 Tab，自带底栏 + 景深切换 + 左右滑动。 */
@Composable
private fun MochiHomeRoute(
    activity: MiuixMainActivity,
    onOpenAbout: () -> Unit,
    onOpenFriendStats: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenLog: (LogType) -> Unit,
    onOpenSettings: (userId: String?) -> Unit,
) {
    val tabs = remember {
        persistentListOf(
            mochiTab("首页", Icons.Filled.Home),
            mochiTab("日志", Icons.Filled.Description),
            mochiTab("配置", Icons.Filled.Tune),
            mochiTab("设置", Icons.Filled.Settings),
        )
    }
    val tabState = rememberMochiTabScaffoldState()
    // 🔴 MochiTabScaffold 的 content 不自带顶部状态栏留白：库源码里 content(page) 是裸调用的，
    // 只处理底部导航条 insets。方案A之前旧 MD 主页用 SesamePageTitleBar（自带 statusBarsPadding），
    // 切到 mochi 后这层丢了 → 第一个分组标题顶到 y=0、被系统状态栏压住（K40 实测标题 bounds [44,0]）。
    // 照 mochi demo CatalogGrid 的规范补回：top = 状态栏高度 + 12dp 呼吸。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
    MochiTabScaffold(
        items = tabs,
        state = tabState,
        collapseOnScroll = false,
        behavior = MochiTabBarDefaults.iconOnlyBehavior(selectedLabelOnly = true),
        // 对齐旧 MainScreen：切回首页时刷新统计（首页 Tab 未被导航栈覆盖时仍会触发）。
        onSelect = { index -> if (index == 0) activity.refreshStatistics() },
    ) { page ->
        // MochiTabScaffold 的 content 不自带滚动，这里统一包可滚动 Column；
        // 每个 page 有独立 rememberScrollState，MochiTabScaffold 的 SaveableStateHolder
        // 会随页面切换保留各自的滚动位置。fling 用项目桥接（MD 走 mochi 慢停曲线）。
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), flingBehavior = sesameFlingBehavior())
                .padding(start = 16.dp, end = 16.dp, top = statusBarTop)
                // 给悬浮底栏留白，用库提供的标准值（底栏高 + 手势区 + 间隙）。
                .padding(bottom = MochiTabScaffoldDefaults.contentBottomPadding())
        ) {
            when (page) {
                0 -> {
                    HomeTab(activity)
                    // 无存储权限时提示（与旧 MainScreen 一致，只首页显示，避免四页各一条噪音）
                    if (!activity.hasPermission) {
                        SesamePermissionNotice(
                            onClick = { PermissionUtil.checkOrRequestFilePermissions(activity) }
                        )
                    }
                }
                1 -> LogsTab(activity, onOpenLog = onOpenLog)
                2 -> ConfigTab(activity, onOpenSettings = onOpenSettings)
                3 -> SettingsTab(
                    activity = activity,
                    onOpenAbout = onOpenAbout,
                    onOpenFriendStats = onOpenFriendStats,
                    onOpenExtensions = onOpenExtensions,
                    onOpenBlacklist = onOpenBlacklist,
                )
            }
        }
    }
}

private fun mochiTab(label: String, icon: ImageVector) = MochiTabItem(
    label = label,
    icon = { tint -> Icon(icon, contentDescription = label, tint = tint) },
    selectedIcon = { tint -> Icon(icon, contentDescription = label, tint = tint) },
)

/** 关于页（mochi route 版）：用 MochiBackButton 返回，不依赖 Activity.finish()。 */
@Composable
private fun MochiAboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    MochiLargeTitleScaffold(
        title = "关于",
        navigationIcon = { MochiBackButton(onClick = onBack) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(sesamePrimary())
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            }
            Spacer(Modifier.height(16.dp))
            SesameText(text = ViewAppInfo.getAppTitle(), fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            SesameText(
                text = "版本: ${ViewAppInfo.getAppVersion()}",
                fontSize = 13.sp,
                color = sesameOnSurfaceVariant(),
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                SesameCardGroup {
                    SesameClickRow(
                        title = "在 GitHub 查看源码",
                        onClick = { openWebUrl(context, "https://github.com/aw1y2z/Sesame-M") },
                    )
                    SesameClickRow(
                        title = "用户协议",
                        summary = "条款版本 ${Agreement.VERSION} · " +
                            (Agreement.acceptedAtText()?.let { "同意于 $it" } ?: "未记录同意时间"),
                        onClick = { openWebUrl(context, TERMS_URL) },
                    )
                }
            }
        }
    }
}

private const val TERMS_URL =
    "https://github.com/aw1y2z/Sesame-M/blob/main/docs/%E5%90%88%E8%A7%84%E4%B8%8E%E5%85%8D%E8%B4%A3.md"

private fun openWebUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        io.github.aw1y2z.sesame.util.ToastUtil.show(context, "无法打开链接")
    }
}

/** 选择型字段类型：搜索命中时决定跳四级编辑页还是三级字段页（与设置页 openFieldHit 保持一致）。 */
private val SELECTION_FIELD_TYPES_FOR_NAV =
    setOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE")

/**
 * 配置页 route（MD）。
 *
 * 【初始化】原 Activity.onCreate 里做 `Model.initAllModel() + ConfigPreload.prepare(uid)`（破坏性重建注册表再灌配置），
 * 这里在进入 route 时做一次（remember(uid) 保证同一次进入只做一次；uid 变化如切账号会重做）。
 * 【保存】字段编辑直接写 ConfigV2 单例，退出落盘由 onDispose 兜底（覆盖系统返回键/右滑手势），
 * 顶栏返回按钮走 onBack→nav.pop() 同样触发 onDispose；两条路径幂等（save 内部有 hasFieldChanges 短路）。
 * 【recreate】导入/恢复配置后需重建以刷新界面与主题，单 Activity 下用 activity.recreate() 等价实现。
 */
@Composable
private fun SettingsRoute(
    activity: MiuixMainActivity,
    userId: String?,
    onBack: () -> Unit,
    onOpenFieldHit: (FieldHit) -> Unit,
    onOpenGroup: (groupCode: String) -> Unit,
) {
    // 进入配置页时重建注册表 + 灌入该账号配置（等价旧 Activity.onCreate 的前两步）。
    remember(userId) {
        Model.initAllModel()
        ConfigPreload.prepare(userId)
    }
    // 保存函数：先放空实现，SettingsContent 的 SideEffect 会注册真正的保存逻辑。
    val saveHandler = remember { mutableStateOf<() -> Unit>({}) }
    DisposableEffect(Unit) {
        onDispose { saveHandler.value() }
    }
    SettingsContent(
        userId = userId,
        onBack = onBack,
        onSaveRegister = { saveHandler.value = it },
        onOpenFieldHit = onOpenFieldHit,
        onOpenGroup = onOpenGroup,
        onReload = { activity.recreate() },
        onAccountPurged = { activity.configAccountsVersion++; onBack() },
    )
}

/** 三级配置字段页 route（MD）。字段编辑写单例，保存靠 onDispose 兜底。 */
@Composable
private fun GroupFieldsRoute(
    userId: String?,
    groupCode: String,
    initialQuery: String?,
    onBack: () -> Unit,
    onOpenSelection: (fieldCode: String, modelCode: String) -> Unit,
) {
    val saveHandler = remember { mutableStateOf<() -> Unit>({}) }
    DisposableEffect(Unit) {
        onDispose { saveHandler.value() }
    }
    val group = ModelGroup.entries.find { it.name == groupCode }
    if (group != null) {
        GroupFieldsContent(
            userId = userId,
            groupCode = groupCode,
            group = group,
            initialQuery = initialQuery,
            onBack = onBack,
            onSaveRegister = { saveHandler.value = it },
            onOpenSelection = onOpenSelection,
        )
    } else {
        SesameText(text = "分组不存在: $groupCode")
    }
}

/** 四级选填编辑页 route（MD）。勾选态在 Composable 内，保存函数由 SelectionEditContent 注册。 */
@Composable
private fun SelectionEditRoute(
    userId: String?,
    fieldCode: String,
    modelCode: String,
    onBack: () -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val field = (ConfigV2.INSTANCE.getModelFields(modelCode) as? ModelFields)?.get(fieldCode) as? ModelField<*>
    if (field != null) {
        val saveHandler = remember { mutableStateOf<() -> Unit>({}) }
        DisposableEffect(Unit) {
            onDispose { saveHandler.value() }
        }
        SelectionEditContent(
            field = field,
            modelCode = modelCode,
            userId = userId,
            onBack = onBack,
            onSaveRegister = { saveHandler.value = it },
        )
    } else {
        SesameText(text = "字段不存在: $fieldCode")
    }
}
