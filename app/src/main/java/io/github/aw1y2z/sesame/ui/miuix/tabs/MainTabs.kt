package io.github.aw1y2z.sesame.ui.miuix.tabs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.miuix.CardColumn
import io.github.aw1y2z.sesame.ui.miuix.LogType
import io.github.aw1y2z.sesame.ui.miuix.MiuixAboutActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixBlacklistActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixExtensionsActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixFriendStatsActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixLogViewerActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixMainActivity
import io.github.aw1y2z.sesame.ui.miuix.MiuixSettingsActivity
import io.github.aw1y2z.sesame.ui.miuix.groupIconOf
import io.github.aw1y2z.sesame.ui.theme.LocalThemeReveal
import io.github.aw1y2z.sesame.ui.theme.LocalUiStyle
import io.github.aw1y2z.sesame.ui.theme.SesameCard
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameCounterText
import io.github.aw1y2z.sesame.ui.theme.SesameExpandRow
import io.github.aw1y2z.sesame.ui.theme.SesameIcons
import io.github.aw1y2z.sesame.ui.theme.SesameInfoRow
import io.github.aw1y2z.sesame.ui.theme.SesameInlineEditor
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.SesameSelectRow
import io.github.aw1y2z.sesame.ui.theme.SesameStatusCard
import io.github.aw1y2z.sesame.ui.theme.SesameSwitchRow
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.SesameTheme
import io.github.aw1y2z.sesame.ui.theme.UiStyle
import io.github.aw1y2z.sesame.ui.theme.sesameApplyTheme
import io.github.aw1y2z.sesame.ui.theme.sesameApplyThemeColor
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.ui.theme.sesameIconHere
import io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant
import io.github.aw1y2z.sesame.ui.theme.sesamePaletteStyle
import io.github.aw1y2z.sesame.ui.theme.sesameRevealThemeChange
import io.github.aw1y2z.sesame.ui.theme.sesameSeedColor
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.Statistics.DataType
import io.github.aw1y2z.sesame.util.Statistics.TimeType
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import kotlinx.coroutines.delay
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.theme.PaletteStyle


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
        icon = sesameIconHere(SesameIcons.CheckCircle)
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
    // 订阅 statisticsVersion：load / 广播刷新后自增，触发本表重组读取最新数据。
    // ⚠️ 数据源必须是 activity.statisticsPages（按账号从各自文件读出），**不能再读 Statistics 静态单例** ——
    // 上游 6aad0eb9 起统计按账号隔离，静态单例只对应「本进程启动那一刻加载的那一份」，
    // 界面若仍读单例，刷新时拿到的永远是旧值（表现为「数据统计一直是 0 / 不更新」）。
    activity.statisticsVersion
    val pages = activity.statisticsPages
    if (pages.isEmpty()) {
        return
    }
    // 左右滑动切换账号，默认停在首项（全部账号合计）。
    // 循环滑动：页数取超大值、真实下标取模，滑到最后一页继续滑就回到第一页（两个方向都能一直滑）。
    val initialPage = remember(pages.size) {
        val base = Int.MAX_VALUE / 2
        base - base % pages.size
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { Int.MAX_VALUE }
    // 每次进入首页都回到首项（合计）：pagerState 自身会记住上次页码，不归位就不算「默认显示合计」
    LaunchedEffect(pages.size) {
        pagerState.scrollToPage(initialPage)
    }
    val current = pagerState.currentPage % pages.size
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
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SesameText(text = pages[current].label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (pages.size > 1) {
                // 明示这一块可以左右滑动，否则用户不知道还有别的账号。
                // 「当前第几个」和「操作提示」是两个事实，各占一行 ——
                // 不用 "·"/空格把它们粘成一行（mochi 规范明令禁止）。
                Column(horizontalAlignment = Alignment.End) {
                    SesameText(
                        text = "${current + 1}/${pages.size}",
                        fontSize = 12.sp,
                        color = sesameOnSurfaceVariant()
                    )
                    SesameText(
                        text = "左右滑动切换账号",
                        fontSize = 12.sp,
                        color = sesameOnSurfaceVariant()
                    )
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            StatisticsGrid(pages[page % pages.size].statistics)
        }
    }
}

/** 单个账号（或「全部账号合计」）的统计表：行=项目、列=今日/本月/今年 */
@Composable
private fun StatisticsGrid(statistics: Statistics) {
    val rows = listOf(
        "收" to listOf(DataType.COLLECTED),
        "帮" to listOf(DataType.HELPED),
        "浇" to listOf(DataType.WATERED),
        "被水" to listOf(DataType.WATEREDCOUNT),
        "浇水" to listOf(DataType.WATERINGCOUNT)
    )
    val columns = listOf(TimeType.DAY, TimeType.MONTH, TimeType.YEAR)
    val headers = listOf("今日", "本月", "今年")

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
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
                    val value = types.sumOf { statistics.dataOf(timeType, it) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SesameCounterText(value = value.toLong(), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTab(
    activity: MiuixMainActivity,
    onOpenLog: (LogType) -> Unit = { openLog(activity, it) },
) {
    // ── 分类记录：每个配置分组一个条目
    //    （森林/保护/物种/海洋/庄园/新村/农场/金豆/运动/会员/经营/AI答/其他） ──
    // 全部**平级**展示，每行都有自己的写入开关。
    // 其中 4 个分组（新村/农场 → farm.log，运动/会员 → other.log）与森林/庄园等
    // **共用日志文件**，但开关是按分组给的（见 AppConfig.isEnableStallLog 等，
    // 写入侧由 Log.allowCategoryFile 按模块名路由），所以它们并不需要跟着谁走。
    SesameSectionTitle(text = "分类记录")
    SesameCardGroup {
        for (entry in LogType.displayOrder) {
            CategoryRow(activity, entry, onOpenLog)
        }
    }

    SesameSectionTitle(text = "系统记录")
    SesameCardGroup {
        var debug by remember { mutableStateOf(AppConfig.INSTANCE.enableDebugLog ?: false) }
        SesameSwitchRow(
            title = "抓包记录",
            icon = sesameIconHere(SesameIcons.Wifi),
            checked = debug,
            onCheckedChange = {
                debug = it
                AppConfig.INSTANCE.enableDebugLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                // 关闭时**不清空** debug 日志：抓到的包是排查证据，要清空请到日志页点「删除」
            },
            onClick = { onOpenLog(LogType.DEBUG) }
        )
        var error by remember { mutableStateOf(AppConfig.INSTANCE.enableViewErrorLog ?: true) }
        SesameSwitchRow(
            title = "查看异常日志",
            icon = sesameIconHere(SesameIcons.Bug),
            checked = error,
            onCheckedChange = {
                error = it
                AppConfig.INSTANCE.enableViewErrorLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                if (!it) FileUtil.clearLog("error")
            },
            onClick = { onOpenLog(LogType.ERROR) }
        )
        var runtime by remember { mutableStateOf(AppConfig.INSTANCE.enableViewRuntimeLog ?: true) }
        SesameSwitchRow(
            title = "查看运行日志",
            icon = sesameIconHere(SesameIcons.Terminal),
            checked = runtime,
            onCheckedChange = {
                runtime = it
                AppConfig.INSTANCE.enableViewRuntimeLog = it
                AppConfig.save()
                activity.broadcastReloadConfig()
                if (!it) FileUtil.clearLog("runtime")
            },
            onClick = { onOpenLog(LogType.RUNTIME) }
        )
    }
}

/**
 * 「分类记录」里的一行：分组名 + 该分组自己的**写入开关**（各分组平级）。
 *
 * `showArrow = false`：开关本身就是行尾控件（MD3 的常规），不再并排一个箭头 ——
 * 两者并存会让行尾显得拥挤；点整行仍是进日志查看页。
 *
 * 少数分组**没有独立开关**（如「AI答」：日志只有 Log.record、落在运行日志里），
 * 这些行走 [SesameClickRow] 只保留查看入口 —— 原因见函数体注释。
 *
 * ⚠️ 关闭开关**只停写新的**，不删已有历史日志：同一个文件里还混着兄弟分组的日志
 * （farm.log 里同时有庄园/新村/农场），按文件清空会误伤邻家。
 * 想清掉本分组的历史记录，请到查看页点「删除」（按 `[模块名]` 精确删，见 clearGroupLines）。
 */
@Composable
private fun CategoryRow(
    activity: MiuixMainActivity,
    entry: LogType,
    onOpenLog: (LogType) -> Unit,
) {
    val toggle = categoryToggleOf(entry)
    // 分组图标从 Ionicons 资源解析，需要 Context（见 groupIconOf）
    val context = LocalContext.current
    if (toggle == null) {
        // 没有分类文件、也就没有独立写入开关的分组（目前只有「AI答」：它只调 Log.record，
        // 日志落在运行日志里）。这里刻意**不放开关** —— 它唯一能配的是「查看运行日志」，
        // 而那一个开关管着所有模块的 record 日志，摆在这一行会让用户以为"只关 AI答"、
        // 实际把整条运行日志停了。写入控制交给「系统记录 → 查看运行日志」。
        SesameClickRow(
            title = entry.displayName,
            summary = "日志随「运行日志」写入",
            icon = groupIconOf(context, entry.groupCode.orEmpty()),
            onClick = { onOpenLog(entry) }
        )
        return
    }
    var enabled by remember { mutableStateOf(toggle.getValue()) }
    SesameSwitchRow(
        title = entry.displayName,
        icon = groupIconOf(context, entry.groupCode.orEmpty()),
        checked = enabled,
        showArrow = false,
        onCheckedChange = {
            enabled = it
            toggle.setValue(it)
            AppConfig.save()
            activity.broadcastReloadConfig()
            // 刻意不在这里清空文件：本分组的日志与兄弟分组同处一个文件
            // （farm.log / other.log），整文件清空会把它们的日志一并删掉。
        },
        onClick = { onOpenLog(entry) }
    )
}

/**
 * 分类记录的开关描述子：读写 `AppConfig` 里对应分组的日志开关。
 *
 * 每个分组各有一个，互不影响；写入侧由 `Log.allowCategoryFile` 按模块名路由到同一个字段。
 */
private class CategoryToggle(
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit
) {
    fun getValue(): Boolean = getter()
    fun setValue(value: Boolean) = setter(value)
}

/**
 * 取该分类对应的开关。
 *
 * ⚠️ 这里的读法与写入侧 `Log.allowCategoryFile` 必须一一对应：
 * 「森林」组管 AntForestV2、「金豆夺宝」组管 goldenbeans、「庄园」管 AntFarm、
 * 「新村」管 AntStall、「农场」管 AntOrchard、「运动」管 AntSports、「会员」管 AntMember，
 * 「其他」管剩下的全部模块。两边改一边不改，就会出现「拨了开关日志照写」。
 */
private fun categoryToggleOf(logType: LogType): CategoryToggle? = when (logType) {
    LogType.FOREST -> CategoryToggle(
        { AppConfig.INSTANCE.enableForestLog ?: true },
        { AppConfig.INSTANCE.enableForestLog = it }
    )
    LogType.FARM -> CategoryToggle(
        { AppConfig.INSTANCE.enableFarmLog ?: true },
        { AppConfig.INSTANCE.enableFarmLog = it }
    )
    LogType.STALL -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableStallLog },
        { AppConfig.INSTANCE.setEnableStallLog(it) }
    )
    LogType.ORCHARD -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableOrchardLog },
        { AppConfig.INSTANCE.setEnableOrchardLog(it) }
    )
    LogType.GOLDENBEANS -> CategoryToggle(
        { AppConfig.INSTANCE.enableGoldenBeansLog ?: true },
        { AppConfig.INSTANCE.enableGoldenBeansLog = it }
    )
    LogType.SPORTS -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableSportsLog },
        { AppConfig.INSTANCE.setEnableSportsLog(it) }
    )
    LogType.MEMBER -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableMemberLog },
        { AppConfig.INSTANCE.setEnableMemberLog(it) }
    )
    LogType.OTHER -> CategoryToggle(
        { AppConfig.INSTANCE.enableOtherLog ?: true },
        { AppConfig.INSTANCE.enableOtherLog = it }
    )
    // ── 2026-10-09：上游 e811eb0f 拆出的 5 个新分组 ──
    // ⚠️ 这几行必须与写入侧 Log.allowCategoryFile 的 case 一一对应，
    //    否则会出现「拨了开关日志照写 / 关了还写」的分裂（见该方法的注释）。
    LogType.PROTECT -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableProtectLog },
        { AppConfig.INSTANCE.setEnableProtectLog(it) }
    )
    LogType.SPECIES -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableSpeciesLog },
        { AppConfig.INSTANCE.setEnableSpeciesLog(it) }
    )
    LogType.OCEAN -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableOceanLog },
        { AppConfig.INSTANCE.setEnableOceanLog(it) }
    )
    LogType.GREEN_FINANCE -> CategoryToggle(
        { AppConfig.INSTANCE.isEnableGreenFinanceLog },
        { AppConfig.INSTANCE.setEnableGreenFinanceLog(it) }
    )
    // 「AI答」故意不在这里：它的日志只有 Log.record（写运行日志），没有自己的分类文件，
    // 也就没有独立开关可配。复用「运行日志」开关会误导（一拨就停掉整条运行日志），
    // 故这一行在 CategoryRow 里走「无开关的纯入口」，写入控制交给「系统记录 → 查看运行日志」。
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
fun ConfigTab(
    activity: MiuixMainActivity,
    onOpenSettings: ((userId: String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    // 🔴 key 不能省：这段要实时反映 config 目录里真实存在的账号，
    // 用「无 key 的 remember」会被缓存死 —— 删掉某个账号后回到本页，那一行仍在，
    // 必须切到别的 Tab 再切回来（Composable 重新组合）才消失。
    // 版本号由 MiuixMainActivity.onResume 自增，从设置页返回即触发重扫。
    val items = remember(activity.configAccountsVersion) {
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
                icon = sesameIconHere(SesameIcons.Account),
                onClick = {
                    // MD 单Activity导航下走 route push；MIUIX 不传回调 → 仍走原 startActivity。
                    if (onOpenSettings != null) {
                        onOpenSettings(userId)
                    } else {
                        val intent = Intent(context, MiuixSettingsActivity::class.java)
                        if (userId != null) intent.putExtra("userId", userId)
                        context.startActivity(intent)
                    }
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
            icon = sesameIconHere(SesameIcons.Sync),
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
            icon = sesameIconHere(SesameIcons.Chat),
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
            icon = sesameIconHere(SesameIcons.SwapVertical),
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
            icon = sesameIconHere(SesameIcons.Security),
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
            icon = sesameIconHere(SesameIcons.Ban),
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

/**
 * 取色算法（与 mochi PaletteStyle 对应，只暴露 demo 展示的 4 档）。
 *
 * ⚠️ 必须用中文短标签：4 个英文词（Tonal/Expressive/Vibrant/Neutral）横向铺满整行后，
 * `MochiListRow` 的标题位会被压到 0 —— 实测「取色算法」四个字直接消失，只剩一排选项。
 * 中文每项 2 字后标题才有位置。
 */
private val THEME_PALETTE_LABELS = listOf("色调", "表现", "鲜艳", "中性")
private val THEME_PALETTES = listOf(
    PaletteStyle.TonalSpot,
    PaletteStyle.Expressive,
    PaletteStyle.Vibrant,
    PaletteStyle.Neutral,
)

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

/**
 * 「设置」Tab。
 *
 * @param glassEnabled 底栏液态玻璃当前状态（**由 MainScreen 持有** —— 它是 Compose state，
 *   所以切换时底栏能就地重组生效，不必像以前那样 `activity.recreate()`，
 *   那会把整个界面销毁重建、视觉上"闪一下"）。
 * @param onGlassEnabledChange 切换回调，由 MainScreen 提供（同时负责写回 AppConfig 落盘）。
 */
@Composable
fun SettingsTab(
    activity: MiuixMainActivity,
    glassEnabled: Boolean = AppConfig.INSTANCE.liquidGlassNavBar != false,
    onGlassEnabledChange: ((Boolean) -> Unit)? = null,
    // 方案A：MD 单Activity导航下，关于页作为 route push；MIUIX 不传 → 仍走原 startActivity。
    onOpenAbout: (() -> Unit)? = null,
    // 好友统计：MD 走 route push；MIUIX 不传 → 仍走原 startActivity。
    onOpenFriendStats: (() -> Unit)? = null,
    // 扩展功能 / 任务黑名单：MD 走 route push；MIUIX 不传 → 仍走原 startActivity。
    onOpenExtensions: (() -> Unit)? = null,
    onOpenBlacklist: (() -> Unit)? = null,
) {
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
    // 主题揭开的 fromDark/toDark 需要在切换回调里拿到「切换前后的真实明暗」。
    // 两项都是可观察快照，读一次即可；onSelect lambda 捕获的是重组时的值。
    val systemDark = isSystemInDarkTheme()
    val currentDark = if (followSystem) systemDark else darkMode
    // MD 根部注入的主题揭开状态；MIUIX 下为 null（切换时直接改，零变化）
    val themeReveal = LocalThemeReveal.current
    SesameCardGroup {
        SesameSelectRow(
            title = "界面风格",
            summary = "选择应用的界面风格",
            icon = sesameIconHere(SesameIcons.Palette),
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
            icon = sesameIconHere(SesameIcons.Moon),
            // 三态由「跟随系统」+「深色模式」两个既有配置**组合**表达，不新增配置项：
            // 取色入口 SesameTheme.sesameIsDark 就是按这两个布尔算的，改数据模型只会徒增不兼容。
            options = THEME_MODE_LABELS,
            selectedIndex = themeModeIndex(followSystem, darkMode),
            onSelect = { index ->
                val (nextFollow, nextDark) = themeModeFlags(index, darkMode)
                if (followSystem != nextFollow || darkMode != nextDark) {
                    followSystem = nextFollow
                    darkMode = nextDark
                    // ⚠️ 不再 activity.recreate()：明暗已改成可观察 state（见 SesameTheme.DarkOverride），
                    // 写进去即整页重组换配色。原先靠重建 Activity 才生效 → 视觉上"界面闪一下"。
                    // MD 风格：包一层 mochi 主题揭开——先拍屏、再改主题，从点击处圆形揭开新配色；
                    // MIUIX 分支 sesameRevealThemeChange 内部直接执行 change，HyperOS 观感零变化。
                    val nextResolvedDark = if (nextFollow) systemDark else nextDark
                    sesameRevealThemeChange(themeReveal, currentDark, nextResolvedDark) {
                        sesameApplyTheme(nextFollow, nextDark)
                        AppConfig.save()
                    }
                }
            }
        )
        // 底栏液态玻璃：外观类开关，与「界面风格 / 主题样式」同属主题分组。
        // 原先放在「系统设置」里 —— 但它改的是**外观**（底栏材质），不是系统行为，故挪到主题。
        //
        // ⚠️ 这里**不再**调 activity.recreate()：玻璃状态已由 MainScreen 以 Compose state 持有，
        // 切换后底栏就地重组生效。原先靠 recreate 让新值生效，会销毁重建整个 Activity →
        // 界面"闪一下"（这个开关独有的问题：它改的是整套渲染路径，不是某个数值）。
        // onGlassEnabledChange 为空（例如被别处单独调用）时才退回 recreate 兜底。
        SesameSwitchRow(
            title = "底栏液态玻璃",
            icon = sesameIconHere(SesameIcons.Sparkles),
            // 不带副标题：原先写「iOS 26 风折射悬浮底栏（实验）」，
            // 既是实现细节（折射怎么做的）、又带"实验"字样容易让人不敢开，
            // 对用户没有决策价值 —— 开关叫什么、开了什么效果，看开关本身即可。
            checked = glassEnabled,
            onCheckedChange = { checked ->
                if (onGlassEnabledChange != null) {
                    onGlassEnabledChange(checked)
                } else {
                    AppConfig.INSTANCE.liquidGlassNavBar = checked
                    AppConfig.save()
                    activity.recreate()
                }
            }
        )
    }
    // 风格说明原本放在卡片下方（「Material You 设计语言…」那一行）：老板要求去掉，
    // 分组内两项各自已带副标题，再补一段容易读成 App 的免责/宣传文案。

    // ── 主题色（仅 MD）──────────────────────────────────────────────────────
    // MIUIX 走 HyperOS 自己的配色体系，不接 mochi 的种子色生成，所以整块不显示。
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        val seed = sesameSeedColor()
        val palette = sesamePaletteStyle()
        // 非 Composable 的 onCheckedChange 里读不到 MaterialTheme，先在组合期取出来
        val currentPrimary = MaterialTheme.colorScheme.primary.toArgb()
        var seedState by remember { mutableStateOf(seed) }
        // 从外面（配置恢复、其它页改）同步回来
        LaunchedEffect(seed) { seedState = seed }

        SesameSectionTitle(text = "主题色")
        SesameCardGroup {
            SesameSwitchRow(
                title = "跟随系统壁纸取色",
                summary = if (seedState == null) {
                    "当前配色由系统壁纸生成（Material You）"
                } else {
                    "关闭后使用下方自选色块"
                },
                checked = seedState == null,
                onCheckedChange = { follow ->
                    if (follow) {
                        seedState = null
                        sesameApplyThemeColor(null, palette)
                    } else {
                        // 以当前主色为起点，避免一打开就跳到与界面无关的颜色
                        val start = currentPrimary
                        seedState = start
                        sesameApplyThemeColor(start, palette)
                    }
                }
            )
            SesameSelectRow(
                title = "取色算法",
                summary = "仅自选主题色时生效，决定配色的彩度与层次",
                options = THEME_PALETTE_LABELS,
                selectedIndex = THEME_PALETTES.indexOf(palette).coerceAtLeast(0),
                onSelect = { index ->
                    THEME_PALETTES.getOrNull(index)?.let { sesameApplyThemeColor(seedState, it) }
                }
            )
        }
        SesameCardGroup {
            MochiSeedPalette(
                // 色板按卡片可用宽度等距铺满，首列/取色轮会贴到卡片边缘甚至视觉溢出；
                // 补水平内缩进，与卡片内其它行的文字左边距对齐。
                modifier = Modifier.padding(horizontal = 16.dp),
                selected = seedState?.let { Color(it) },
                onSelect = { color ->
                    val argb = color.toArgb()
                    seedState = argb
                    sesameApplyThemeColor(argb, palette)
                },
                style = palette,
            )
        }
        // 下方说明：与 demo 同样的口径，避免用户以为选了色还跟着壁纸变
        SesameText(
            text = "选了色块后，「跟随系统壁纸取色」会关闭，改用同样明暗的静态配色。",
            fontSize = 13.sp,
            color = sesameOnSurfaceVariant(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }

    SesameSectionTitle(text = "功能设置")
    SesameCardGroup {
        SesameClickRow(
            title = "好友统计",
            icon = sesameIconHere(SesameIcons.People),
            onClick = {
                if (onOpenFriendStats != null) onOpenFriendStats()
                else context.startActivity(Intent(context, MiuixFriendStatsActivity::class.java))
            }
        )
        SesameClickRow(
            title = "扩展功能",
            icon = sesameIconHere(SesameIcons.Extension),
            onClick = {
                if (onOpenExtensions != null) onOpenExtensions()
                else context.startActivity(Intent(context, MiuixExtensionsActivity::class.java))
            }
        )
        // 任务黑名单：把"模块自己拉黑了哪些任务"摊开给用户看（原先只在日志里，界面完全不可见）
        SesameClickRow(
            title = "任务黑名单",
            icon = sesameIconHere(SesameIcons.Ban),
            onClick = {
                if (onOpenBlacklist != null) onOpenBlacklist()
                else context.startActivity(Intent(context, MiuixBlacklistActivity::class.java))
            }
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
                icon = sesameIconHere(SesameIcons.Folder),
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
            icon = sesameIconHere(SesameIcons.EyeOff),
            checked = iconHidden,
            onCheckedChange = {
                activity.toggleHideIcon()
                iconHidden = activity.isIconHidden()
            }
        )
        var logViewByRun by remember { mutableStateOf(AppConfig.INSTANCE.logViewByRun ?: false) }
        SesameSwitchRow(
            title = "日志按轮次查看",
            icon = sesameIconHere(SesameIcons.Terminal),
            checked = logViewByRun,
            summary = "按每轮「开始执行」把日志聚成一页，重进日志页生效",
            onCheckedChange = {
                AppConfig.INSTANCE.logViewByRun = it
                AppConfig.save()
                logViewByRun = it
            }
        )
        // 深色/跟随系统的开关已上移为「主题 → 主题样式」的三态选择：
        // 原来的两个开关是「一个布尔管另一个布尔」的从属关系（跟随系统开着时深色模式不参与取色），
        // 拆成三态后每个选项都自解释，不会再出现「点了没反应」的死开关。
        // （「底栏液态玻璃」开关同样已上移到「主题」分组 —— 它也是外观项，和界面风格/主题样式同类。）
        var batteryPerm by remember { mutableStateOf(AppConfig.INSTANCE.batteryPerm ?: true) }
        SesameSwitchRow(
            title = "为支付宝申请后台运行权限",
            icon = sesameIconHere(SesameIcons.BatteryCharging),
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
                    icon = sesameIconHere(SesameIcons.BatteryHalf),
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
            icon = sesameIconHere(SesameIcons.Info),
            onClick = {
                // 方案A：MD 单Activity导航下关于页是 route；MIUIX 仍走原 Activity。
                if (onOpenAbout != null) onOpenAbout()
                else context.startActivity(Intent(context, MiuixAboutActivity::class.java))
            }
        )
    }
}
