package io.github.aw1y2z.sesame.ui.miuix

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountCircle
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
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.theme.SesameCard
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameExpandRow
import io.github.aw1y2z.sesame.ui.theme.SesameInfoRow
import io.github.aw1y2z.sesame.ui.theme.SesameInlineEditor
import io.github.aw1y2z.sesame.ui.theme.SesameNavBar
import io.github.aw1y2z.sesame.ui.theme.SesameNavItem
import io.github.aw1y2z.sesame.ui.theme.SesamePageTitle
import io.github.aw1y2z.sesame.ui.theme.SesameRadioRow
import io.github.aw1y2z.sesame.ui.theme.SesameScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.SesameStatusCard
import io.github.aw1y2z.sesame.ui.theme.SesameSwitchRow
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.UiStyle
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant
import io.github.aw1y2z.sesame.ui.theme.sesamePrimary
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.LanguageUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.Statistics.DataType
import io.github.aw1y2z.sesame.util.Statistics.TimeType
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Calendar

class MiuixMainActivity : MiuixBaseActivity() {

    var runTypeText by mutableStateOf("")

    /** 可观察的激活状态：HomeTab 等页面订阅它，onServiceBind/广播更新时自动重组刷新 */
    var uiRunType by mutableStateOf<RunType>(RunType.DISABLE)
    var statisticsText by mutableStateOf("")
    var hasPermission by mutableStateOf(false)

    /** 统计版本号：load / 广播刷新后自增,首页 StatisticsTable 订阅它来触发重组 */
    var statisticsVersion by mutableStateOf(0)

    private val handler = Handler(Looper.getMainLooper())
    private var isClick = false
    // 标记是否已通过系统设置页请求过权限，用于 onResume 中检测用户是否已授权
    var hasRequestedPermission by mutableStateOf(false)

    /** 激活探测已重试次数,上限见 MAX_RUN_TYPE_PROBE_TIMES */
    private var runTypeProbeTimes = 0

    private lateinit var titleRunner: Runnable

    init {
        /**
         * 激活探测:仅当真实状态仍为 DISABLE 时才显示未激活,并在超时前周期性重试,
         * 避免覆盖晚到的 onServiceBind 激活信号,也避免 XposedService 绑定较慢时误报未激活
         */
        titleRunner = Runnable {
            if (ViewAppInfo.getRunType() == RunType.DISABLE) {
                runTypeProbeTimes++
                updateSubTitle(RunType.DISABLE)
                if (runTypeProbeTimes < MAX_RUN_TYPE_PROBE_TIMES) {
                    sendQueryBroadcast()
                    handler.postDelayed(titleRunner, 3000)
                }
            } else {
                runTypeProbeTimes = 0
            }
        }
    }

    companion object {
        /** 最多探测次数:每次间隔 3 秒,共约 15 秒,覆盖 XposedService 冷启动绑定晚于 Activity 的情况 */
        private const val MAX_RUN_TYPE_PROBE_TIMES = 5

        /**
         * 设备显示名:优先读市场名(如 Xiaomi 13)。
         * 小米/红米及多数云手机、模拟器的 ro.product.model 只是内部型号编号(如 2211133C),
         * 多设备会显示相同,须用 ro.product.marketname 才有人类可读名称。
         */
        fun getDeviceDisplayName(): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java)
                val market = get.invoke(null, "ro.product.marketname") as? String
                if (!market.isNullOrBlank()) {
                    return market
                }
            } catch (_: Throwable) {
            }
            return Build.MODEL ?: Build.DEVICE ?: ""
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.i("view broadcast action:" + action + " intent:" + intent)
            if (action != null) {
                when (action) {
                    "io.github.aw1y2z.sesame.status" -> {
                        // 模块已被 LSPosed 启用并注入支付宝，标记为已激活
                        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode())
                        runTypeProbeTimes = 0
                        handler.removeCallbacks(titleRunner)
                        updateSubTitle(RunType.MODEL)
                        if (isClick) {
                            ToastUtil.show(context, "芝麻粒加载状态正常")
                            isClick = false
                        }
                    }

                    "io.github.aw1y2z.sesame.update" -> {
                        refreshStatistics()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 日志 Tab 要按配置分组列出各项入口，需要模型注册表。
        // 用「按需、非破坏性」的初始化：只有在注册表还是空的时候才建，
        // 已有注册表（例如已由 MiuixSettingsActivity 加载过真实配置）一律原样保留。
        // ⚠️ 不要改成 initAllModel()——那会重建整张注册表、把已加载的配置值清成默认值。
        Model.initAllModelIfNeeded()
        // runType 被模块置为 MODEL（onModuleLoaded）时立即刷新界面，无需手动加载配置
        ViewAppInfo.setRunTypeListener {
            runOnUiThread {
                handler.removeCallbacks(titleRunner)
                updateSubTitle(ViewAppInfo.getRunType())
            }
        }
        ViewAppInfo.checkRunType()
        updateSubTitle(ViewAppInfo.getRunType())
        val intentFilter = IntentFilter()
        intentFilter.addAction("io.github.aw1y2z.sesame.status")
        intentFilter.addAction("io.github.aw1y2z.sesame.update")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        setAppContent {
            MainScreen(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // 激活状态探测独立于存储权限：防止 titleRunner 重复累积，先清空再启动
        if (RunType.DISABLE == ViewAppInfo.getRunType()) {
            handler.removeCallbacks(titleRunner)
            runTypeProbeTimes = 0
            sendQueryBroadcast()
            handler.postDelayed(titleRunner, 3000)
        }
        checkPermissionAndRefresh()
    }

    /** 检查文件权限，若已授权则刷新统计；同时处理首次请求权限的场景 */
    private fun checkPermissionAndRefresh() {
        if (hasRequestedPermission) {
            hasRequestedPermission = false
            if (PermissionUtil.checkFilePermissions(this)) {
                hasPermission = true
                refreshStatistics()
            }
        } else if (!hasPermission && PermissionUtil.checkFilePermissions(this)) {
            // 首次进入或权限刚被授予
            hasPermission = true
            refreshStatistics()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (granted) refreshStatistics()
        }
    }

    /**
     * 重新从统计文件加载并通知首页表格刷新。
     * 基于可观察的 statisticsVersion 触发 Compose 重组,解决"首次进入首页统计为 0、
     * 必须进入配置返回后才刷新"的问题(此前 StatisticsTable 直接读静态单例,单例变化不会重组)。
     */
    fun refreshStatistics() {
        if (!hasPermission) return
        try {
            Statistics.load()
            Statistics.updateDay(Calendar.getInstance())
            statisticsVersion++
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    fun updateSubTitle(runType: RunType) {
        uiRunType = runType
        runTypeText = when (runType) {
            RunType.DISABLE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.disable) + "】"
            RunType.MODEL -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.activated) + "】"
            RunType.PACKAGE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.loading) + "】"
        }
    }

    fun sendStatus() {
        try {
            isClick = true
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 向支付宝进程查询本模块注入状态（不弹 Toast），由 titleRunner 周期性调用 */
    fun sendQueryBroadcast() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 通知支付宝进程重载共享配置（日志开关等），使开关在注入进程中即时生效 */
    /**
     * 让注入进程整体重启（重新初始化并重挂 hook）。
     * 适用于改完必须重新初始化的开关，比如「使用新接口」要重挂 RPC bridge；
     * 只是重载 AppConfig 的 broadcastReloadConfig() 不够用。
     */
    fun broadcastRestart() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.restart"))
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    fun broadcastReloadConfig() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast reloadConfig err:")
            Log.printStackTrace(th)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(this, "无法打开链接")
        }
    }

    fun toggleLanguage() {
        val appConfig = AppConfig.INSTANCE
        appConfig.languageSimplifiedChinese = !appConfig.languageSimplifiedChinese
        if (AppConfig.save()) {
            LanguageUtil.setLocal(this)
            recreate()
        }
    }

    fun isIconHidden(): Boolean {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        return packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun toggleHideIcon() {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        val state = packageManager.getComponentEnabledSetting(alias)
        val newState = if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
    }

    fun exportStatistics(): Uri? {
        return FileUtil.getExportedStatisticsFile()?.let { Uri.fromFile(it) }
    }

    fun importStatistics(): Boolean {
        val src = FileUtil.getExportedStatisticsFile()
        if (src != null && FileUtil.copyTo(src, FileUtil.getStatisticsFile())) {
            statisticsText = Statistics.getText(this)
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        // 离开前台即停止状态轮询，避免后台无谓广播与泄漏
        handler.removeCallbacks(titleRunner)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(titleRunner)
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (_: Exception) {
        }
    }
}

/**
 * 一级页面骨架：底部导航 + 可滚动内容区。
 * 组件全部来自 ui.theme 的风格无关组件集，因此 HyperOS / Material 3 两套风格共用同一份页面代码。
 */
@Composable
fun MainScreen(activity: MiuixMainActivity) {
    // 用 rememberSaveable：切换界面风格会 recreate() 重建整个页面，
    // 若用普通 remember，重建后这里会退回 0，用户刚点开的设置页又被弹回首页。
    // rememberSaveable 会随 Activity 的实例状态一起恢复，滚动位置同理（rememberScrollState 自带）。
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 每个 Tab 各记各的滚动位置。4 个 Tab 共用同一个外层滚动容器，若也共用一份 ScrollState，
    // 「日志」滚到中间再切「配置」，配置会停在同一高度（长页切短页还会看到下方一片空白）。
    // rememberScrollState() 底层是 saveable，切界面风格 recreate 后各 Tab 的位置也能各自恢复。
    val homeScroll = rememberScrollState()
    val logsScroll = rememberScrollState()
    val configScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()

    // 底部 Tab 用 HorizontalPager 承载：左右滑动切页带过渡动画，切走再切回各页滚动位置不变。
    // pagerState 与 selectedTab 双向同步——点底部导航 → 平滑滚到对应页；手指滑动 → 高亮跟随。
    // ⚠️ 关键修复：点导航用 animateScrollToPage 跨多页滑动时，pagerState.currentPage 会依次经过中间页，
    // 若直接回写 selectedTab 会反向取消动画、把 pager 卡在半页（"界面卡在一部分"）。
    // 因此用 isProgrammaticScroll 锁：程序化滑动期间不接受 pager 回灌的页码，避免双向 effect 打架。
    val pagerState = rememberPagerState(initialPage = selectedTab) { 4 }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage == selectedTab) return@LaunchedEffect
        isProgrammaticScroll = true
        try {
            pagerState.animateScrollToPage(selectedTab)
        } finally {
            isProgrammaticScroll = false
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (!isProgrammaticScroll && selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }

    SesameScaffold(
        bottomBar = {
            SesameNavBar(
                items = listOf(
                    SesameNavItem(Icons.Filled.Home, "首页"),
                    SesameNavItem(Icons.Filled.Description, "日志"),
                    SesameNavItem(Icons.Filled.Tune, "配置"),
                    SesameNavItem(Icons.Filled.Settings, "设置")
                ),
                // 选中态绑 selectedTab（点击即定为目标页，不会经过中间页闪烁）；
                // 手指滑动时第二个 LaunchedEffect 会把 selectedTab 跟到当前页，高亮仍跟随手指。
                selected = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            val scroll = when (page) {
                0 -> homeScroll
                1 -> logsScroll
                2 -> configScroll
                else -> settingsScroll
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (page) {
                    0 -> HomeTab(activity)
                    1 -> LogsTab(activity)
                    2 -> ConfigTab(activity)
                    3 -> SettingsTab(activity)
                }
            }
        }
    }
}

@Composable
fun HomeTab(activity: MiuixMainActivity) {
    // 订阅 Compose state：onServiceBind / 状态广播到达时会自动重组刷新首页状态
    val activated = activity.uiRunType == RunType.MODEL
    val version = ViewAppInfo.getAppVersion()

    // 不传 bottomPadding：组件默认 12dp，与上游本次把标题下间距由 4dp 调到 12dp 的意图一致
    SesamePageTitle(text = "Sesame-M")

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
    SesamePageTitle(text = "日志")

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

    SesamePageTitle(text = "配置")

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

@Composable
fun SettingsTab(activity: MiuixMainActivity) {
    val context = LocalContext.current

    SesamePageTitle(text = "设置")

    // ── 界面风格：双设计语言切换，切换后 recreate 让整套页面按新风格重建 ──
    SesameSectionTitle(text = "界面风格")
    // 选中项要跨 recompose / recreate 保留，状态声明在卡片组之外
    var uiStyle by remember { mutableStateOf(UiStyle.current()) }
    SesameCardGroup {
        UiStyle.entries.forEach { style ->
            SesameRadioRow(
                title = style.label,
                icon = if (style == UiStyle.MATERIAL3) Icons.Outlined.Palette else Icons.Outlined.Smartphone,
                selected = uiStyle == style,
                onClick = {
                    if (uiStyle != style) {
                        uiStyle = style
                        AppConfig.INSTANCE.uiStyle = style.code
                        AppConfig.save()
                        activity.recreate()
                    }
                }
            )
        }
    }
    // 风格说明放在卡片**下方**：它注解的是整个分组，混在卡内会被读成最后一项的副标题。
    SesameText(
        text = uiStyle.summary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        fontSize = 12.sp,
        color = sesameOnSurfaceVariant()
    )

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
        // 「跟随系统设置」必须先声明：下面「深色模式」的可用状态由它决定。
        // 两者不是并列关系——跟随系统开着时，深色模式的值根本不参与取色计算
        // （见 SesameTheme.sesameIsDark），此时让它可点等于让用户白点，必须置灰。
        var followSystem by remember { mutableStateOf(AppConfig.INSTANCE.followSystem ?: true) }
        var darkMode by remember { mutableStateOf(AppConfig.INSTANCE.darkMode ?: false) }
        SesameSwitchRow(
            title = "深色模式",
            icon = Icons.Outlined.DarkMode,
            checked = darkMode,
            enabled = !followSystem,
            summary = if (followSystem) "需先关闭下方「跟随系统设置」" else null,
            onCheckedChange = {
                AppConfig.INSTANCE.darkMode = it
                AppConfig.save()
                darkMode = it
                activity.recreate()
            }
        )
        SesameSwitchRow(
            title = "跟随系统设置",
            icon = Icons.Outlined.Sync,
            checked = followSystem,
            onCheckedChange = {
                AppConfig.INSTANCE.followSystem = it
                AppConfig.save()
                followSystem = it
                activity.recreate()
            }
        )
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

/**
 * 二级页沿用的分组容器（Miuix 实现）。
 * 二级页面尚未迁移到风格组件集，此处保持原有实现，待后续按页迁移后统一替换为 SesameCardGroup。
 */
@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Card 只传 modifier：preference 行直接作为子项，行的左右缩进交给行自身的 insideMargin。
    // 之前给 Card 传 insideMargin 会把所有行整体往里缩，行自带的方形按压高亮就成了"悬在卡片里的方框"；
    // 让行顶满卡片宽度后，高亮是一条通栏色带，圆角由卡片自身裁剪处理。
    Card(modifier = modifier.fillMaxWidth()) {
        content()
    }
}
