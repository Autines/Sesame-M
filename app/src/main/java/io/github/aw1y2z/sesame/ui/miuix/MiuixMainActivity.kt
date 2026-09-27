package io.github.aw1y2z.sesame.ui.miuix

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
import androidx.compose.foundation.layout.Box

// 液态玻璃（Kyant0/Backdrop 2.0.1，Maven Central）—— 本文件只负责创建「折射源」：
//   内容层用 Modifier.layerBackdrop 把页面录进 backdrop，悬浮底栏再采样它。
//   玻璃面本身（模糊/折射/色散/高光配方）在 ui.theme 的 SesameGlassDock 里，本文件不再直接调库效果函数。
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.theme.SesameNavBar
import io.github.aw1y2z.sesame.ui.theme.SesameNavItem
import io.github.aw1y2z.sesame.ui.theme.SesamePageTitleBar
import io.github.aw1y2z.sesame.ui.theme.SesameScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameGlassDock
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.LanguageUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.flow.collect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import java.util.Calendar
import io.github.aw1y2z.sesame.ui.miuix.tabs.HomeTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.LogsTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.ConfigTab
import io.github.aw1y2z.sesame.ui.miuix.tabs.SettingsTab

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
 * 一级页元数据：**顺序 = 页码索引 = 底部导航项顺序 = 顶栏标题来源**，四者必须一致。
 *
 * 集中在这一处，是为了消除原先的错位风险 —— 改代码前，这四件事分散在四处
 * （顶栏标题的 `pageTitles`、导航项的 `SesameNavItem` 列表、两处 `when (page)` 的滚动状态映射、
 * 以及 `rememberPagerState { 4 }` 里的硬编码页数），全部靠"顺序对上"隐式维持；
 * 将来插一页、换一次顺序，漏改任何一处都会静默错位。
 */
private data class MainTab(val title: String, val label: String, val icon: ImageVector)

private val MAIN_TABS = listOf(
    MainTab(title = "Sesame-M", label = "首页", icon = Icons.Filled.Home),
    MainTab(title = "日志", label = "日志", icon = Icons.Filled.Description),
    MainTab(title = "配置", label = "配置", icon = Icons.Filled.Tune),
    MainTab(title = "设置", label = "设置", icon = Icons.Filled.Settings)
)

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

    // 页码 → 当前页滚动状态。原先这套 `when (page) { 0/1/2/else }` 映射在文件里**写了两遍**
    // （订阅顶栏/底栏状态时一次、pager 内容里取一次），改一处漏一处会让底栏按旧页滚动显隐。现只此一份。
    val pageScrolls = remember(homeScroll, logsScroll, configScroll, settingsScroll) {
        listOf(homeScroll, logsScroll, configScroll, settingsScroll)
    }
    fun scrollOf(page: Int) = pageScrolls.getOrElse(page) { settingsScroll }

    // 底部 Tab 用 HorizontalPager 承载：左右滑动切页带过渡动画，切走再切回各页滚动位置不变。
    // pagerState 与 selectedTab 双向同步——点底部导航 → 平滑滚到对应页；手指滑动 → 高亮跟随。
    // ⚠️ 关键修复：点导航用 animateScrollToPage 跨多页滑动时，pagerState.currentPage 会依次经过中间页，
    // 若直接回写 selectedTab 会反向取消动画、把 pager 卡在半页（"界面卡在一部分"）。
    // 因此用 isProgrammaticScroll 锁：程序化滑动期间不接受 pager 回灌的页码，避免双向 effect 打架。
    val pagerState = rememberPagerState(initialPage = selectedTab) { MAIN_TABS.size }
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

    // 底栏液态玻璃悬浮效果：玻璃面（navBar 外层 Box）采样「内容层」（HorizontalPager）做折射。
    // 开关存于 AppConfig.liquidGlassNavBar；关闭时回落为普通底栏，保持历史观感不变。
    val glassEnabled = AppConfig.INSTANCE.liquidGlassNavBar != false
    // Kyant0 Backdrop 的「折射源」句柄。内容层挂 Modifier.layerBackdrop(dockBackdrop) 把页面
    // 录进它的 GraphicsLayer，底栏再用 drawBackdrop(dockBackdrop) 采样这一层。
    val dockBackdrop = rememberLayerBackdrop()

    // 底栏随滚动显隐（参考 iOS/Chrome 的「上滑收起、下滑唤出」）：
    //   手指上滑 = 内容继续往下走 = 当前页 ScrollState.value 增大 → 收起；
    //   手指下滑 = value 减小、或回到顶部(value<=0) → 唤出。
    // 四个 Tab 各有一份 ScrollState，这里只订阅「当前页签」那份；pagerState.currentPage 变化时
    // activeScroll 换对象，LaunchedEffect 以它为 key 自动重订阅，避免跨页残留旧方向的抖动。
    val activeScroll = scrollOf(pagerState.currentPage)
    var navBarHidden by remember { mutableStateOf(false) }
    LaunchedEffect(activeScroll) {
        var prev = activeScroll.value
        snapshotFlow { activeScroll.value }.collect { v ->
            val delta = v - prev
            prev = v
            when {
                // 顶部永远露出，避免"明明到顶了还藏着一半"
                v <= 0 -> navBarHidden = false
                // 阈值 6px：滤掉惯性滚动里 ±1 的抖动，避免底栏高频闪动
                delta > 6 -> navBarHidden = true
                delta < -6 -> navBarHidden = false
            }
        }
    }
    // 收起 = 下移一个 dock 的高度（64dp 高 + 20dp 外边距，取 96dp 保证完全出屏）+ 淡出
    val navBarOffset by animateDpAsState(
        targetValue = if (glassEnabled && navBarHidden) 96.dp else 0.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "navBarOffset"
    )
    val navBarAlpha by animateFloatAsState(
        targetValue = if (glassEnabled && navBarHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "navBarAlpha"
    )

    // 顶部常驻大标题栏（对标 SukiSU Ultra 的实机效果）：标题钉在顶部不随滚动移动，
    // 滚动后顶栏底色微微加深、把从下方穿过的内容压住。
    // 「是否已滚动」从当前页 ScrollState 派生 —— 用 derivedStateOf 只在**跨过阈值**时触发重组，
    // 不会因为滚动时每像素变化把整个 Scaffold 反复重组；以 activeScroll 为 key，切页时重新派生。
    val titleBarScrolled by remember(activeScroll) { derivedStateOf { activeScroll.value > 1 } }

    SesameScaffold(
        topBar = {
            SesamePageTitleBar(
                // 标题与底部导航项同源（MAIN_TABS），不再各维护一份、靠顺序隐式对齐
                title = MAIN_TABS.getOrElse(selectedTab) { MAIN_TABS.first() }.title,
                scrolled = titleBarScrolled
            )
        },
        bottomBar = {
            val navBar: @Composable () -> Unit = {
                SesameNavBar(
                    // 图标 + 文案与顶栏标题同源，顺序即页码索引（见 MAIN_TABS 注释）
                    items = MAIN_TABS.map { SesameNavItem(it.icon, it.label) },
                    // 选中态绑 selectedTab（点击即定为目标页，不会经过中间页闪烁）；
                    // 手指滑动时第二个 LaunchedEffect 会把 selectedTab 跟到当前页，高亮仍跟随手指。
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    // 玻璃开启时导航栏自身不画实色底、且切成紧凑胶囊形态，外观交给玻璃面
                    transparent = glassEnabled,
                    compact = glassEnabled
                )
            }
            if (glassEnabled) {
                // 玻璃的配方（模糊/折射/色散/tint/高光）收敛在 ui.theme 的 SesameGlassDock 里，
                // 这里只负责把导航栏与两个滚动收放动画量传进去。
                SesameGlassDock(
                    backdrop = dockBackdrop,
                    offsetY = navBarOffset,
                    alpha = navBarAlpha
                ) {
                    navBar()
                }
            } else {
                navBar()
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // 玻璃效果开启时，内容层铺满全屏并录制成「折射源」（layerBackdrop），
                    // 让悬浮底栏采样到身后的页面内容。
                    if (glassEnabled) Modifier.layerBackdrop(dockBackdrop)
                    else Modifier.padding(padding)
                )
        ) { page ->
            val scroll = scrollOf(page)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    // 顶部【不留白】：常驻标题栏已经给出「大标题 → 内容」的间距，
                    // 这里再留 12dp 会让第一个分组标题离大标题过远（实测到「主题」有 59dp）。
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .then(
                        // 玻璃开启时：悬浮 dock 占据底部 84dp（行高 56 + 内边距 4×2 + 距屏底 20），
                        // 底部留白必须 > 84dp 才能让最后一项（设置页「关于」）完整露出、滑得到，
                        // 否则会被 dock 盖住。滚动过程中卡片仍会从 dock 下方经过 → 玻璃才有东西可折射。
                        if (glassEnabled) Modifier.padding(bottom = 100.dp)
                        else Modifier.padding(bottom = padding.calculateBottomPadding())
                    )
                    .then(
                        // 玻璃开启时内容层是「全屏铺满」的（要当折射源），所以拿不到 Scaffold 给的
                        // topBar 内边距 —— 必须自己补顶栏高度，否则内容会滑到常驻标题栏底下被压住。
                        // 非玻璃模式由上面那条 Modifier.padding(padding) 统一处理，这里不重复加。
                        if (glassEnabled) Modifier.padding(top = padding.calculateTopPadding())
                        else Modifier
                    )
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
