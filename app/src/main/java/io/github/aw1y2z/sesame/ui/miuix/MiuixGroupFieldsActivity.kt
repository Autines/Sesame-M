package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelConfig
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.ui.theme.LocalUiStyle
import io.github.aw1y2z.sesame.ui.theme.M3_PER_ITEM_CARDS
import io.github.aw1y2z.sesame.ui.theme.SESAME_CARD_CORNER
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.SesameTopBar
import io.github.aw1y2z.sesame.ui.theme.UiStyle
import io.github.aw1y2z.sesame.ui.theme.sesameCardShape
import io.github.aw1y2z.sesame.ui.theme.sesameGroupedShape
import io.github.aw1y2z.sesame.ui.theme.sesameSurface
import io.github.aw1y2z.sesame.ui.theme.sesameSurfaceContainer
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.StringUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置字段页（三级）：显示某个分组下的所有配置字段。
 * 从 MiuixSettingsActivity 跳转进来，通过 Intent 传递 userId 和 groupCode。
 */
class MiuixGroupFieldsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_GROUP_CODE = "groupCode"

        /** 预填搜索词：从二级页搜索结果跳进来时带上，进入即过滤到该字段 */
        const val EXTRA_SEARCH_QUERY = "searchQuery"
    }

    private var userId: String? = null
    internal var groupCode: String? = null
    private var initialQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        // 返回键走 OnBackPressedDispatcher（原因见 MiuixSettingsActivity）：
        // 本页的落盘全靠退出流程，覆盖 onBackPressed() 在 targetSdk 36 上等于不保存。
        onBackPressedDispatcher.addCallback(this) {
            save()
            finish()
        }
        // 上游 0cfb2abb：从二级页搜索结果跳进来时带上搜索词，进页面即过滤
        initialQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
        setAppContent {
            groupCode?.let { code ->
                val group = ModelGroup.entries.find { it.name == code }
                if (group != null) {
                    GroupFieldsContent(activity = this, userId = userId, groupCode = code, group = group,
                            initialQuery = initialQuery)
                } else {
                    top.yukonga.miuix.kmp.basic.Text("分组不存在: $code")
                }
            } ?: run {
                top.yukonga.miuix.kmp.basic.Text("缺少参数")
            }
        }
    }

    /** 顶部返回按钮与系统返回统一入口：先保存再退出。 */
    fun saveAndFinish() {
        save()
        finish()
    }

    /**
     * 统一落盘入口：本页字段变更只写内存，只有真正退出时才调用这里写一次磁盘。
     * 先用 hasFieldChanges() 判断是否有字段级改动（无改动直接短路，不写盘、不提示），
     * 确认有改动后走 force=true，避免 ConfigV2.save() 内部再做一次全量序列化比较。
     * <p>没有账号或写盘失败时都要出声：否则用户以为存上了，实际改动无声消失。
     */
    fun save() {
        // ⚠️ 这里不要写 `if (userId == null) return`。
        // 「默认账号」的 userId 本来就是 null，配置读写会退回默认配置文件
        // （ConfigV2.isModify/save 内部已做了 isEmpty(userId) → 默认文件的处理）。
        // 判空直接 return 会让默认账号下本页的改动既不落盘、也不给任何提示。
        if (!ConfigV2.isModify(userId)) return
        if (!ConfigV2.hasFieldChanges()) return
        if (ConfigV2.save(userId, true)) {
            ToastUtil.show(this, "保存成功！")
            sendRestartIfNeeded()
        } else {
            ToastUtil.show(this, "保存失败，改动未落盘")
        }
    }

    private fun sendRestartIfNeeded() {
        if (!StringUtil.isEmpty(userId)) {
            try {
                val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                intent.putExtra("userId", userId)
                sendBroadcast(intent)
            } catch (th: Throwable) {
                Log.printStackTrace(th)
            }
        }
    }
}

/**
 * 扁平化后的列表行：把「模型标题」和「字段」都提升为 LazyColumn 的独立 item，
 * 让虚拟化真正下沉到字段级。
 *
 * 原先每个 ModelConfig 是一个 item、内部用 fields.forEach 组合全部字段，
 * 导致 Forest 组（77 个字段）一旦进入视口就要一次性组合、measure、layout 所有字段。
 */
private sealed interface GroupFieldsRow {
    val key: String

    data class Header(override val key: String, val title: String) : GroupFieldsRow

    data class Field(
        override val key: String,
        val modelCode: String,
        val field: ModelField<*>,
        val first: Boolean,
        val last: Boolean
    ) : GroupFieldsRow
}

@Composable
fun GroupFieldsContent(activity: MiuixGroupFieldsActivity, userId: String?, groupCode: String, group: ModelGroup,
                       initialQuery: String? = null) {
    // 父字段开关/选项变化后，依赖其显示的子字段需重新计算可见性，
    // 用 depVersion 作为 remember 键触发扁平行列表重建。
    var depVersion by remember { mutableStateOf(0) }
    // 固定一份实例：每次重组都换新 lambda 会让子项无法跳过重组
    val bumpDependency: () -> Unit = remember { { depVersion++ } }
    // 只有「被其他字段依赖」的父字段变动才可能改变可见性。其余字段（如普通文本框）
    // 每次输入都重建整份扁平行，77 字段的组里就表现为「输入框打一个字就消失」。
    val dependencyCodes = remember(group) {
        Model.getGroupModelConfig(group).values
            .flatMap { it.fields.values }
            .mapNotNull { it.dependsOn?.takeIf { code -> code.isNotEmpty() } }
            .toSet()
    }
    // 字段对象由 ConfigV2 单例持有，引用稳定；仅当分组或依赖版本变化时才重建。
    val rows = remember(group, depVersion) {
        val list = ArrayList<GroupFieldsRow>()
        val modelConfigs = Model.getGroupModelConfig(group).values.filter { it.fields.isNotEmpty() }
        // 分组独立后单模型组的小节标题与顶栏组名重复；多模型组才需要标题区分
        val showHeader = modelConfigs.size > 1
        modelConfigs.forEach { mc ->
            val fields = mc.fields.values.toList()
            if (showHeader) {
                list.add(GroupFieldsRow.Header(key = "header:${mc.getCode()}", title = mc.name ?: ""))
            }
            // 过滤：依赖父字段但父未激活的子字段
            val visibleFields = fields.filter { f ->
                f.isVisible(mc)
            }
            visibleFields.forEachIndexed { index, field ->
                list.add(
                    GroupFieldsRow.Field(
                        key = "field:${mc.getCode()}:${field.code}",
                        modelCode = mc.getCode(),
                        field = field,
                        first = index == 0,
                        last = index == visibleFields.lastIndex
                    )
                )
            }
        }
        list
    }

    /**
     * 执行当前分组的任务。
     * 本进程是模块 App 的 UI 进程，没有 libxposed 类（ApplicationHook/hook.Toast/NotificationUtil 一碰
     * 就 NoClassDefFoundError），任务循环也不能压在主线程上，所以只发广播让注入进程去跑。
     * BASE 分组由注入侧解释为"执行全部任务"。
     */
    val onExecute = remember {
        {
            try {
                val intent = Intent("com.eg.android.AlipayGphone.sesame.execute")
                intent.putExtra("group", group.getCode())
                activity.sendBroadcast(intent)
                ToastUtil.show(activity, "已发送执行请求：${group.getName()}")
            } catch (th: Throwable) {
                Log.printStackTrace(th)
                ToastUtil.show(activity, "执行失败: ${th.message}")
            }
            Unit
        }
    }

    Scaffold(
        topBar = {
            SesameTopBar(
                title = group.getName(),
                onBack = { activity.saveAndFinish() },
                onExecute = onExecute
            )
        },
        containerColor = sesameSurface()
    ) { padding ->
        // 搜索框：与四级页一致，按字段名/编码/描述过滤，并隐藏无匹配的分区
        // 带 initialQuery 进来时（二级页搜索结果跳转）直接以它为初始值，进页面即过滤
        var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
        // 按分区（Header）归组：一个分组 = 一张 CardColumn（四角 16dp 圆角、行无缝），
        // 与一级页「一张卡里排多行」完全一致；行作为 Card 的子项，背景/裁剪/按压观感都由它负责。
        // 注意：必须在这里算（@Composable 上下文），不能放进 LazyColumn 的 content lambda
        val sections = remember(rows, searchQuery) {
                val query = searchQuery.trim()
                val list = ArrayList<Pair<String?, MutableList<GroupFieldsRow.Field>>>()
                var title: String? = null
                var fields = ArrayList<GroupFieldsRow.Field>()
                rows.forEach { row ->
                    when (row) {
                        is GroupFieldsRow.Header -> {
                            if (fields.isNotEmpty()) list.add(title to fields)
                            title = row.title
                            fields = ArrayList()
                        }
                        is GroupFieldsRow.Field -> {
                            if (query.isBlank() || fieldMatchesSearch(query, row.field)) {
                                fields.add(row)
                            }
                        }
                    }
                }
                if (fields.isNotEmpty()) list.add(title to fields)
            list
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索框（与四级页 MiuixSelectionEditActivity 一致）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "",
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                "×",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp).clickable { searchQuery = "" }
                            )
                        }
                    }
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                // M3 下每行是一张独立卡，靠行间距分开；Miuix 下多行共享一张连续卡，
                // 必须零间距，否则卡面会被切成一截一截。
                verticalArrangement = if (LocalUiStyle.current == UiStyle.MATERIAL3 && M3_PER_ITEM_CARDS) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Top
                }
            ) {
                if (searchQuery.isNotBlank() && sections.isEmpty()) {
                    item {
                        SmallTitle(text = "无匹配字段")
                    }
                }
                items(sections.size) { index ->
                    val (title, fields) = sections[index]
                    title?.let { SmallTitle(text = it) }
                    CardColumn {
                        fields.forEach { fieldRow ->
                            // 按行唯一 key 分组：依赖子行增删后下方行会重排，
                            // 不包 key 会沿用上一行的组合状态（表现为下方开关显示错位）
                            key(fieldRow.key) {
                                GroupFieldRow(
                                    activity = activity,
                                    userId = userId,
                                    groupCode = groupCode,
                                    row = fieldRow,
                                    onDependencyChanged =
                                        if (dependencyCodes.contains(fieldRow.field.code)) bumpDependency else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 三级页搜索匹配：字段名、编码或描述包含关键字（忽略大小写）。
 * 与四级页按 name/id 过滤的语义保持一致，这里用字段的 name/code/description。
 */
internal fun fieldMatchesSearch(query: String, field: ModelField<*>): Boolean {
    return field.name?.contains(query, ignoreCase = true) == true
        || field.code.contains(query, ignoreCase = true)
        || field.description?.contains(query, ignoreCase = true) == true
}

/**
 * 单个字段行的卡片外观。
 *
 * 两套风格对「字段列表」的表达不同：
 * - `MIUIX`：相邻行背景一致、圆角只在首尾外露 → 视觉上是一张连续大卡，
 *   同时每一行仍能被 LazyColumn 独立复用/回收；
 * - `MATERIAL3`：每行一张**独立卡**（四角全圆角），行间距由 LazyColumn 提供。
 *
 * 「行自持内边距」的模型两套风格一致：Miuix 的 ArrowPreference 需要外层 16dp
 * 水平留白，M3 的行组件已自带 16dp，因此这里只负责形状与背景。
 */
@Composable
private fun GroupFieldRow(
    activity: MiuixGroupFieldsActivity,
    userId: String?,
    groupCode: String,
    row: GroupFieldsRow.Field,
    onDependencyChanged: (() -> Unit)?
) {
    val m3 = LocalUiStyle.current == UiStyle.MATERIAL3
    val perItem = m3 && M3_PER_ITEM_CARDS
    val shape = if (perItem) {
        sesameCardShape()
    } else {
        sesameGroupedShape(
            first = row.first,
            last = row.last,
            radius = if (m3) SESAME_CARD_CORNER else 16.dp
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sesameSurfaceContainer(), shape)
            // 首尾内边距只在「连续大卡」模式下需要（把卡面顶到组边界）；
            // 独立卡自带完整圆角，行内容的高度由行组件自己保证。
            .padding(
                top = if (row.first && !perItem) 8.dp else 0.dp,
                bottom = if (row.last && !perItem) 8.dp else 0.dp
            )
    ) {
        val field = row.field
        when (field.type) {
            "SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE" -> {
                SesameClickRow(
                    title = field.name ?: "",
                    summary = field.description,
                    onClick = {
                        activity.startActivity(
                            Intent(activity, MiuixSelectionEditActivity::class.java).apply {
                                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                                putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, groupCode)
                                putExtra(MiuixSelectionEditActivity.EXTRA_FIELD_CODE, field.code)
                                putExtra(MiuixSelectionEditActivity.EXTRA_MODEL_CODE, row.modelCode)
                            }
                        )
                    }
                )
            }
            else -> {
                // 只写内存，落盘统一在 saveAndFinish() / onBackPressed() 完成
                FieldItem(field = field, onFieldChanged = onDependencyChanged)
            }
        }
    }
}


