package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
            else -> MochiHomeRoute(
                activity,
                onOpenAbout = { nav.push(ROUTE_ABOUT) },
                onOpenFriendStats = { nav.push(ROUTE_FRIEND_STATS) },
                onOpenExtensions = { nav.push(ROUTE_EXTENSIONS) },
                onOpenBlacklist = { nav.push(ROUTE_BLACKLIST) },
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
                .padding(horizontal = 16.dp)
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
                1 -> LogsTab(activity)
                2 -> ConfigTab(activity)
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
