package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.ui.theme.SesameEmptyState
import io.github.aw1y2z.sesame.ui.theme.sesameFlingBehavior
import io.github.aw1y2z.sesame.ui.theme.SesameFilterChip
import io.github.aw1y2z.sesame.ui.theme.SesameLogCard
import io.github.aw1y2z.sesame.ui.theme.SesameRunCard
import io.github.aw1y2z.sesame.ui.theme.SesameSearchField
import io.github.aw1y2z.sesame.ui.theme.SesameTopBar
import io.github.aw1y2z.sesame.ui.theme.sesameSurface
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File

/**
 * 二级日志页支持的日志类型。
 *
 * 「分类记录」的每一个条目 = 一个配置分组（ModelGroup），与配置页的分组一一对应。
 *
 * ⚠️ **分组 ≠ 日志文件**：分类日志文件是按**业务域**切的，一个文件里往往装着多个分组的
 * 模块日志 —— `Log.farm()` 同时被「庄园」（AntFarm）和「农场」（AntOrchard）两个模块调用，
 * 所以 farm 文件里两种日志都有；反过来，一个分组模块的日志也不只落在一个文件里
 * （`Log.i()` / `Log.record()` 写 runtime.log，`Log.farm()` 写 farm.log，双写时两边都有）。
 *
 * 所以分组页取数必须两步走：
 * ① 把可能承载本分组日志的文件**全部读进来**（见 [sourceFiles]）；
 * ② 再按正文的 `[模块名]` 前缀**收口到本分组**（见 [scopeEntriesToGroup]）。
 * 少任何一步都会出错 —— 只读"同名文件"会把别的分组的日志串进来（庄园页里出现农场日志）。
 *
 * 另有「系统记录」三个入口（抓包 / 异常 / 运行日志）不分组，各自对应自己的文件。
 */
enum class LogType(val displayName: String) {
    FOREST("森林记录"),
    FARM("庄园记录"),
    GOLDENBEANS("金豆记录"),
    OTHER("其他记录"),
    DEBUG("抓包记录"),
    ERROR("查看异常日志"),
    RUNTIME("查看运行日志"),

    // ── 分类记录中新增的分组：没有独立日志文件，从 runtime.log 聚合 ──
    STALL("新村记录"),
    ORCHARD("农场记录"),
    SPORTS("运动记录"),
    MEMBER("会员记录"),

    // ── 2026-10-09 补：上游 e811eb0f 把 6 个模块从「森林」组拆到 5 个新分组 ——
    //    神奇动物→物种、蚂蚁海洋→海洋、古树/保护生态→保护、绿色经营→经营、答题有奖→AI答。
    //
    // ⚠️ 为什么当初会漏：设置页的分组清单用 `ModelGroup.values()` **动态生成**、自动跟上；
    //    而本页是**手写清单**，且这个文件是**本地专属**（上游根本没有 MiuixLogViewerActivity），
    //    合并时既不冲突也不报错 → 静默漏掉。后果是这些模块的日志按模块名收口后
    //    被森林/其他页过滤掉、又没有新入口页可去 → **在日志页里彻底看不到**。
    //    以后再遇上游调整分组，必须同步这里（见 android-upstream-sync-merge 技能）。
    PROTECT("保护记录"),
    SPECIES("物种记录"),
    OCEAN("海洋记录"),
    GREEN_FINANCE("经营记录"),
    ANSWER_AI("AI答记录");

    /** 每次访问都重新取当日文件,避免跨天后路径过期 */
    val file: File
        get() = when (this) {
            FOREST -> FileUtil.getForestLogFile()
            GOLDENBEANS -> FileUtil.getGoldenBeansLogFile()
            FARM -> FileUtil.getFarmLogFile()
            OTHER -> FileUtil.getOtherLogFile()
            DEBUG -> FileUtil.getDebugLogFile()
            ERROR -> FileUtil.getErrorLogFile()
            // 无独立日志文件的分组统一读运行日志，再按模块聚合
            else -> FileUtil.getRuntimeLogFile()
        }

    /**
     * 该页面的取数来源。
     *
     * 分组页（「分类记录」）：runtime.log + 四个分类文件，**全都读**。
     * 之所以不能只读"同名文件"：分类文件按业务域切，farm 文件里同时有庄园与农场的日志，
     * 而庄园模块的 `Log.i` 日志又只在 runtime.log 里 —— 只读一个文件必然既串组又漏日志。
     * 读进来之后由 [scopeEntriesToGroup] 按 `[模块名]` 前缀收口到本分组。
     *
     * 系统记录页（抓包/异常/运行日志）：不分组，只读自己那一个文件。
     */
    val sourceFiles: List<File>
        get() = if (isGrouped) {
            listOf(
                FileUtil.getRuntimeLogFile(),
                FileUtil.getForestLogFile(),
                FileUtil.getFarmLogFile(),
                FileUtil.getGoldenBeansLogFile(),
                FileUtil.getOtherLogFile()
            )
        } else {
            listOf(file)
        }

    /**
     * 该页面对应的配置分组 code；系统记录类入口返回 null。
     * 分组视图的过滤都基于它展开。
     */
    val groupCode: String?
        get() = when (this) {
            FOREST -> ModelGroup.FOREST.code
            FARM -> ModelGroup.FARM.code
            GOLDENBEANS -> ModelGroup.GOLDENBEANS.code
            OTHER -> ModelGroup.OTHER.code
            STALL -> ModelGroup.STALL.code
            ORCHARD -> ModelGroup.ORCHARD.code
            SPORTS -> ModelGroup.SPORTS.code
            MEMBER -> ModelGroup.MEMBER.code
            PROTECT -> ModelGroup.PROTECT.code
            SPECIES -> ModelGroup.SPECIES.code
            OCEAN -> ModelGroup.OCEAN.code
            GREEN_FINANCE -> ModelGroup.GREEN_FINANCE.code
            ANSWER_AI -> ModelGroup.ANSWER_AI.code
            else -> null
        }

    /** 是否为按配置分组查看（「分类记录」里的条目） */
    val isGrouped: Boolean
        get() = groupCode != null

    companion object {
        /** 一级页跳转时携带的 extra key,值为 LogType.name */
        const val EXTRA_LOG_TYPE = "sesame_log_type"

        fun fromIntent(intent: Intent?): LogType {
            val name = intent?.getStringExtra(EXTRA_LOG_TYPE)
            return entries.firstOrNull { it.name == name } ?: RUNTIME
        }

        /**
         * 「分类记录」的展示顺序：**平铺**，顺序对齐 ModelGroup 的声明顺序
         * （基础不在此列：基础日志由运行日志承载）。
         *
         * 历史上这里是两级结构（森林/庄园/金豆/其他 为父项，新村/农场/运动/会员 缩进为子项），
         * 因为后者与父项**共用日志文件**（新村/农场 → farm.log，运动/会员 → other.log）、
         * 而开关是按文件给的就只能跟着父项走。2026-10-04 起每个分组都有了自己的写入开关
         * （见 `AppConfig.isEnableStallLog` 等，`Log.allowCategoryFile` 按模块名路由），
         * 「共用文件」不再意味着「共用一个开关」，子项因此全部提为一级、与自己平级展示。
         */
        val displayOrder: List<LogType>
            get() = listOf(
                FOREST, PROTECT, SPECIES, OCEAN, FARM, STALL, ORCHARD,
                GOLDENBEANS, SPORTS, MEMBER, GREEN_FINANCE, ANSWER_AI, OTHER
            )

        /**
         * 该分组对应的分类日志标签，用于**兜底归属**：
         * 条目没有 `[模块名]` 前缀时（旧版模块写下的日志），只能靠标签判断它属于哪一组。
         *
         * 没有分类标签的分组（新村/农场/运动/会员）返回 null —— 它们的日志必定带模块标记，
         * 兜底不到就是不归本组。
         */
        fun tagOf(groupCode: String): String? = when (groupCode) {
            ModelGroup.FOREST.code -> "FOREST"
            ModelGroup.FARM.code -> "FARM"
            ModelGroup.GOLDENBEANS.code -> "GOLDENBEANS"
            ModelGroup.OTHER.code -> "OTHER"
            else -> null
        }
    }
}

/**
 * 单条日志条目(可能含多行正文)。
 *
 * [module] 是从正文 `[模块名] ...` 前缀里解析出的模块归属，
 * 由 `Log.withModulePrefix()` 在模块执行线程内写入（见 ModelTask.mainRunnable）。
 * 界面进程自己写的日志（配置加载等）没有前缀，此处为 null。
 *
 * [source] / [sortKey] 只在**多文件合并**（分组页）时有意义：
 * 分组页要把 runtime.log 与四个分类文件合到一起，[source] 记来源文件名（去重与列表 key 用），
 * [sortKey] 是排序键（无时间戳的续行继承上一条的时间，保证合并后时间序不乱）。
 */
data class LogEntry(
    val lineNumber: Int,
    val time: String?,
    val tag: String?,
    val module: String?,
    val body: String,
    val source: String? = null,
    val sortKey: String = ""
)

/* ───────────────────── 按执行轮次查看 ─────────────────────
 * 一轮 = 从运行日志里的一条「开始执行」到下一轮「开始执行」之间的所有日志。
 * 边界只认运行日志（RUNTIME tag）的这三种形态，其余含「开始执行」字样的业务日志
 * （如「森林阅读📖开始执行…」「黄金票[开始执行]」）不算边界 —— 否则一轮会被切碎。
 */

/** 日志视图模式：按单条 / 按执行轮次 */
enum class LogViewMode { BY_ENTRY, BY_RUN }

/**
 * 一轮执行的聚合结果。
 *
 * [startKey] 是本轮首条日志的「来源文件-行号」，只用作 LazyColumn 的稳定 key ——
 * 分组页的数据来自 5 个文件，单看 [LogEntry.lineNumber] 会重复。
 */
data class LogRunEntry(
    val runIndex: Int,
    val startTime: String?,
    val startKey: String,
    val entries: List<LogEntry>
)

/**
 * 轮次开始行正则。三形态：`[账号N]开始执行`、`[账号N]开始执行分组【xx】任务`、
 * `[账号N]开始执行全部任务`。用「前缀 + 整行收尾」双重限定，避免误判业务日志。
 */
private val RUN_START_REGEX =
    Regex("""^\[账号\d+\]开始执行(?:\s*$|分组【[^】]*】任务\s*$|全部任务\s*$)""")

/**
 * 模块加载起点行正则：「模块初始化开始」。
 * 由 ApplicationHook 在主进程、AppConfig.load 之前打印，是主进程一次加载周期的最早日志。
 * 旧版模块没有这一行，所以对历史日志的轮次划分没有影响。
 */
private val INIT_START_REGEX = Regex("""^模块初始化开始\s*$""")

/** 运行日志的两类轮次锚点（均已按时间升序） */
private data class RuntimeAnchors(
    val runStarts: List<String>,
    val initStarts: List<String>
)

/**
 * 从运行日志**全文**提取轮次锚点：
 * ① 执行起点（`[账号N]开始执行…`）；② 模块加载起点（`模块初始化开始`）。
 *
 * 为什么要读全文、而不是复用页面上已加载的条目：页面只保留了尾部窗口
 * （[MAX_LOG_ENTRIES] / [MAX_TAIL_BYTES] 限制），而锚点必须覆盖全天每一轮，
 * 否则「森林/庄园/金豆」这些自身没有轮次标记的页面，早上的日志会被归到错误的一轮。
 * 读全文只在进页面时做一次（[remember] 缓存），当天文件量级下开销可忽略。
 */
private fun readRuntimeAnchors(): RuntimeAnchors {
    val file = LogType.RUNTIME.file
    if (!file.exists()) return RuntimeAnchors(emptyList(), emptyList())
    val runStarts = mutableListOf<String>()
    val initStarts = mutableListOf<String>()
    try {
        file.forEachLine(Charsets.UTF_8) { line ->
            val m = LOG_TIME_REGEX.find(line) ?: return@forEachLine
            val tag = m.groupValues[2].takeIf { it.isNotEmpty() }
            val body = m.groupValues[3]
            val time = m.groupValues[1].takeIf { it.isNotEmpty() } ?: return@forEachLine
            if (tag == "RUNTIME") {
                when {
                    RUN_START_REGEX.containsMatchIn(body) -> runStarts.add(time)
                    INIT_START_REGEX.containsMatchIn(body) -> initStarts.add(time)
                }
            }
        }
    } catch (_: Throwable) {
        // 读取失败时退化为无锚点（各页按单条显示），不影响原有查看能力
    }
    return RuntimeAnchors(runStarts.sorted(), initStarts.sorted())
}

/**
 * 把条目按「最近一次开始执行」归轮。
 *
 * 若两轮「开始执行」之间存在模块加载起点（进程重启后的加载阶段），该轮边界提前到
 * 加载起点，使加载阶段的日志归到本轮开头，而不是被算进上一轮尾巴（见 [effectiveRunStarts]）。
 *
 * 无锚点（当天还没跑过）时整批归为一轮，而非返回空 —— 否则刚装完/刚清完日志时
 * 切到按轮模式会一片空白，容易误判成功能坏了。时间比较基于 `HH:mm:ss.SSS` 的
 * 字典序，同一天内等价于时间序。
 */
private fun aggregateByRun(
    source: List<LogEntry>,
    runStarts: List<String>,
    initStarts: List<String>
): List<LogRunEntry> {
    val anchors = effectiveRunStarts(runStarts, initStarts)
    if (source.isEmpty()) return emptyList()
    if (anchors.isEmpty()) {
        val first = source.first()
        return listOf(
            LogRunEntry(1, first.time, "${first.source}-${first.lineNumber}", source)
        )
    }
    val buckets = MutableList(anchors.size) { mutableListOf<LogEntry>() }
    for (e in source) {
        val t = e.time
        // 找不到 <= t 的锚点（早于全部锚点的散落行）归入最早一轮
        val k = if (t == null) -1 else anchors.indexOfLast { it <= t }
        buckets[(if (k < 0) 0 else k)].add(e)
    }
    return buckets.mapIndexedNotNull { index, list ->
        if (list.isEmpty()) null
        else {
            val first = list.first()
            LogRunEntry(
                runIndex = index + 1,
                startTime = first.time,
                startKey = "${first.source}-${first.lineNumber}",
                entries = list.toList()
            )
        }
    }
}

/**
 * 加载起点与「开始执行」之间允许的最大间隔。
 * 只接受紧邻轮次开始前的加载标记，避免执行中途其它子进程的偶发标记把下一轮边界
 * 拖到本轮中间（表现为轮次卡片错位/消失）。正常冷启动加载到开始执行不足 1 秒，
 * 10 秒上限足够覆盖慢设备。
 */
private const val INIT_BOUNDARY_WINDOW_MS = 10_000L

/** `HH:mm:ss.SSS` → 当天毫秒数（同日有效）；格式异常返回 null */
private fun timeToMillis(t: String): Long? {
    return try {
        val parts = t.split(':', '.')
        if (parts.size != 4) return null
        (((parts[0].toLong() * 60 + parts[1].toLong()) * 60 + parts[2].toLong()) * 1000) + parts[3].toLong()
    } catch (_: Throwable) {
        null
    }
}

/**
 * 每轮的有效起点：默认取该轮「开始执行」时间；若开始执行前 [INIT_BOUNDARY_WINDOW_MS] 内
 * 存在模块加载起点（取离开始执行最近的一条），则把边界提前到该加载起点。
 * 第 1 轮不调整 —— 早于所有锚点的散落日志本就归入第 1 轮。
 * 旧版模块不打印「模块初始化开始」，initStarts 为空时行为与原来完全一致。
 */
private fun effectiveRunStarts(runStarts: List<String>, initStarts: List<String>): List<String> {
    if (runStarts.isEmpty() || initStarts.isEmpty()) return runStarts
    return runStarts.mapIndexed { index, start ->
        if (index == 0) {
            start
        } else {
            val prev = runStarts[index - 1]
            val startMs = timeToMillis(start) ?: return@mapIndexed start
            // initStarts 已升序：区间内最后一条即离开始执行最近的标记
            val marker = initStarts.lastOrNull { it > prev && it < start }?.takeIf { m ->
                val mMs = timeToMillis(m)
                mMs != null && startMs - mMs in 0..INIT_BOUNDARY_WINDOW_MS
            }
            marker ?: start
        }
    }
}

class MiuixLogViewerActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 「按分组收口日志」要读模型注册表（分组 -> 模块 -> 显示名）；注册表只在主界面里建过，
        // 直接进本页、或进程被杀后从最近任务恢复本页时它是空的 —— 空表会让过滤整段失效，
        // 页面退化成"运行日志全量"。用非破坏性初始化，取数前先确保它建好。
        Model.initAllModelIfNeeded()
        setAppContent {
            LogScreen(this, LogType.fromIntent(intent))
        }
    }
}

/**
 * 日志详情页:展示指定类目的全部条目卡片。
 * 仿 LSPosed 日志界面:每条目一张卡(标签 + 时间 + 正文)。
 *
 * 列表从底部开始排(reverseLayout),而列表初始位置就是最新一条,
 * 所以一打开页面看到的就是最新日志;之后每 [LOG_REFRESH_INTERVAL_MS] 检查一次日志文件,
 * 有新内容就刷新并跟到最新;上滑翻历史时暂停跟随(不会被新日志顶跑),滑回最新后自动恢复。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(activity: MiuixLogViewerActivity, logType: LogType) {
    val context = LocalContext.current
    // 取数来源：分组页是「runtime + 四个分类文件」，系统记录页是各自那一个文件
    val sources = remember(logType) { logType.sourceFiles }
    // 分组元数据：组内模块 + 分组日志标签（不随过滤变化，故只按 logType 记忆）
    // ⚠️ 必须在首次取数**之前**建好：取数时要靠它把本分组的日志先收口、再按窗口截断
    //    （顺序反了窗口会被别组日志占满，详见 loadLogEntries 的说明）。
    val groupFilter = remember(logType) { buildGroupFilter(logType.groupCode) }
    var entries by remember(logType) { mutableStateOf(loadLogEntries(sources, groupFilter)) }
    var revision by remember(logType) { mutableStateOf(0) }
    var browsingHistory by remember(logType) { mutableStateOf(false) }
    // 搜索文本
    var searchQuery by remember { mutableStateOf("") }
    // 模块过滤：日志正文 [模块名] 前缀里的模块名，null = 全部模块
    var selectedModule by remember(logType) { mutableStateOf<String?>(null) }
    // 视图模式：跟随设置页「日志按轮次查看」开关（在设置页切换后重进本页生效）
    val viewMode = if (AppConfig.INSTANCE.logViewByRun == true) LogViewMode.BY_RUN else LogViewMode.BY_ENTRY

    val listState = rememberLazyListState()

    /**
     * 当前页面要展示的条目（已完成分组收口）。
     *
     * **收口在取数时就做完了** —— [loadLogEntries] 内、窗口截断之前，此处只是取用、不再重复过滤。
     * ⚠️ 若拖到这一步才收口，窗口早被别组日志占满：实测森林组应有 544 条、只显示了 60 条。
     *
     * 为什么必须收口：分类文件按业务域切，farm 文件里同时装着庄园和农场的日志，
     * 直接展示就会串组（庄园页里出现农场日志）。收口依据见 [scopeEntriesToGroup]。
     */
    val scopedEntries = entries

    // 列表停下时按位置判断是否停在最新(索引 0):还能往回滚就说明用户翻到上面看历史了
    LaunchedEffect(listState, logType) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { browsingHistory = listState.canScrollBackward }
    }

    // 定时刷新:只读新增的字节并增量解析;有新条目才更新列表与滚动标记
    LaunchedEffect(logType) {
        var stamp = logFilesStamp(sources)
        while (true) {
            delay(LOG_REFRESH_INTERVAL_MS)
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                continue
            }
            val newStamp = logFilesStamp(sources)
            if (newStamp == null || newStamp == stamp) {
                continue
            }
            stamp = newStamp
            entries = withContext(Dispatchers.IO) { loadLogEntries(sources, groupFilter) }
            revision++
        }
    }

    // 有新日志时把视角钉回最新一条
    LaunchedEffect(revision) {
        if (!browsingHistory && entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 计算可见条目:根据搜索文本、模块、tag 三层过滤
    // ⚠️ 模块匹配走 [displayLabelOf]（含子标签细分）—— 与计数、卡片标签同源，
    //    否则会出现「点标签筛不出东西」的分裂（见 displayLabelOf 注释）。
    val filteredEntries = scopedEntries.filter { e ->
        val matchSearch = searchQuery.isBlank() ||
                (e.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                e.body.contains(searchQuery, ignoreCase = true)
        val matchModule = selectedModule == null || displayLabelOf(e, groupFilter.subLabels) == selectedModule
        matchSearch && matchModule
    }

    /**
     * 按轮次聚合后的列表（仅 [LogViewMode.BY_RUN] 用）。
     *
     * ⚠️ 聚合放在**搜索/模块过滤之后**：这样轮内条数就是「当前筛选条件下的条数」，
     * 与按条模式的列表内容严格一致 —— 反过来先聚合再过滤，会出现「点数变少了、
     * 展开却是全量」的错位。
     */
    val runtimeAnchors = remember { readRuntimeAnchors() }
    val runEntries = remember(filteredEntries, runtimeAnchors) {
        aggregateByRun(filteredEntries, runtimeAnchors.runStarts, runtimeAnchors.initStarts)
    }

    /**
     * 顶部模块选择条的内容：以**该分组声明的子模块**为骨架（按配置顺序），
     * 再并入日志里实际出现过的模块；有子标签的分组（运动/会员）再按正文特征细分。
     *
     * 为什么不只看「日志里出现过的模块」：分类日志（如 forest.log）里的每一行
     * 是否带 `[模块名]` 前缀，取决于写日志时模块有没有在运行线程里登记归属。
     * 用旧版模块写下的日志天然没有前缀 —— 若把「出现过多个模块」当作显示条件，
     * 选择条就会时有时无（同一页面对不同时期的日志表现不一致）。
     * 改成按分组声明固定展示后，森林组的「森林/保护/物种/海洋」恒定可见。
     *
     * ⚠️ **计数为 0 的项目不再展示**（2026-10-04）：老板没开「保护」这类功能时，
     * 它永远写不出日志，一个恒为 0 的 chip 只是噪音 —— 现在直接从筛选条里剔除。
     *
     * ⚠️ **单项目分组整条不显示**（2026-10-04）：庄园/新村/农场/金豆这类只有 1 个模块、
     * 且没有子标签的分组，标签条上只会孤零零一个 chip，纯噪音 → 由 [showFilterBar] 收起。
     *
     * 计数口径与列表筛选、卡片标签**同源**（都走 [displayLabelOf]）。之前这里只认
     * `[模块名]` 前缀，而列表还能靠「执行开始-X」标记归属，于是出现「标签写 0、
     * 列表里却有条目、点标签还筛不出东西」的分裂 —— 真机见过：物种计数 0，
     * 实际有 4 条执行记录。
     */
    val moduleCounts = remember(scopedEntries, groupFilter) {
        // 先按 [displayLabelOf] 归位（含子标签细分），再统计
        val actual = scopedEntries.mapNotNull { displayLabelOf(it, groupFilter.subLabels) }
            .groupBy { it }
            .mapValues { it.value.size }
        val ordered = LinkedHashMap<String, Int>()
        // ① 分组声明的模块按配置顺序排在前（计数为 0 的不收 —— 见上方说明）
        groupFilter.modules.mapNotNull { it.displayName }.forEach { name ->
            val count = actual[name] ?: 0
            if (count > 0) ordered[name] = count
        }
        // ② 本分组定义的子标签按声明顺序紧随其后（同样只收计数 > 0 的）
        groupFilter.subLabels.forEach { sub ->
            val count = actual[sub.label] ?: 0
            if (count > 0 && !ordered.containsKey(sub.label)) ordered[sub.label] = count
        }
        // ③ 日志里实际出现过、但既不在分组声明、也不在子标签里的名称追加在最后
        actual.entries.sortedByDescending { it.value }.forEach { (name, count) ->
            if (!ordered.containsKey(name)) ordered[name] = count
        }
        ordered
    }

    /**
     * 是否展示整条筛选条。
     *
     * **只有"有东西可筛"时才展示**：可选项目数 > 1。单项（庄园/新村/农场/金豆这类
     * 只有一个模块、且没子标签的分组）时标签条只是噪音 —— 一个孤零零的 chip 加一个
     * 「全部」，点了也没有第二种选择，直接收起。
     *
     * ⚠️ 判据用 [moduleCounts] 的**实际条目数**而不是"分组声明的模块数"：
     * 运动/会员这类有子标签的分组，声明的模块可能只有 1 个，但细分后会出现
     * 「运动 + 健康岛」「会员 + 芝麻粒 + 黄金票」多个可选项，这时**要**展示。
     */
    val showFilterBar = moduleCounts.size > 1

    // 选中的模块可能因日志刷新（该模块的记录被挤出窗口 / 被清除）而离开筛选条，
    // 此时筛选条上不再有对应 chip，但列表却仍被它过滤 —— 界面会自相矛盾。
    // 一旦发现选中项已不在可选项里，就地取消选中，回到「全部」。
    LaunchedEffect(moduleCounts, selectedModule) {
        val current = selectedModule
        if (current != null && !moduleCounts.containsKey(current)) {
            selectedModule = null
        }
    }

    Scaffold(
        topBar = {
            SesameTopBar(
                title = logType.displayName,
                onBack = { activity.finish() },
                onExport = {
                    val exported = FileUtil.exportFile(logType.file)
                    if (exported != null) {
                        ToastUtil.show(context, "已导出: " + exported.path)
                    } else {
                        ToastUtil.show(context, "导出失败")
                    }
                },
                // 分组页的「清除」= 只删本分组的日志行（数据是跨文件聚合来的，
                // 整文件清空会误伤：农场/新村/运动/会员页的主文件就是 runtime.log）。
                // 系统记录页是自己的独立文件，照旧整文件清空。
                onClear = {
                    if (logType.isGrouped) {
                        val removed = clearGroupLines(sources, groupFilter)
                        entries = loadLogEntries(sources, groupFilter)
                        searchQuery = ""
                        selectedModule = null
                        ToastUtil.show(
                            context,
                            if (removed) "已清除本分组日志" else "本分组没有可清除的日志"
                        )
                    } else if (FileUtil.clearFile(logType.file)) {
                        entries = loadLogEntries(sources, groupFilter)
                        searchQuery = ""
                        selectedModule = null
                        ToastUtil.show(context, "已清空")
                    }
                },
                onShare = if (logType == LogType.RUNTIME) {
                    {
                        val file = logType.file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, file.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                    }
                } else null
            )
        },
        containerColor = sesameSurface()
    ) { padding ->
        // ⚠️ bottom 不在 Column 这里吃：连 bottom 一起 padding(padding) 会把滚动区整体抬高，
        // 列表滚到底时最后一条下面会空出「底栏高度 + 一段空白」——
        // 视觉上就是老板说的「最后一行离底部太靠上」，且怎么滚都压不下去。
        // 所以这里先整体 padding，再把 bottom **显式归零**，让底栏那块高度
        // 改由 LazyColumn 的 contentPadding 承担（内容因此能滚进空白区）。
        Column(
            Modifier.fillMaxSize().padding(
                top = padding.calculateTopPadding(),
                bottom = 0.dp
            )
        ) {
            // ── 搜索框 ──────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SesameSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f)
                )
            }
            // ── 模块选择条 ──────────────────────────────────────────────
            // 只在**确有可选项**（细分后 > 1 个）时展示，见 [showFilterBar]。
            // 单项分组（庄园/新村/农场/金豆）整条收起，避免孤零零一个 chip 的噪音。
            //
            // 注：运行日志页过去还并排着一排「文件标签」筛选项（RUNTIME / FARM /
            // FOREST / OTHER …），它和本排是同一批业务域的两种写法（森林↔FOREST、
            // 庄园↔FARM），既重复又是全英文，已删除，各日志页统一只保留本排中文模块筛选。
            if (showFilterBar) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SesameFilterChip(
                        label = "全部",
                        count = scopedEntries.size,
                        selected = selectedModule == null,
                        onClick = { selectedModule = null }
                    )
                    for ((module, count) in moduleCounts) {
                        SesameFilterChip(
                            label = module,
                            count = count,
                            selected = selectedModule == module,
                            onClick = {
                                selectedModule = if (selectedModule == module) null else module
                            }
                        )
                    }
                }
            }
            // ── 日志列表 ────────────────────────────────────────────────
            if (filteredEntries.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (searchQuery.isNotEmpty() || selectedModule != null) {
                        // 走到这里只可能是「搜了关键词但没匹配到」—— 已选模块必定在列表里有条目，
                        // 因为计数为 0 的模块根本不会出现在筛选条上、也就选不中（见 moduleCounts）。
                        SesameEmptyState(
                            text = "无匹配日志",
                            hint = "换个关键词或清空筛选条件试试"
                        )
                    } else if (logType.isGrouped) {
                        // 分组页空了通常不是"坏了"，而是本组模块这次没产生日志
                        SesameEmptyState(
                            text = "该分组暂无日志",
                            hint = "对应模块执行过一次后就会有记录"
                        )
                    } else {
                        SesameEmptyState(
                            text = "(空)",
                            hint = "该日志文件还没有内容"
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // ⚠️ reverseLayout = true 时，contentPadding 的 top/bottom 会**互换**：
                    // Compose 把 start 当作列表的"起始边"，而反转布局的起始边是**底部**。
                    // 所以想让「列表底部（视觉上的最后一条那侧）」贴到屏幕底，
                    // 得把底栏高度填进 **top** 这一档。
                    // （不清这个的话，改了 padding 反而会在错误的一侧留白。）
                    contentPadding = PaddingValues(
                        top = padding.calculateBottomPadding() + 8.dp,
                        bottom = 8.dp
                    ),
                    flingBehavior = sesameFlingBehavior()
                ) {
                    // 按轮模式用聚合后的轮次列表，按条模式用原始单条列表
                    val displayList: List<Any> = if (viewMode == LogViewMode.BY_RUN) {
                        runEntries.asReversed()
                    } else {
                        filteredEntries.asReversed()
                    }
                    itemsIndexed(
                        displayList,
                        key = { _, item ->
                            when (item) {
                                is LogEntry -> "${item.source}-${item.lineNumber}-${item.hashCode()}"
                                // key 与内容解耦：轮次卡片若把 startKey（含行号）纳入 key，
                                // 尾部窗口丢弃旧条目时 key 会变，锚点失效、卡片被重建（视图自己跳）
                                is LogRunEntry -> "run-${item.runIndex}"
                                else -> item.hashCode().toString()
                            }
                        }
                    ) { _, item ->
                        when (item) {
                            is LogEntry -> SesameLogCard(
                                tag = logTagOf(item, groupFilter.subLabels),
                                time = item.time ?: "",
                                body = item.body
                            )
                            is LogRunEntry -> {
                                // 折叠状态按轮独立保存：切换筛选导致列表重建时，已展开的轮不会跟着收起
                                var expanded by remember(item.startKey) { mutableStateOf(true) }
                                SesameRunCard(
                                    title = "第 ${item.runIndex} 轮" +
                                            (item.startTime?.let { "  $it" } ?: ""),
                                    count = item.entries.size,
                                    expanded = expanded,
                                    onToggle = { expanded = !expanded },
                                    details = item.entries.map { e ->
                                        buildString {
                                            if (e.time != null) append(e.time).append(" ")
                                            val label = logTagOf(e, groupFilter.subLabels)
                                            if (label.isNotBlank()) append(label).append(": ")
                                            append(e.body)
                                        }
                                    }
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────────── 按配置分组的日志聚合 ───────────────────── */

/**
 * 分组视图的过滤元数据。
 *
 * [modules] 为该分组下的模块，key 是 `ModelConfig.code`（类简名，如 AntFarm），
 * value 是该模块的显示名（`Model.getName()`，如「庄园」）。
 *
 * **匹配键用显示名而不是类简名**：`ModelTask.mainRunnable` 写的是
 * `执行开始-` + `task.getName()`，即 `Model.getName()` 的返回值（中文短名），
 * 所以 runtime.log 里看到的是「执行开始-庄园」，而不是「执行开始-AntFarm」。
 *
 * [groupTags] 是旧日志的**兜底归属**依据（见 [scopeEntriesToGroup]）：
 * 有模块前缀的条目一律按前缀精确归属，只有在没有前缀时才看标签。
 *
 * [subLabels] 是本分组的**子标签**定义（见 [SubLabel]）：把同一模块的日志按正文特征再细分
 * （如运动组的「健康岛」、会员组的「芝麻粒/黄金票」）。为空表示本分组不细分。
 */
data class GroupFilter(
    val modules: List<ModuleRef>,
    val groupTags: Set<String>,
    val subLabels: List<SubLabel> = emptyList()
) {
    val isEmpty: Boolean get() = modules.isEmpty() && groupTags.isEmpty()

    fun displayNameOf(code: String): String? = modules.firstOrNull { it.code == code }?.displayName
}

/** 模块在分组视图里的引用：类简名 + 显示名 */
data class ModuleRef(val code: String, val displayName: String)

/**
 * 分组内的**子标签**：把同一个模块写下的日志，按**正文特征**再细分成几类。
 *
 * 为什么需要它：日志的模块前缀（`[运动]` / `[会员]`）由 `Log.setCurrentModule(task.getName())`
 * 在模块执行时一次性设定，**一个模块只能有一个前缀**。可有些模块内部装着几套彼此独立的业务
 * （`AntSports` 里既有运动任务也有「悦动健康岛」；`AntMember` 里既有会员任务也有芝麻粒、黄金票），
 * 它们全被写成一个前缀，界面上就混在一起了。
 *
 * 不能靠"改写入前缀"来拆：那样**历史日志拆不开**（旧日志一律写的是老前缀），
 * 而且要动业务代码。所以这里改为**读取时按正文特征重新归属** —— 纯 UI 层，
 * 新旧日志一视同仁，业务零改动。
 *
 * @param label 界面上显示的标签名（如「健康岛」「芝麻粒」）
 * @param keywords 命中任一关键词即归入本子标签（大小写不敏感）
 */
data class SubLabel(val label: String, val keywords: List<String>)

/**
 * 各分组的子标签定义；未列出的分组没有子标签（整条筛选条按单模块处理）。
 *
 * ⚠️ 关键词必须**只在目标业务的日志里出现**，不能选那种在别处也用的通用词。
 * 血的教训①：「健康能量」看着像健康岛专属，其实「好友大战🉐抢购…花费[N]健康能量」也是
 * 运动体系里的一句，用它当关键词会把好友大战误判进健康岛。改用 `悦动健康🚑️`
 * 这个稳定的文案前缀（健康岛所有日志都带它）。
 *
 * ⚠️ 血的教训②（2026-10-05）：**业务名会出现在"任务名"里**，不能拿业务名当关键词。
 * 曾经用「芝麻信用」当关键词，结果会员任务里有个任务就叫「逛一逛芝麻信用」——
 * 那条「会员任务🎖️完成[逛一逛芝麻信用]」被误判进芝麻粒子标签。
 * 修法：给关键词补上该业务**专属文案前缀里的 emoji**（芝麻信用 → `芝麻信用💳` / `芝麻信用💌`），
 * 业务名可以出现在任务名里，但完整的前缀（含 emoji）只会在该业务自己的日志里出现。
 *
 * ⚠️ 顺序有意义：**先匹配到的优先**。当前「芝麻炼金」与「芝麻粒」「芝麻信用」互不为子串、
 * 暂无冲突，但新增关键词时要留意「短词会吃掉长词」的陷阱（如加「芝麻」就会吞掉另两个）。
 */
private val GROUP_SUB_LABELS: Map<String, List<SubLabel>> = mapOf(
    ModelGroup.SPORTS.code to listOf(
        SubLabel("健康岛", listOf("悦动健康", "健康岛"))
    ),
    ModelGroup.MEMBER.code to listOf(
        SubLabel("黄金票", listOf("黄金票")),
        // 关键词取「各业务日志的稳定文案前缀」而非业务名，避免被任务名蹭中：
        // 收芝麻粒🙇🏻‍♂️ / 芝麻粒🥇跳过 / 攒芝麻分🎁🎖️🧾 / 芝麻树🌳 / 芝麻炼金⚗️⏩ / 芝麻信用💳💌
        // ⚠️「芝麻粒」这个短词要保留：`芝麻粒🥇跳过：…` 之外的收粒日志也带它；它不会误伤
        // 会员任务，因为会员任务文案是「会员任务🎖️完成[任务名]」，正文里没有独立的「芝麻粒」。
        SubLabel("芝麻粒", listOf("芝麻粒", "芝麻信用💳", "芝麻信用💌", "芝麻炼金", "攒芝麻分", "芝麻树"))
    )
)

/** 取某分组的子标签定义；没有则返回空表 */
fun subLabelsOf(groupCode: String?): List<SubLabel> =
    if (groupCode == null) emptyList() else GROUP_SUB_LABELS[groupCode].orEmpty()

/**
 * 按正文特征判断条目落在哪个子标签上。
 *
 * @param module 条目归属的模块名（已解析）；为 null（无归属）时一律不细分
 * @return 命中的子标签名；都不命中返回 null（表示归"主标签"，即模块名本身）
 *
 * ⚠️ 只有当条目归属的模块**在本分组的子标签体系内**时才细分：
 * 森林页虽然有多个模块，但它们各是各的模块，不需要靠正文特征再拆。
 */
fun subLabelOf(entryModule: String?, body: String, subLabels: List<SubLabel>): String? {
    if (entryModule == null || subLabels.isEmpty()) {
        return null
    }
    for (sub in subLabels) {
        for (kw in sub.keywords) {
            if (body.contains(kw, ignoreCase = true)) {
                return sub.label
            }
        }
    }
    return null
}

/** 构建指定分组的过滤元数据；分组 code 为 null 时返回空过滤（即全部日志） */
fun buildGroupFilter(groupCode: String?): GroupFilter {
    if (groupCode == null) {
        return GroupFilter(emptyList(), emptySet())
    }
    val group = ModelGroup.getByCode(groupCode) ?: return GroupFilter(emptyList(), emptySet())
    val configs = Model.getGroupModelConfig(group)
    val modules = ArrayList<ModuleRef>(configs.size)
    for ((code, config) in configs) {
        modules.add(ModuleRef(code, config.name))
    }
    val tag = LogType.tagOf(groupCode)
    return GroupFilter(
        modules = modules,
        groupTags = if (tag == null) emptySet() else setOf(tag),
        subLabels = subLabelsOf(groupCode)
    )
}

/**
 * 从日志条目里抽出属于 [groupFilter] 内模块的部分。
 *
 * ⚠️ **调用时机：必须在窗口截断之前**（见 [loadLogEntries]）——
 * 放到截断之后，窗口会被别组日志占满，本分组只剩零头。
 *
 * 归属**先看正文里的模块标记，绝不看条目来自哪个日志文件** —— 日志文件是按业务域切的，
 * farm 文件里同时装着庄园与农场的日志，拿"文件"当归属依据必然串组
 * （这正是「庄园记录里出现农场日志」的成因）。
 *
 * 判据分两档：
 * ① **有模块标记**（`[模块名] ` 前缀 → 「执行开始-X」/「执行结束-X」标记，前缀由
 *    `Log.setCurrentModule` 在模块执行线程内写入，ThreadLocal 隔离不串味）：
 *    只认本分组的模块，其它一律不收 —— 这是修串组的关键。
 * ② **没有模块标记**（旧版模块写下的分类日志）：退回用日志标签（`FARM` / `FOREST` …）
 *    兜底归属；标签也不属于本分组就丢弃。
 *
 * 第 ② 档不能写成"一律保留"：runtime.log 里界面进程的配置日志（`[账号1]加载APP配置`
 * 这类）既没有模块前缀、标签也是 `RUNTIME`，一律保留会把每个分组页都刷成运行日志。
 */
fun scopeEntriesToGroup(entries: List<LogEntry>, groupFilter: GroupFilter): List<LogEntry> {
    if (groupFilter.isEmpty) {
        return entries
    }
    val moduleNames = groupFilter.modules.mapNotNull { it.displayName }.toHashSet()
    if (moduleNames.isEmpty()) {
        return entries
    }
    return entries.filter { entry ->
        // ⚠️ 这里**必须**用 [moduleOf]（真实模块名），**不能**换成 [displayLabelOf]（含子标签细分）。
        // 收口是"这条日志属不属于本分组"的判断，粒度必须是模块级；换成子标签粒度后，
        // 子标签名（如「健康岛」）不在 moduleNames 里，会把本分组的日志误判成他组而丢弃。
        // 子标签只影响"在分组内部怎么归类显示"，不影响"属不属于这个分组"。
        val module = moduleOf(entry)
        if (module != null) {
            module in moduleNames
        } else {
            entry.tag != null && entry.tag in groupFilter.groupTags
        }
    }
}

/*
 * 说明：`scopeEntriesToGroup` 现在只做「按模块归属过滤」，不再处理段落边界。
 * 方案 B 之前这里用「从执行开始锚点向后收集到下一个执行开始/本模块执行结束」的
 * 区间切分法，但多模块真并发时区间会互相嵌套（实测庄园区间套在新村区间里），
 * 导致邻居模块的日志混入。加模块前缀后这个问题从根上消失了。
 */

/** 模块执行开始 / 结束标记的前缀（见 ModelTask.mainRunnable 的 Log.record 调用） */
private const val START_MARK = "执行开始-"
private const val END_MARK = "执行结束-"

/**
 * 正文开头的方括号片段（`[庄园] ` / `[账号1]` 这类）。
 *
 * 刻意不写 `^` 锚点：Kotlin 的 `find(input, startIndex)` 在正则带 `^` 时只能从 0 开始匹配，
 * 这里改为靠 `range.first == offset` 自己保证「必须紧贴当前位置」。
 */
private val LEADING_BRACKET_REGEX = Regex("\\[([^\\[\\]]+)]\\s?")

/** 账号简称前缀：`Log.withUser()` 写入的 `[账号N]`，不是模块名 */
private val ACCOUNT_LABEL_REGEX = Regex("^账号\\d+$")

/**
 * 把正文里的模块前缀拆出来。
 *
 * 正文最多带两层前缀：`[庄园] [账号1] 投喂小鸡…`（模块 → 账号）。
 * 返回 `(模块名, 去掉模块前缀的正文)`；账号前缀保留在正文里，用户需要看到是哪个账号。
 *
 * 规则：从正文开头起逐个读方括号片段，跳过账号简称（`账号N`），第一个非账号片段即模块名。
 * 这样即便前缀顺序被写反（`[账号1][庄园] …`）也仍能正确归属。
 *
 * 兼容：加模块前缀之前的日志（含上游版本写下的真机日志）只有 `[账号N]`、没有模块前缀，
 * 此时模块名返回 null（而不是把「账号1」当成模块），调用方会退回用「执行开始-X」标记判断归属。
 */
private fun splitModulePrefix(body: String): Pair<String?, String> {
    var offset = 0
    val skipped = StringBuilder()
    while (offset < body.length) {
        val match = LEADING_BRACKET_REGEX.find(body, offset) ?: break
        if (match.range.first != offset) {
            break
        }
        val token = match.groupValues[1].trim()
        if (token.isNotEmpty() && !ACCOUNT_LABEL_REGEX.matches(token)) {
            return token to (skipped.toString() + body.substring(match.range.last + 1))
        }
        skipped.append(match.value)
        offset = match.range.last + 1
    }
    return null to body
}

/**
 * 剥掉正文开头连续的账号前缀（`[账号1]` 这类），返回剩下的内容。
 *
 * 真机日志的正文是 `[账号1]执行开始-会员`，模块标记并不在正文最开头，
 * 所以识别「执行开始-X」前必须先剥掉账号前缀，否则老日志的分组页会全部为空。
 */
private fun stripAccountPrefixes(body: String): String {
    var offset = 0
    while (offset < body.length) {
        val match = LEADING_BRACKET_REGEX.find(body, offset) ?: break
        if (match.range.first != offset || !ACCOUNT_LABEL_REGEX.matches(match.groupValues[1].trim())) {
            break
        }
        offset = match.range.last + 1
    }
    return if (offset == 0) body else body.substring(offset)
}

/** 从「执行开始-X」这类标记里取出模块名；不匹配返回 null */
private fun moduleNameOf(prefix: String, body: String): String? {
    val trimmed = stripAccountPrefixes(body).trim()
    if (!trimmed.startsWith(prefix)) {
        return null
    }
    return trimmed.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}

/**
 * 条目的**归属模块** —— 顶部计数、模块筛选、卡片标签、分组收口必须共用这一个函数。
 *
 * 三级取值：① 正文的 `[模块名]` 前缀（最准）；② 「执行开始-X」/「执行结束-X」标记
 * （加模块前缀之前的老日志，以及任务框架自己打的执行标记）；③ 都没有则返回 null，
 * 由调用方决定是否退回文件 tag。
 *
 * ⚠️ **不要在任何一处单独写 `entry.module`**：计数与筛选一旦口径不同，界面就会自相矛盾 ——
 * 真机实测过：森林页顶部「物种」写 0，可列表里就有物种的执行记录，点标签还筛不出东西。
 * 原因是那批条目只有「执行开始-物种」标记、没有 `[物种]` 前缀，只认前缀的计数自然数不到。
 */
private fun moduleOf(entry: LogEntry): String? =
    entry.module
        ?: moduleNameOf(START_MARK, entry.body)
        ?: moduleNameOf(END_MARK, entry.body)

/**
 * 条目在界面上**实际显示的标签** —— 顶部计数、模块筛选、卡片标签、选中判必须共用这一个函数。
 *
 * 比 [moduleOf] 多一层：拿到模块名后，若本分组定义了子标签（[SubLabel]），
 * 再按正文特征把它细分（如运动组的「健康岛」、会员组的「芝麻粒/黄金票」）。
 * 都命中不了就回落到模块名（即"主标签"）。
 *
 * ⚠️ 计数、筛选、卡片标签**必须全部走这个函数**。历史上就因为计数与筛选口径不同，
 * 出现过「标签写 0、列表里却有条目、点标签还筛不出东西」的分裂（见 [moduleOf] 注释）。
 *
 * @param subLabels 本分组的子标签定义；空表时退化为纯 [moduleOf]，不影响其它分组。
 */
private fun displayLabelOf(entry: LogEntry, subLabels: List<SubLabel>): String? {
    val module = moduleOf(entry) ?: return null
    return subLabelOf(module, entry.body, subLabels) ?: module
}

/**
 * 日志卡片要显示的标签，优先级：
 * ① 正文细分出的子标签（如「健康岛」「芝麻粒」，见 [SubLabel]）；
 * ② 正文解析出的模块名（`[模块名]` 前缀，最准）；
 * ③ 「执行开始-X」/「执行结束-X」标记里的模块名（老日志兼容）；
 * ④ 文件自带的 tag（`FOREST` / `FARM` / `RUNTIME` 等）。
 */
private fun logTagOf(entry: LogEntry, subLabels: List<SubLabel>): String {
    return displayLabelOf(entry, subLabels) ?: entry.tag ?: "日志"
}

/** 日志查看时最多从文件尾部读取的字节数(避免大文件全量加载导致卡顿) */
private const val MAX_TAIL_BYTES = 1024 * 1024L

/**
 * 日志查看时最多展示的条目数。
 *
 * 这是**收口之后**的窗口（见 [loadLogEntries]）：分组页先筛出本分组的日志、再按它截断，
 * 所以窗口里装的都是本组内容，不会被别组日志挤掉。
 */
private const val MAX_LOG_ENTRIES = 500

/**
 * 从文件尾部读取文本,最多 maxBytes 字节。
 * 若非从头读取,会丢弃首个可能被截断的行。
 */
private fun readTailText(file: File, maxBytes: Long): String {
    val length = file.length()
    if (length <= 0L) {
        return ""
    }
    val start = maxOf(0L, length - maxBytes)
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(start)
        val bytes = ByteArray((length - start).toInt())
        raf.readFully(bytes)
        var text = String(bytes, Charsets.UTF_8)
        if (start > 0L) {
            val idx = text.indexOf('\n')
            text = if (idx >= 0) text.substring(idx + 1) else ""
        }
        return text
    }
}

/** 日志自动刷新间隔(毫秒) */
private const val LOG_REFRESH_INTERVAL_MS = 1000L

/**
 * 多个日志文件的组合签名:各文件的长度与修改时间混成一个数。
 * 只要任意一个来源有新内容,签名就会变,刷新循环据此决定要不要重新读盘。
 */
private fun logFilesStamp(files: List<File>): Long? {
    var acc = 0L
    var any = false
    for (file in files) {
        if (!file.exists()) {
            continue
        }
        any = true
        acc = acc * 31 + file.length() * 31 + file.lastModified()
    }
    return if (any) acc else null
}

/**
 * 读取一个或多个日志文件,按时间序合并为条目列表。
 *
 * 单文件（系统记录页）直接返回，不排序不去重 —— 文件本身已经是时间序。
 *
 * 多文件（分组页）：分类日志是**双写**的（`Log.farm()` 同时写 farm.log 与 runtime.log），
 * 合并时必须按 `时间 + tag + 正文` 去重，否则同一条日志会在页面上出现两遍。
 *
 * ⚠️ **分组收口必须发生在窗口截断之前**（[groupFilter] 要在 `MAX_LOG_ENTRIES` 裁剪之前生效）。
 * 顺序反了窗口就会被别组日志占满 —— 实测：5 个文件合并出 1703 条，其中 326 条（65%）
 * 是无归属的界面日志（`[账号1]加载APP配置` 这类）。先截「最近 500 条」再收口，
 * 森林组本应看到的 544 条只剩 60 条、可回溯时间从 07:54 缩到 21:18；
 * 先收口再截断，同样 500 条的窗口装下的全是本组日志。
 *
 * @param groupFilter 分组页传入本分组的收口条件；「系统记录」页传 null（不分组，取全量）
 */
private fun loadLogEntries(files: List<File>, groupFilter: GroupFilter? = null): List<LogEntry> {
    if (files.isEmpty()) {
        return emptyList()
    }
    if (files.size == 1 && groupFilter == null) {
        return loadLogEntriesFromFile(files[0])
    }
    val seen = HashSet<String>()
    val merged = ArrayList<LogEntry>()
    for (file in files) {
        val name = file.name
        for (entry in loadLogEntriesFromFile(file)) {
            // body 取 trimEnd：同一行在分类文件与 runtime 里的尾随空白可能不同（不影响内容判定）
            val key = "${entry.sortKey}|${entry.tag}|${entry.body.trimEnd()}"
            if (seen.add(key)) {
                merged.add(entry.copy(source = name))
            }
        }
    }
    merged.sortBy { it.sortKey }   // HH:mm:ss.SSS 字典序即时间序
    // ① 先收口到本分组（「系统记录」页无分组，原样返回）
    val scoped = if (groupFilter == null) merged else scopeEntriesToGroup(merged, groupFilter)
    // ② 再按窗口截断 —— 此时窗口里全是本分组的日志，一个位置都不浪费
    return if (scoped.size > MAX_LOG_ENTRIES) {
        scoped.subList(scoped.size - MAX_LOG_ENTRIES, scoped.size).toList()
    } else {
        scoped
    }
}

/** 日志行的时间戳 + 可选 tag 前缀；runtime 与分类文件两种格式共用 */
private val LOG_TIME_REGEX = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(?:(\\w+):\\s*)?(.*)$")

/**
 * 把指定分组在 [files] 里的日志行删掉，其余内容原样保留。
 *
 * 分组页的数据是跨文件聚合来的（runtime + 各分类文件），**不能整文件清空** ——
 * 农场/新村/运动/会员页的主文件就是 runtime.log，整清会把所有分组的日志一起清光。
 * 所以这里按行判定归属，只删属于本分组的行（连同其续行），判定规则与
 * [scopeEntriesToGroup] 完全一致：有模块标记看标记，没有标记看日志标签兜底。
 *
 * @return 是否真的删掉了内容
 */
private fun clearGroupLines(files: List<File>, groupFilter: GroupFilter): Boolean {
    if (groupFilter.isEmpty) {
        return false
    }
    val moduleNames = groupFilter.modules.mapNotNull { it.displayName }.toHashSet()
    if (moduleNames.isEmpty()) {
        return false
    }
    var changed = false
    for (file in files) {
        if (!file.exists() || file.length() == 0L) {
            continue
        }
        val text = try {
            file.readText()
        } catch (t: Throwable) {
            continue
        }
        val kept = StringBuilder(text.length)
        var dropping = false
        var touched = false
        for (line in text.lineSequence()) {
            val match = LOG_TIME_REGEX.find(line)
            if (match != null) {
                val owner = splitModulePrefix(match.groupValues[3]).first
                    ?: moduleNameOf(START_MARK, match.groupValues[3])
                    ?: moduleNameOf(END_MARK, match.groupValues[3])
                val tag = match.groupValues[2].takeIf { it.isNotEmpty() }
                dropping = if (owner != null) {
                    owner in moduleNames
                } else {
                    tag != null && tag in groupFilter.groupTags
                }
                if (dropping) {
                    touched = true
                }
            }
            // 续行跟着上一条走：上一条被删，它的续行也一起删
            if (!dropping) {
                kept.append(line).append('\n')
            }
        }
        if (touched) {
            try {
                file.writeText(kept.toString())
                changed = true
            } catch (t: Throwable) {
                // 单个文件写失败不影响其它来源
            }
        }
    }
    return changed
}

/**
 * 读取单个日志文件并按行解析为条目;无时间戳的行合并到上一条(多行日志聚合为同一卡片)。
 * 仅读取文件尾部,并限制最大条目数,保证大文件也能快速打开。
 */
private fun loadLogEntriesFromFile(file: File): List<LogEntry> {
    if (!file.exists()) {
        return emptyList()
    }
    // 两种格式：
    // runtime/system/debug: 13:34:10.251 RUNTIME: 执行结束-庄园
    // forest/farm/goldenbeans/other: 19:37:33.150 森林签到...
    val timeRegex = LOG_TIME_REGEX
    val entries = ArrayDeque<LogEntry>()
    return try {
        val text = readTailText(file, MAX_TAIL_BYTES)
        // 先裁掉文件末尾的空行。它没有任何内容，却会让「同一条日志在分类文件与 runtime 里」
        // 的正文不一致 —— 分类文件里那条往往是最后一行（行尾带换行），runtime 里同一条后面
        // 还有别的行（行尾不带）—— 双写去重的键因此对不上，同一条日志会在分组页显示两遍。
        val lines = text.lineSequence().toList()
        var lineCount = lines.size
        while (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--
        }
        var lineNumber = 0
        var lastTime = ""
        for (index in 0 until lineCount) {
            val line = lines[index]
            lineNumber = index + 1
            val match = timeRegex.find(line)
            if (match != null) {
                // 正文可能是 `[庄园] 肥料领取...`，把模块前缀拆出来单独存
                val (module, body) = splitModulePrefix(match.groupValues[3])
                lastTime = match.groupValues[1]
                entries.addLast(
                    LogEntry(
                        lineNumber = lineNumber,
                        time = lastTime,
                        tag = match.groupValues[2].takeIf { it.isNotEmpty() },
                        module = module,
                        body = body,
                        sortKey = lastTime
                    )
                )
            } else {
                // 无时间戳:视为上一条的续行,合并到同卡片(排序键继承上一条,保证跨文件合并后不乱序)
                if (entries.isNotEmpty()) {
                    val last = entries.removeLast()
                    entries.addLast(last.copy(body = last.body + "\n" + line))
                } else {
                    entries.addLast(LogEntry(lineNumber, null, null, null, line, sortKey = lastTime))
                }
            }
            // 超出上限时丢弃最早的条目,保证内存占用可控
            while (entries.size > MAX_LOG_ENTRIES) {
                entries.removeFirst()
            }
        }
        entries.toList()
    } catch (e: Throwable) {
        emptyList()
    }
}