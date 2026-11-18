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
import io.github.aw1y2z.sesame.ui.HostBridge
import androidx.activity.compose.rememberLauncherForActivityResult
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
import io.github.aw1y2z.sesame.ui.theme.SesameTabPager
import io.github.aw1y2z.sesame.ui.theme.sesameFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.Agreement
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.theme.SesameNavBarHeight
import io.github.aw1y2z.sesame.ui.theme.SesameNavBar
import io.github.aw1y2z.sesame.ui.theme.SesameNavItem
import io.github.aw1y2z.sesame.ui.theme.SesamePageTitleBar
import io.github.aw1y2z.sesame.ui.theme.SesamePermissionNotice
import io.github.aw1y2z.sesame.ui.theme.SesameScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameGlassDock
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.LanguageUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.io.File
import kotlin.math.roundToInt

class MiuixMainActivity : MiuixBaseActivity() {

    var runTypeText by mutableStateOf("")

    /** 可观察的激活状态：HomeTab 等页面订阅它，onServiceBind/广播更新时自动重组刷新 */
    var uiRunType by mutableStateOf<RunType>(RunType.DISABLE)
    var hasPermission by mutableStateOf(false)

    /** 是否已同意条款：界面与 onResume 共用同一份状态，避免两处判断不一致 */
    private var agreed by mutableStateOf(Agreement.isAccepted())

    /** 统计分页：首项是「全部账号合计」，其后按账号；由 refreshStatistics() 重新读取 */
    var statisticsPages by mutableStateOf<List<StatisticsPage>>(emptyList())

    /** 统计版本号：load / 广播刷新后自增,首页 StatisticsTable 订阅它来触发重组 */
    var statisticsVersion by mutableStateOf(0)

    /**
     * 账号列表版本号：onResume 时自增，配置页的账号列表订阅它重新扫描 config 目录。
     * <p>不这样做的话，那个列表会被 `remember` 缓存死 —— 删掉某个账号后回到配置页，
     * 那一行仍然在，必须切到别的 Tab 再切回来（Composable 重新组合）才消失。
     */
    var configAccountsVersion by mutableStateOf(0)

    private val handler = Handler(Looper.getMainLooper())
    private var isClick = false
    // 标记是否已通过系统设置页请求过权限，用于 onResume 中检测用户是否已授权
    var hasRequestedPermission by mutableStateOf(false)

    /** 激活探测已重试次数,上限见 MAX_RUN_TYPE_PROBE_TIMES */
    private var runTypeProbeTimes = 0

    private lateinit var titleRunner: Runnable

    init {
        /** 激活探测:仅当状态仍为 DISABLE 才显示未激活并周期性重试,避免覆盖晚到的 onServiceBind */
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
         * 自动权限引导的「已提示过」标记。
         * 存**模块私有** SharedPreferences（`/data/data/<pkg>/shared_prefs`）——
         * 它不需要外部存储权限，所以在还没拿到文件权限时也能正常读写。
         */
        private const val PERMISSION_PREFS = "sesame_ui"
        private const val KEY_PERMISSION_PROMPTED = "permission_prompted"

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
            } catch (e: Throwable) {
                // 取不到市场名就退回内部型号：显示会退化（小米/红米尤其明显），异常要留痕
                Log.debug("getDeviceDisplayName: " + e)
            }
            return Build.MODEL ?: Build.DEVICE ?: ""
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null) {
                when (action) {
                    "io.github.aw1y2z.sesame.status" -> {
                        // 回包必须带本机已配对的令牌：缺失或对不上就是别人伪造的探活，忽略
                        val token = intent.getStringExtra("token")
                        if (token == null || token != HostBridge.getToken(context)) {
                            Log.i("status reply without valid token, ignored")
                            return
                        }
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
        // 首帧就取真实权限值：hasPermission 默认 false，不预置会让顶栏的
        // 「缺少文件权限」提示条在第一帧闪一下（有权限的机器上尤其明显）
        hasPermission = PermissionUtil.checkFilePermissions(this)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        setAppContent {
            // 未同意协议只渲染门禁页：此阶段不碰宿主、不读统计，已同意的用户行为完全不变
            if (agreed) {
                MainScreen(this)
            } else {
                AgreementScreen(
                    onAgree = {
                        agreed = true
                        // onResume 在未同意时提前返回，这里补一次配对、激活探测与统计读取
                        HostBridge.ensurePaired(this)
                        probeRunType()
                        checkPermissionAndRefresh()
                    },
                    onExit = { finishAffinity() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 上游 v1.6.1：界面与副作用共用同一份判断，未同意协议前不产生任何副作用
        agreed = Agreement.isAccepted()
        if (!agreed) {
            handler.removeCallbacks(titleRunner)
            return
        }
        // 回到前台就重扫账号列表：删掉/新增账号后，从设置页返回能立刻看到变化，
        // 不必再切到别的 Tab 再切回来（详见 configAccountsVersion 的说明）
        configAccountsVersion++
        // 回到前台补一次配对：首次启动时宿主多半没在跑，用户开完支付宝再回来就通了
        HostBridge.ensurePaired(this)
        probeRunType()
        checkPermissionAndRefresh()
    }

    /** 激活状态探测独立于存储权限：先清空旧回调，避免 titleRunner 重复累积 */
    private fun probeRunType() {
        if (RunType.DISABLE != ViewAppInfo.getRunType()) return
        handler.removeCallbacks(titleRunner)
        runTypeProbeTimes = 0
        sendQueryBroadcast()
        handler.postDelayed(titleRunner, 3000)
    }

    /**
     * 检查文件权限：已授权也要重读统计，否则从配置页或后台返回首页仍是旧值。
     */
    private fun checkPermissionAndRefresh() {
        if (hasRequestedPermission) {
            hasRequestedPermission = false
        }
        // 每次 resume 都重取真实权限值并据此刷新。
        // 旧逻辑只在「无权限 → 有权限」的转变瞬间才调 refreshStatistics()；而权限早已授予的设备上
        // onCreate 就把 hasPermission 置 true，转变永远不会发生 → 正常启动路径加载不到 statistics.json，
        // 首页统计恒为 0（盘里明明有数据，K80 Pro 实测：json 有 58690，界面全 0）。
        val granted = PermissionUtil.checkFilePermissions(this)
        hasPermission = granted
        if (granted) {
            refreshStatistics()
        } else {
            autoPromptPermissionOnce()
        }
    }

    /**
     * 装机后自动引导一次「所有文件访问权限」。
     *
     * **为什么需要**：配置和日志写在**宿主包名的 media 目录**
     * （`/sdcard/Android/media/<宿主包>/sesame-M/`），Android 11+ 访问它必须持有该权限；
     * 而**卸载重装会被系统清空、且系统不会自动再询问**。用户看到的现象只是「设置改了不保存」，
     * 只能靠猜 —— 所以在装机后第一次打开时主动跳一次系统设置页。
     *
     * **只跳一次**：标记存在模块私有 SharedPreferences（不需要外部存储权限），之后不再自动跳。
     * 否则每次打开都被弹走，只想先看看界面的用户会很烦；后续靠顶部的常驻提示条，用户主动点才跳。
     */
    private fun autoPromptPermissionOnce() {
        val prefs = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PERMISSION_PROMPTED, false)) {
            return
        }
        prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
        // 稍等再跳：onResume 紧跟 onCreate，立刻 startActivity 会和启动动画打架、闪一下
        handler.postDelayed({ PermissionUtil.checkOrRequestFilePermissions(this) }, 450)
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
     * 重新加载统计并刷新首页。
     * <p>靠可观察的 statisticsVersion 触发重组:静态单例变化不会重组,首页会一直是 0。
     */
    fun refreshStatistics() {
        if (!hasPermission) return
        try {
            // 统计按账号分文件，界面一次读全部账号：首项是合计（默认显示），其后是各账号。
            // 不再读单例：界面进程没有「当前账号」的概念，读单例只会拿到没有归属的那一份。
            val perAccount = FileUtil.listAccountStatisticsUids().map { uid ->
                StatisticsPage(uid, UserIdMap.getAccountLabel(uid) ?: uid, Statistics.readFrom(uid))
            }
            val total = Statistics.aggregate(perAccount.map { it.statistics })
            statisticsPages = listOf(StatisticsPage(null, "全部账号合计", total)) + perAccount
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
        isClick = true
        HostBridge.send(this, "status", null, null, false)
    }

    /** 向支付宝进程查询本模块注入状态（不弹 Toast），由 titleRunner 周期性调用 */
    fun sendQueryBroadcast() {
        HostBridge.send(this, "status", null, null, false)
    }

    /**
     * 让注入进程整体重启（重新初始化并重挂 hook），用于必须重挂的开关如「使用新接口」；
     * 只重载 AppConfig 的 broadcastReloadConfig() 不够。
     */
    fun broadcastRestart() {
        HostBridge.send(this, "restart", null, null, false)
    }

    /** 通知支付宝进程重载共享配置（日志开关等），使开关在注入进程中即时生效 */
    fun broadcastReloadConfig() {
        HostBridge.send(this, "reloadConfig", null, null, false)
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
        } catch (e: Exception) {
            // 未注册成功时注销会抛，属正常路径，留痕即可
            Log.debug("unregisterReceiver: " + e)
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

    // 底部分页容器改由 SesameTabPager 统一承载：
    // - MD 风格 → MochiPeerTransition（平级弹簧切换，GitHub demo 首页效果）
    // - MIUIX → 原 HorizontalPager（整页平滑滑动 + pagerState 双向同步锁，观感零变化）
    // 分页状态机（含原 isProgrammaticScroll 锁）已随之下沉到桥接层，selectedTab 仍是唯一真源。

    // 底栏液态玻璃悬浮效果：玻璃面（navBar 外层 Box）采样「内容层」（HorizontalPager）做折射。
    // 开关存于 AppConfig.liquidGlassNavBar；关闭时回落为普通底栏，保持历史观感不变。
    //
    // ⚠️ 这里用 **可观察 state**（rememberSaveable），而不是每帧去读 AppConfig 的普通 Java 字段：
    // 玻璃开关原本在设置页里用 `activity.recreate()` 让新值生效 —— 那会**销毁并重建整个
    // Activity**，视觉上就是"界面闪一下"，是这个开关独有的问题（别的开关改的是数值，
    // 重建不可见）。改成 state 后，设置页改开关 → 这里自动重组 → **不闪**。
    // 初始值仍从 AppConfig 取；rememberSaveable 让 recreate（如切界面风格）后能恢复。
    // ⚠️ 用 remember（不是 rememberSaveable）：它是**配置的镜像**，recreate 后必须
    // 重新从 AppConfig 读最新值。若用 rememberSaveable，会把重建前的旧值恢复回来 ——
    // 例如「恢复完整备份」改了配置再 recreate，界面却仍按旧值渲染（配置与实际不符）。
    // （对比：selectedTab 用 rememberSaveable 是对的，页签选择不属于配置。）
    var glassEnabled by remember { mutableStateOf(AppConfig.INSTANCE.liquidGlassNavBar != false) }
    // 供设置页回写：切换开关时同时更新 AppConfig（落盘）与这里的 state（立即生效）
    val onGlassEnabledChange: (Boolean) -> Unit = { newValue ->
        AppConfig.INSTANCE.liquidGlassNavBar = newValue
        AppConfig.save()
        glassEnabled = newValue
    }
    // Kyant0 Backdrop 的「折射源」句柄。内容层挂 Modifier.layerBackdrop(dockBackdrop) 把页面
    // 录进它的 GraphicsLayer，底栏再用 drawBackdrop(dockBackdrop) 采样这一层。
    val dockBackdrop = rememberLayerBackdrop()
    // ⚠️ 切玻璃开关时要把**内容层与玻璃面**一起换掉：Modifier 链是构造时定下的，
    // 而 layerBackdrop/drawBackdrop 共享同一个句柄才能采样到彼此。
    // 这里给内容层的 Modifier 按 glassEnabled 取值即可（不重建句柄）：
    // 关闭时内容层不挂 layerBackdrop，玻璃面也不挂 drawBackdrop，两边自然脱钩。


    // 底栏随滚动显隐（参考 iOS/Chrome 的「上滑收起、下滑唤出」）：
    //   手指上滑 = 内容继续往下走 = 当前页 ScrollState.value 增大 → 收起；
    //   手指下滑 = value 减小、或回到顶部(value<=0) → 唤出。
    // 四个 Tab 各有一份 ScrollState，这里只订阅「当前页签」那份；selectedTab 变化时
    // activeScroll 换对象，LaunchedEffect 以它为 key 自动重订阅，避免跨页残留旧方向的抖动。
    val activeScroll = scrollOf(selectedTab)
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
            Column {
                SesamePageTitleBar(
                    // 标题与底部导航项同源（MAIN_TABS），不再各维护一份、靠顺序隐式对齐
                    title = MAIN_TABS.getOrElse(selectedTab) { MAIN_TABS.first() }.title,
                    scrolled = titleBarScrolled
                )
                // 没拿到存储权限时，任何设置都存不下来（界面还会回弹）——这是**功能不可用**级别的状态。
                // 只在首页提示：它属于「首次装机出现一次、处理完就消失」的状态，
                // 铺到每个 Tab 会变成噪音（四个页面各顶一条红条，比问题本身还显眼）。
                if (!activity.hasPermission && selectedTab == 0) {
                    SesamePermissionNotice(
                        onClick = { PermissionUtil.checkOrRequestFilePermissions(activity) }
                    )
                }
            }
        },
        bottomBar = {
            val navBar: @Composable () -> Unit = {
                SesameNavBar(
                    // 图标 + 文案与顶栏标题同源，顺序即页码索引（见 MAIN_TABS 注释）
                    items = MAIN_TABS.map { SesameNavItem(it.icon, it.label) },
                    // 选中态绑 selectedTab（点击即定为目标页，不会经过中间页闪烁）；
                    // 手指滑动时第二个 LaunchedEffect 会把 selectedTab 跟到当前页，高亮仍跟随手指。
                    selected = selectedTab,
                    onSelect = {
                        selectedTab = it
                        // 切回首页时刷新数据统计，避免停留在旧值（对齐上游 v1.2.8）
                        if (it == 0) activity.refreshStatistics()
                    },
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
        SesameTabPager(
            tabCount = MAIN_TABS.size,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // 玻璃效果开启时，内容层铺满全屏并录制成「折射源」（layerBackdrop），
                    // 让悬浮底栏采样到身后的页面内容。
                    //
                    // ⚠️ 非玻璃分支**只取 top**（bottom 显式归零）：Scaffold 的 padding 里已经
                    // 含底栏高度（+ 系统导航栏 inset），若这里连 bottom 一起吃，滚动区会被抬高，
                    // 而下面 Column 里又再加一次底栏高度 —— 同一份高度被加了两遍，
                    // 实测底部空隙达 **216dp**（80dp 底栏 × 2 + 导航栏 inset），
                    // 表现就是「最后一行离底部太靠上、怎么滚都压不下去」（首页/日志/配置/设置四个 Tab 全中）。
                    if (glassEnabled) Modifier.layerBackdrop(dockBackdrop)
                    else Modifier.padding(
                        top = padding.calculateTopPadding(),
                        bottom = 0.dp
                    )
                )
                    ) { page ->
            val scroll = scrollOf(page)
            // 底部留白必须**等于底栏的总高**，而不是只有 Row 的高度。
            // ⚠️ 实测教训：底栏 = `navigationBarsPadding()`（系统手势区，本次实测 34dp）
            // + Row(SesameNavBarHeight=60dp) = **94dp**。我先前只按 60dp 留白，
            // 差的 34dp 正好把卡片下沿的 28dp 圆角塞到底栏底下 → 「最后一行被底栏切掉一截」。
            // 变量取不到 inset 高度（Compose MP 分支无 WindowInsets 查询 API 变通），
            // 故显式写成实测值；换机型/换导航模式时**按真机重新量这一行**。
            val bottomInsetDp = 34
                Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll, flingBehavior = sesameFlingBehavior())
                    // 顶部【不留白】：常驻标题栏已经给出「大标题 → 内容」的间距，
                    // 这里再留 12dp 会让第一个分组标题离大标题过远（实测到「主题」有 59dp）。
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .then(
                        // 玻璃开启时另走 100.dp：悬浮 dock 要留 > 84dp，
                        // 让卡片能从 dock 下方经过 —— 玻璃才有东西可折射。
                        if (glassEnabled) Modifier.padding(bottom = 100.dp)
                        else Modifier.padding(bottom = SesameNavBarHeight + bottomInsetDp.dp)
                    )
                    .then(
                        // 玻璃开启时内容层是「全屏铺满」的（要当折射源），所以拿不到 Scaffold 给的
                        // topBar 内边距 —— 必须自己补顶栏高度，否则内容会滑到常驻标题栏底下被压住。
                        // 非玻璃模式由上面那条 padding(top=...) 统一处理，这里不重复加。
                        if (glassEnabled) Modifier.padding(top = padding.calculateTopPadding())
                        else Modifier
                    )
            ) {
                when (page) {
                    0 -> HomeTab(activity)
                    1 -> LogsTab(activity)
                    2 -> ConfigTab(activity)
                    3 -> SettingsTab(
                        activity = activity,
                        glassEnabled = glassEnabled,
                        onGlassEnabledChange = onGlassEnabledChange
                    )
                }
            }
        }
    }
}

/**
 * 首页统计分页的一项：`userId == null` 表示「全部账号合计」。
 * <p>上游 6aad0eb9 起统计按账号隔离（`Statistics.readFrom(uid)`），首页按账号循环分页展示。
 */
data class StatisticsPage(val userId: String?, val label: String, val statistics: Statistics)

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
