package io.github.aw1y2z.sesame.ui.miuix.tabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.miuix.CardColumn
import io.github.aw1y2z.sesame.ui.miuix.MiuixMainActivity
import io.github.aw1y2z.sesame.ui.theme.SesameCard
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameExpandRow
import io.github.aw1y2z.sesame.ui.theme.SesameInfoRow
import io.github.aw1y2z.sesame.ui.theme.SesameInlineEditor
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.SesameSelectRow
import io.github.aw1y2z.sesame.ui.theme.SesameStatusCard
import io.github.aw1y2z.sesame.ui.theme.SesameSwitchRow
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.UiStyle
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.Statistics.DataType
import io.github.aw1y2z.sesame.util.Statistics.TimeType
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import kotlinx.coroutines.delay
import io.github.aw1y2z.sesame.ui.miuix.LogType
import io.github.aw1y2z.sesame.ui.miuix.MiuixAboutActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixExtensionsActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixFriendStatsActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixLogViewerActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixSettingsActivity
import io.github.aw1y2z.sesame.ui.theme.SesameTheme
import io.github.aw1y2z.sesame.ui.miuix.groupIconOf

/* ───────────────────────── 一级页的四个 Tab 页面实现 ─────────────────────────
 * 这些是**页面实现**（首页 / 日志 / 配置 / 设置），原先和 Activity 本体混在一个文件里。
 * 拆出来后：Activity 只留「壳 + 系统逻辑（广播/权限/图标隐藏）」，改页面不再动 Activity 文件。
 * 页面代码只依赖 ui.theme 的风格无关组件，所以两套皮肤共用同一份实现。
 */
@Composable
fun HomeTab(activity: MiuixMainActivity) {
    // 订阅 Compose state：onServiceBind / 状态广播到达时会自动重组刷新首页状态
    val activated = activity.uiRunType == RunType.MODEL
    val version = ViewAppInfo.getAppVersion()

    SesameStatusCard(
        activated = activated,
        statusText = if (activated) "已激活" else "已关闭",
        lines = listOf(
            "$version (${io.github.aw1y2z.sesame.BuildConfig.VERSION_CODE})",
            "API 102"
        ),
        icon = Icons.Filled.CheckCircle
    )

    // 分组之间的垂直节奏由 SesameSectionTitle 自带的上下留白负责，
    // 页面不再手动插 Spacer —— 避免间距值散落在各处、改一处破一屏。
    // 标题沿用上游改后的「运行环境」：顶部状态卡已显示激活状态与版本，此处不再重复那两行。
    SesameSectionTitle(text = "运行环境")
    SesameCardGroup {
        SesameInfoRow("SDK API", Build.VERSION.SDK_INT.toString())
        SesameInfoRow("设备", MiuixMainActivity.getDeviceDisplayName())
        SesameInfoRow("系统架构", Build.SUPPORTED_ABIS?.firstOrNull() ?: "")
    }

    SesameSectionTitle(text = "数据统计")
    SesameCardGroup {
        // 统计表不是列表行，拿不到行组件自带的卡片外观，显式包一层。
        SesameCard {
            StatisticsTable(activity)
        }
    }
}

@Composable
fun StatisticsTable(activity: MiuixMainActivity) {
    // 左右补 16dp：卡片( CardColumn )自带的 16dp 之外，再补上行内边距，
    // 让表格文字与「模块状态」那些行的标签左边界对齐（都是 48dp），否则整块会贴着卡片边缘更靠左

    // 订阅 statisticsVersion：load / 广播刷新后自增,触发本表重组读取最新单例数据
    activity.statisticsVersion
    val rows = listOf(
        "收" to listOf(DataType.COLLECTED),
        "帮" to listOf(DataType.HELPED),
        "浇" to listOf(DataType.WATERED),
        "被水" to listOf(DataType.WATEREDCOUNT),
        "浇水" to listOf(DataType.WATERINGCOUNT)
    )
    val columns = listOf(TimeType.DAY, TimeType.MONTH, TimeType.YEAR)
    val headers = listOf("今日", "本月", "今年")

    Column(
        Modifier
            .fillMaxWidth()
            // 水平内边距沿用本地的自适应实现；上下留白由表头(8/4)与行(8)自带的节奏负责，
            // 不再叠加上游的 16dp —— 否则表头 16 与末行 8+16 会不对称。
            .padding(horizontal = sesameGroupHorizontalPadding())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Box(Modifier.weight(1f))
            headers.forEach { header ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SesameText(
                        text = header,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = sesameOnSurfaceVariant()
                    )
                }
            }
        }
        rows.forEach { (label, types) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    // 与 SesameInfoRow 的只读行节奏对齐（上下 8dp），
                    // 否则同一屏里两个卡片的行距不一致，看起来「一松一紧」。
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    SesameText(text = label, fontSize = 14.sp)
                }
                columns.forEach { timeType ->
                    val value = types.sumOf { Statistics.getData(timeType, it) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SesameText(text = value.toString(), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTab(activity: MiuixMainActivity) {
    // ── 分类记录：每个配置分组一个条目（森林/庄园/新村/农场/金豆/运动/会员/其他） ──
    // 8 个分组里只有 4 个有自己的日志文件（forest / farm / goldenbeans / other），
    // 另外 4 个（新村/农场 → farm.log，运动/会员 → other.log）与父项**共用文件**，
    // 拿不到独立开关（开关是按文件给的）。于是把它们渲染成父项下的**轻量子项**、
    // 只给查看入口 —— 把「共用关系」画出来，用户才不会以为这几行漏了开关。
    SesameSectionTitle(text = "分类记录")
    SesameCardGroup {
        for (parent in LogType.toggleableCategories) {
            CategoryParentRow(activity, parent)
            for (child in LogType.childrenOf(parent)) {
                CategoryViewRow(activity, child, isChild = true)
            }
        }
    }

    SesameSectionTitle(text = "系统记录")
    SesameCardGroup {
        var debug by remember { mutableStateOf(AppConfig.INSTANCE.enableDebugLog ?: false) }
        SesameSwitchRow(
            title = "抓包记录",
            icon = Icons.Outlined.WifiTethering,
            checked = debug,
            onCheckedChange = {
                debug = it
                AppConfig.INSTANCE.enableDebugLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                // 关闭时**不清空** debug 日志：抓到的包是排查证据，要清空请到日志页点「删除」
            },
            onClick = { openLog(activity, LogType.DEBUG) }
        )
        var error by remember { mutableStateOf(AppConfig.INSTANCE.enableViewErrorLog ?: true) }
        SesameSwitchRow(
            title = "查看异常日志",
            icon = Icons.Outlined.BugReport,
            checked = error,
            onCheckedChange = {
                error = it
                AppConfig.INSTANCE.enableViewErrorLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                if (!it) FileUtil.clearLog("error")
            },
            onClick = { openLog(activity, LogType.ERROR) }
        )
        var runtime by remember { mutableStateOf(AppConfig.INSTANCE.enableViewRuntimeLog ?: true) }
        SesameSwitchRow(
            title = "查看运行日志",
            icon = Icons.Outlined.Terminal,
            checked = runtime,
            onCheckedChange = {
                runtime = it
                AppConfig.INSTANCE.enableViewRuntimeLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                if (!it) FileUtil.clearLog("runtime")
            },
            onClick = { openLog(activity, LogType.RUNTIME) }
        )
    }
}

/**
 * 「分类记录」里有独立日志文件、可开关的分组行（森林 / 庄园 / 金豆 / 其他）。
 *
 * `showArrow = false`：开关本身就是行尾控件（MD3 的常规），不再并排一个箭头 ——
 * 两者并存会让行尾显得拥挤。同一张卡里的**子项**才用箭头，行的角色因此一眼可分。
 */
@Composable
private fun CategoryParentRow(activity: MiuixMainActivity, entry: LogType) {
    val toggle = categoryToggleOf(entry) ?: return
    var enabled by remember { mutableStateOf(toggle.getValue() ?: true) }
    SesameSwitchRow(
        title = entry.displayName,
        icon = groupIconOf(entry.groupCode.orEmpty()),
        checked = enabled,
        showArrow = false,
        onCheckedChange = {
            enabled = it
            toggle.setValue(it)
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog(toggle.logName)
        },
        onClick = { openLog(activity, entry) }
    )
}

/**
 * 「分类记录」里与父项共用日志文件、因而没有独立开关的分组行
 * （新村 / 农场 / 运动 / 会员）。渲染成内缩的轻量子项，只给查看入口。
 */
@Composable
private fun CategoryViewRow(activity: MiuixMainActivity, entry: LogType, isChild: Boolean) {
    SesameClickRow(
        title = entry.displayName,
        icon = groupIconOf(entry.groupCode.orEmpty()),
        isChild = isChild,
        onClick = { openLog(activity, entry) }
    )
}

/**
 * 分类记录的开关描述子。
 *
 * 只有**有独立日志文件**的分组才有开关（`AppConfig` 里也只有这 4 个日志开关）。
 * 新村/农场/运动/会员与父项**共用同一个文件**（新村/农场 → farm.log，运动/会员 → other.log），
 * 开关是按文件给的，给它们各配一个就会变成「拨一个动两行」，因此返回 null ——
 * 页面把它们渲染成父项下的缩进子项，只作查看入口（见 [LogType.categoryTree]）。
 */
private class CategoryToggle(
    /** 分类日志文件名前缀，用于关闭时按前缀清空（见 FileUtil.clearLog） */
    val logName: String,
    private val getter: () -> Boolean?,
    private val setter: (Boolean) -> Unit
) {
    fun getValue(): Boolean? = getter()
    fun setValue(value: Boolean) = setter(value)
}

/** 取该分类对应的开关；没有独立开关的分组返回 null */
private fun categoryToggleOf(logType: LogType): CategoryToggle? = when (logType) {
    LogType.FOREST -> CategoryToggle(
        "forest",
        { AppConfig.INSTANCE.enableForestLog },
        { AppConfig.INSTANCE.enableForestLog = it }
    )
    LogType.FARM -> CategoryToggle(
        "farm",
        { AppConfig.INSTANCE.enableFarmLog },
        { AppConfig.INSTANCE.enableFarmLog = it }
    )
    LogType.GOLDENBEANS -> CategoryToggle(
        "goldenbeans",
        { AppConfig.INSTANCE.enableGoldenBeansLog },
        { AppConfig.INSTANCE.enableGoldenBeansLog = it }
    )
    LogType.OTHER -> CategoryToggle(
        "other",
        { AppConfig.INSTANCE.enableOtherLog },
        { AppConfig.INSTANCE.enableOtherLog = it }
    )
    else -> null
}

/** 打开日志查看器(显示指定日志类型的全部条目) */
fun openLog(activity: MiuixMainActivity, logType: LogType) {
    try {
        activity.startActivity(
            Intent(activity, MiuixLogViewerActivity::class.java)
                .putExtra(LogType.EXTRA_LOG_TYPE, logType.name)
        )
    } catch (t: Throwable) {
        Log.printStackTrace(t)
    }
}

@Composable
fun ConfigTab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val items = remember {
        // (userId, 标题, 副标题)：标题固定为「账号N」保证单行不换行，昵称/账号放副标题
        val list = ArrayList<Triple<String?, String, String?>>()
        list.add(Triple(null, "默认", null))
        try {
            val dir = FileUtil.CONFIG_DIRECTORY_FILE
            dir.listFiles()?.forEach { configDir ->
                if (configDir.isDirectory) {
                    val userId = configDir.name
                    UserIdMap.loadSelf(userId)
                    val userEntity = UserIdMap.get(userId)
                    val label = UserIdMap.getAccountLabel(userId) ?: userId
                    // 副标题优先显示「昵称:账号」；新用户尚未被模块钩子同步资料（self.json 不存在）时
                    // 回退显示 userId 本身，避免空白且仍能区分账号
                    // 名字/账号任一缺失时跳过，不要拼出字面 "null"（宿主资料可能只同步到一半）
                    val summary = userEntity?.let { e ->
                        val name = e.showName?.takeIf { it.isNotBlank() }
                        val acct = e.account?.takeIf { it.isNotBlank() }
                        when {
                            name != null && acct != null -> "$name: $acct"
                            name != null -> name
                            acct != null -> acct
                            else -> null
                        }
                    } ?: userId
                    list.add(Triple(userId, label, summary))
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
        list
    }

    SesameSectionTitle(text = "配置管理")
    SesameCardGroup {
        items.forEach { (userId, title, summary) ->
            SesameClickRow(
                title = title,
                summary = summary,
                icon = Icons.Outlined.AccountCircle,
                onClick = {
                    val intent = Intent(context, MiuixSettingsActivity::class.java)
                    if (userId != null) intent.putExtra("userId", userId)
                    context.startActivity(intent)
                }
            )
        }
    }
    // 模块功能：全局配置（不分账号），与上面的「按账号配置」并列放在配置页更合理。
    // 上游本版新增该分区；渲染沿用本地的双风格组件集，行为/字段与上游一致。
    SesameSectionTitle(text = "模块功能")
    SesameCardGroup {
        // 这几项原先是「按账号」存在账号配置里，现改为全局配置 AppConfig（模块级，不分账号）
        var newRpc by remember { mutableStateOf(AppConfig.INSTANCE.newRpc ?: true) }
        SesameSwitchRow(
            title = "使用新接口",
            summary = "最低支持 v10.3.96.8100",
            icon = Icons.Outlined.Sync,
            checked = newRpc,
            onCheckedChange = {
                AppConfig.INSTANCE.newRpc = it
                AppConfig.save()
                newRpc = it
                // 换接口要重挂 RPC bridge，必须让注入进程整体重启（只重载配置不够）
                activity.broadcastRestart()
            }
        )
        var showToast by remember { mutableStateOf(AppConfig.INSTANCE.showToast ?: true) }
        SesameSwitchRow(
            title = "气泡提示",
            icon = Icons.Outlined.ChatBubbleOutline,
            checked = showToast,
            onCheckedChange = {
                AppConfig.INSTANCE.showToast = it
                AppConfig.save()
                showToast = it
                activity.broadcastReloadConfig()
            }
        )
        // 气泡纵向偏移：一级界面没有整数控件，用可展开行 + 行内输入框
        var toastOffsetY by remember { mutableStateOf((AppConfig.INSTANCE.toastOffsetY ?: 0).toString()) }
        var offsetExpanded by remember { mutableStateOf(false) }
        SesameExpandRow(
            title = "气泡纵向偏移",
            summary = "${if (toastOffsetY.isEmpty()) "0" else toastOffsetY} px（正数向下）",
            expandable = true,
            icon = Icons.Outlined.SwapVert,
            onClick = { offsetExpanded = !offsetExpanded }
        )
        if (offsetExpanded) {
            SesameInlineEditor(
                value = toastOffsetY,
                onValueChange = { text ->
                    // 只接受整数（允许开头一个负号）
                    toastOffsetY = text.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                },
                isError = toastOffsetY.isNotEmpty() && toastOffsetY.toIntOrNull() == null,
                supportingText = "单位为像素，正数向下、负数向上"
            )
        }
        // 输入即生效：值先落内存，停手 300ms 后统一写盘并通知宿主。
        // 不在每次按键里直接 save()——那是「全量序列化 + 写盘 + 备份检查」，
        // 紧随其后的广播还会打断输入；debounce 之后一段连续输入通常只落盘一次。
        LaunchedEffect(toastOffsetY) {
            val parsed = toastOffsetY.toIntOrNull() ?: return@LaunchedEffect
            if (parsed == AppConfig.INSTANCE.toastOffsetY) return@LaunchedEffect
            delay(300)
            AppConfig.INSTANCE.toastOffsetY = parsed
            AppConfig.save()
            activity.broadcastReloadConfig()
        }
        var enableOnGoing by remember { mutableStateOf(AppConfig.INSTANCE.enableOnGoing ?: false) }
        SesameSwitchRow(
            title = "开启状态栏禁删",
            icon = Icons.Outlined.Security,
            checked = enableOnGoing,
            onCheckedChange = {
                AppConfig.INSTANCE.enableOnGoing = it
                AppConfig.save()
                enableOnGoing = it
                activity.broadcastReloadConfig()
            }
        )
        var closeCaptchaDialog by remember { mutableStateOf(AppConfig.INSTANCE.closeCaptchaDialog ?: true) }
        SesameSwitchRow(
            title = "屏蔽部分弹窗",
            icon = Icons.Outlined.Block,
            checked = closeCaptchaDialog,
            onCheckedChange = {
                AppConfig.INSTANCE.closeCaptchaDialog = it
                AppConfig.save()
                closeCaptchaDialog = it
                activity.broadcastReloadConfig()
            }
        )
    }
}

/**
 * 「主题样式」的三个取值。索引与 [themeModeIndex] / [themeModeFlags] 严格对应：
 * 0 = 跟随系统，1 = 浅色，2 = 深色。
 */
private val THEME_MODE_LABELS = listOf("跟随系统", "浅色", "深色")

/** 由「跟随系统」+「深色模式」两个既有配置推导出主题样式的下标 */
private fun themeModeIndex(followSystem: Boolean, darkMode: Boolean): Int = when {
    followSystem -> 0
    darkMode -> 2
    else -> 1
}

/**
 * 把主题样式的下标还原成两个配置项。
 *
 * [currentDarkMode] 只在选「跟随系统」时用到：此时深色模式不参与取色
 * （见 SesameTheme.sesameIsDark），保留原值可以让用户「跟随系统 → 深色」切回来时
 * 回到自己上次选的那档，而不是被重置成浅色。
 */
private fun themeModeFlags(index: Int, currentDarkMode: Boolean): Pair<Boolean, Boolean> = when (index) {
    0 -> true to currentDarkMode
    2 -> false to true
    else -> false to false
}

@Composable
fun SettingsTab(activity: MiuixMainActivity) {
    val context = LocalContext.current

    // ── 主题：界面风格（设计语言）+ 主题样式（明暗）──
    // 两项都是「从一组固定值里选一个」，所以统一用下拉选择行：行上只显示当前值、
    // 点开才是候选项。相比把候选项一条条铺在页面里，页面更短、行数恒定不跳动，
    // 也与系统设置的既有交互一致。
    SesameSectionTitle(text = "主题")
    // 选中项要跨 recompose / recreate 保留，状态声明在卡片组之外
    var uiStyle by remember { mutableStateOf(UiStyle.current()) }
    var followSystem by remember { mutableStateOf(AppConfig.INSTANCE.followSystem ?: true) }
    var darkMode by remember { mutableStateOf(AppConfig.INSTANCE.darkMode ?: false) }
    SesameCardGroup {
        SesameSelectRow(
            title = "界面风格",
            summary = "选择应用的界面风格",
            icon = Icons.Outlined.Palette,
            options = UiStyle.entries.map { it.shortLabel },
            selectedIndex = UiStyle.entries.indexOf(uiStyle),
            onSelect = { index ->
                UiStyle.entries.getOrNull(index)?.let { style ->
                    if (uiStyle != style) {
                        uiStyle = style
                        AppConfig.INSTANCE.uiStyle = style.code
                        AppConfig.save()
                        activity.recreate()
                    }
                }
            }
        )
        SesameSelectRow(
            title = "主题样式",
            summary = "跟随系统，或固定浅色 / 深色",
            icon = Icons.Outlined.DarkMode,
            // 三态由「跟随系统」+「深色模式」两个既有配置**组合**表达，不新增配置项：
            // 取色入口 SesameTheme.sesameIsDark 就是按这两个布尔算的，改数据模型只会徒增不兼容。
            options = THEME_MODE_LABELS,
            selectedIndex = themeModeIndex(followSystem, darkMode),
            onSelect = { index ->
                val (nextFollow, nextDark) = themeModeFlags(index, darkMode)
                if (followSystem != nextFollow || darkMode != nextDark) {
                    followSystem = nextFollow
                    darkMode = nextDark
                    AppConfig.INSTANCE.followSystem = nextFollow
                    AppConfig.INSTANCE.darkMode = nextDark
                    AppConfig.save()
                    activity.recreate()
                }
            }
        )
        // 底栏液态玻璃：外观类开关，与「界面风格 / 主题样式」同属主题分组。
        // 原先放在「系统设置」里 —— 但它改的是**外观**（底栏材质），不是系统行为，故挪到主题。
        var liquidGlassNavBar by remember { mutableStateOf(AppConfig.INSTANCE.liquidGlassNavBar != false) }
        SesameSwitchRow(
            title = "底栏液态玻璃",
            icon = Icons.Outlined.AutoAwesome,
            summary = "iOS 26 风折射悬浮底栏（实验）",
            checked = liquidGlassNavBar,
            onCheckedChange = {
                AppConfig.INSTANCE.liquidGlassNavBar = it
                AppConfig.save()
                liquidGlassNavBar = it
                // 该配置项不可观察（普通 Java 字段，非 Compose state），重建 Activity 让新值生效
                activity.recreate()
            }
        )
    }
    // 风格说明原本放在卡片下方（「Material You 设计语言…」那一行）：老板要求去掉，
    // 分组内两项各自已带副标题，再补一段容易读成 App 的免责/宣传文案。

    SesameSectionTitle(text = "功能设置")
    SesameCardGroup {
        SesameClickRow(
            title = "好友统计",
            icon = Icons.Outlined.Groups,
            onClick = { context.startActivity(Intent(context, MiuixFriendStatsActivity::class.java)) }
        )
        SesameClickRow(
            title = "扩展功能",
            icon = Icons.Outlined.Extension,
            onClick = { context.startActivity(Intent(context, MiuixExtensionsActivity::class.java)) }
        )
    }

    SesameSectionTitle(text = "系统设置")
    SesameCardGroup {
        // 文件权限申请引导
        val hasFilePerm = activity.hasPermission
        if (!hasFilePerm) {
            SesameClickRow(
                title = "申请文件权限",
                summary = "模块需要文件权限才能正常运行",
                icon = Icons.Outlined.FolderOpen,
                onClick = {
                    try {
                        PermissionUtil.checkOrRequestFilePermissions(activity)
                        activity.hasRequestedPermission = true
                    } catch (e: Exception) {
                        ToastUtil.show(context, "申请权限失败")
                    }
                }
            )
        }
        var iconHidden by remember { mutableStateOf(activity.isIconHidden()) }
        SesameSwitchRow(
            title = "隐藏图标",
            icon = Icons.Outlined.VisibilityOff,
            checked = iconHidden,
            onCheckedChange = {
                activity.toggleHideIcon()
                iconHidden = activity.isIconHidden()
            }
        )
        // 深色/跟随系统的开关已上移为「主题 → 主题样式」的三态选择：
        // 原来的两个开关是「一个布尔管另一个布尔」的从属关系（跟随系统开着时深色模式不参与取色），
        // 拆成三态后每个选项都自解释，不会再出现「点了没反应」的死开关。
        // （「底栏液态玻璃」开关同样已上移到「主题」分组 —— 它也是外观项，和界面风格/主题样式同类。）
        var batteryPerm by remember { mutableStateOf(AppConfig.INSTANCE.batteryPerm ?: true) }
        SesameSwitchRow(
            title = "为支付宝申请后台运行权限",
            icon = Icons.Outlined.BatterySaver,
            checked = batteryPerm,
            onCheckedChange = {
                AppConfig.INSTANCE.batteryPerm = it
                AppConfig.save()
                batteryPerm = it
            }
        )
        if (batteryPerm) {
            val hasPerm = try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations("com.eg.android.AlipayGphone") == true
            } catch (e: Exception) {
                false
            }
            if (!hasPerm) {
                SesameClickRow(
                    title = "立即申请权限",
                    icon = Icons.Outlined.BatteryAlert,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                data = Uri.parse("package:" + "com.eg.android.AlipayGphone")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            ToastUtil.show(context, "申请权限失败")
                        }
                    }
                )
            }
        }
    }

    SesameSectionTitle(text = "关于")
    SesameCardGroup {
        SesameClickRow(
            title = "关于应用",
            icon = Icons.Outlined.Info,
            onClick = { context.startActivity(Intent(context, MiuixAboutActivity::class.java)) }
        )
    }
}
