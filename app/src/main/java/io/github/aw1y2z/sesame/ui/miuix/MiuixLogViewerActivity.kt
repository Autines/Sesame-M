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
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.ui.theme.SesameEmptyState
import io.github.aw1y2z.sesame.ui.theme.SesameFilterChip
import io.github.aw1y2z.sesame.ui.theme.SesameLogCard
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
    MEMBER("会员记录");

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
         * 「分类记录」的分组层级：`父项 to 它的子项`，顺序对齐 ModelGroup 的声明顺序
         * （基础不在此列：基础日志由运行日志承载）。
         *
         * **父项**有自己的日志文件（forest / farm / goldenbeans / other）与写入开关；
         * **子项**的日志与父项**共用同一个文件** —— 新村的 `Log.farm()` 落在 farm.log、
         * 农场的也落在 farm.log，运动的 `Log.other()` 与其他的一样落在 other.log ——
         * 所以它们拿不到独立开关（开关是**按文件**给的，`AppConfig` 里也只有这 4 个）。
         *
         * 把父子关系画出来，用户才不会以为「这几行漏了开关」。
         */
        val categoryTree: List<Pair<LogType, List<LogType>>>
            get() = listOf(
                FOREST to emptyList(),
                FARM to listOf(STALL, ORCHARD),
                GOLDENBEANS to emptyList(),
                OTHER to listOf(SPORTS, MEMBER)
            )

        /** 有独立日志文件、可开关的分组（即 [categoryTree] 的父项） */
        val toggleableCategories: List<LogType>
            get() = categoryTree.map { it.first }

        /** 与 [parent] 共用日志文件、因而没有独立开关的子项；[parent] 无子项时返回空表 */
        fun childrenOf(parent: LogType): List<LogType> =
            categoryTree.firstOrNull { it.first == parent }?.second.orEmpty()

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

    // 定时刷新:文件无变化时只做一次轻量的 length/lastModified 比较,不读盘也不重组
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
    val filteredEntries = scopedEntries.filter { e ->
        val matchSearch = searchQuery.isBlank() ||
                (e.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                e.body.contains(searchQuery, ignoreCase = true)
        val matchModule = selectedModule == null || moduleOf(e) == selectedModule
        matchSearch && matchModule
    }

    /**
     * 顶部模块选择条的内容：以**该分组声明的子模块**为骨架（按配置顺序），
     * 再并入日志里实际出现过的模块。
     *
     * 为什么不只看「日志里出现过的模块」：分类日志（如 forest.log）里的每一行
     * 是否带 `[模块名]` 前缀，取决于写日志时模块有没有在运行线程里登记归属。
     * 用旧版模块写下的日志天然没有前缀 —— 若把「出现过多个模块」当作显示条件，
     * 选择条就会时有时无（同一页面对不同时期的日志表现不一致）。
     * 改成按分组声明固定展示后，森林组的「森林/保护/物种/海洋」恒定可见；
     * 计数为 0 表示当前日志里还没有该模块写下的记录。
     *
     * 计数口径与列表筛选、卡片标签**同源**（都走 [moduleOf]）。之前这里只认
     * `[模块名]` 前缀，而列表还能靠「执行开始-X」标记归属，于是出现「标签写 0、
     * 列表里却有条目、点标签还筛不出东西」的分裂 —— 真机见过：物种计数 0，
     * 实际有 4 条执行记录。
     */
    val moduleCounts = remember(scopedEntries, groupFilter) {
        val actual = scopedEntries.mapNotNull { moduleOf(it) }
            .groupBy { it }
            .mapValues { it.value.size }
        val ordered = LinkedHashMap<String, Int>()
        groupFilter.modules.mapNotNull { it.displayName }.forEach { ordered[it] = actual[it] ?: 0 }
        actual.entries.sortedByDescending { it.value }.forEach { (name, count) ->
            if (!ordered.containsKey(name)) ordered[name] = count
        }
        ordered
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
        Column(Modifier.fillMaxSize().padding(padding)) {
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
            // 该分组声明了多个子模块时才展示（如森林组有 森林/保护/物种/海洋）。
            // 模块名以分组声明为准（稳定），日志里的 [模块名] 前缀只用来统计条数。
            //
            // 注：运行日志页过去还并排着一排「文件标签」筛选项（RUNTIME / FARM /
            // FOREST / OTHER …），它和本排是同一批业务域的两种写法（森林↔FOREST、
            // 庄园↔FARM），既重复又是全英文，已删除，各日志页统一只保留本排中文模块筛选。
            if (moduleCounts.size > 1) {
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
                        // 选中的模块在当前日志里一条都没有时，不要只说「无匹配日志」，
                        // 要说清是「这个模块还没写过日志」，否则容易被当成筛选坏了。
                        val emptyModule = selectedModule?.takeIf { moduleCounts[it] == 0 }
                        SesameEmptyState(
                            text = if (emptyModule != null) "当前日志里没有「$emptyModule」的记录" else "无匹配日志",
                            hint = if (emptyModule != null) "该模块执行过一次后就会有记录"
                            else "换个关键词或清空筛选条件试试"
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
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        filteredEntries.asReversed(),
                        key = { _, e -> "${e.source}-${e.lineNumber}-${e.hashCode()}" }
                    ) { _, entry ->
                        SesameLogCard(
                            tag = logTagOf(entry),
                            time = entry.time ?: "",
                            body = entry.body
                        )
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
 */
data class GroupFilter(
    val modules: List<ModuleRef>,
    val groupTags: Set<String>
) {
    val isEmpty: Boolean get() = modules.isEmpty() && groupTags.isEmpty()

    fun displayNameOf(code: String): String? = modules.firstOrNull { it.code == code }?.displayName
}

/** 模块在分组视图里的引用：类简名 + 显示名 */
data class ModuleRef(val code: String, val displayName: String)

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
        groupTags = if (tag == null) emptySet() else setOf(tag)
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
 * 日志卡片要显示的标签，优先级：
 * ① 正文解析出的模块名（方案 B 写入的 `[模块名]` 前缀，最准）；
 * ② 「执行开始-X」/「执行结束-X」标记里的模块名（老日志兼容）；
 * ③ 文件自带的 tag（`FOREST` / `FARM` / `RUNTIME` 等）。
 */
private fun logTagOf(entry: LogEntry): String {
    return moduleOf(entry) ?: entry.tag ?: "日志"
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