package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold

/* ───────────────────────── 布局骨架 / 页面容器 / 空态与加载 ─────────────────────────
 * 本文件是「风格无关 UI 组件集」的一部分（整体约定与分层说明见 SesameFoundation.kt 顶部）。
 * 每个组件内部按 LocalUiStyle 分派到 Miuix / Material 3 两套实现，
 * 因此页面只依赖这些组件 —— 换肤不会触碰任何业务逻辑。
 */
/* ───────────────────────── 布局骨架 ───────────────────────── */

/** 页面骨架：负责背景色、顶部栏与底部导航占位 */
@Composable
fun SesameScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixScaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            containerColor = MiuixTheme.colorScheme.surface
        ) { padding -> content(padding) }

        UiStyle.MATERIAL3 -> Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            containerColor = sesameSurface()
        ) { padding -> content(padding) }
    }
}

/* ───────────────────────── 页面容器 ───────────────────────── */

/**
 * 二级页通用页面外壳：Scaffold + [SesameTopBar]。
 *
 * 把「顶部栏 + 内容区 padding」这对固定组合收敛到一处，
 * 让每个二级页只关注自己的内容，也保证所有二级页在切风格时行为一致。
 */
@Composable
fun SesameDetailScaffold(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    SesameScaffold(
        topBar = {
            SesameTopBar(
                title = title,
                onBack = onBack,
                onImport = onImport,
                onExport = onExport,
                onClear = onClear,
                onShare = onShare
            )
        }
    ) { padding -> content(padding) }
}

/**
 * 二级页「大标题折叠」骨架（MD 全量重构阶段2，仅 MATERIAL3 生效）。
 *
 * 与 [SesameDetailScaffold] 的区别：MD 侧换成 mochi 的 [MochiLargeTitleScaffold]，
 * 获得 demo 的标志性交互——大标题随内容滚动模糊淡出、顶栏居中标题淡入、
 * 顶栏底色滚动 8dp 渐显、松手停在标题半途自动吸附、下拉越界大标题放大 1.12 倍。
 *
 * 🔴 契约变化：mochi 脚手架**自带** verticalScroll + statusBarsPadding + 16dp 水平留白
 * + barHeight 顶部留白，因此 [content] 必须是「纯内容」（ColumnScope 直接铺行），
 * 页面不要再自己套 verticalScroll / padding(padding) / horizontal padding。
 *
 * MIUIX 分支保持 [SesameDetailScaffold] 的原样结构（普通顶栏 + 页面自带滚动），
 * 观感零变化——桥接层在这里把 mochi 替页面吃掉的那套滚动/padding 原样补回去。
 *
 * @param title 顶栏居中小标题 + 展开态大标题（同一串）
 * @param onBack 返回；MATERIAL3 下渲染为 mochi 风格的圆形返回按钮
 */
@Composable
fun SesameDetailTitleScaffold(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExecute: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> MochiLargeTitleScaffold(
            title = title,
            background = sesameSurface(),
            navigationIcon = { SesameTitleBackIcon(onBack) },
            actions = {
                SesameTitleActions(
                    onImport = onImport, onExport = onExport,
                    onClear = onClear, onShare = onShare,
                    onExecute = onExecute, actions = actions
                )
            },
            content = content
        )

        UiStyle.MIUIX -> SesameScaffold(
            topBar = {
                SesameTopBar(
                    title = title, onBack = onBack,
                    onImport = onImport, onExport = onExport,
                    onClear = onClear, onShare = onShare,
                    onExecute = onExecute, actions = actions
                )
            }
        ) { padding ->
            // 补齐 mochi 脚手架替页面吃掉的那套：状态栏留白由 SesameTopBar 内部处理，
            // 这里只管内容区的滚动 + 顶栏 padding + 水平 16dp，与原页面写法逐字一致。
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

/** 大标题脚手架的返回按钮（MD 顶栏槽），复用与 SesameTopBar MD 分支一致的图标/配色。 */
@Composable
private fun SesameTitleBackIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 大标题脚手架 MD 顶栏的右侧动作按钮。
 * 阶段2 先复用与 SesameTopBar MD 分支一致的 M3 IconButton（功能/观感连续），
 * mochi 玻璃胶囊按钮放到阶段4「顶栏按钮组」统一替换。
 */
@Composable
private fun RowScope.SesameTitleActions(
    onImport: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onExecute: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    actions()
    if (onImport != null) IconButton(onClick = onImport) {
        Icon(Icons.Filled.Upload, contentDescription = "导入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (onExport != null) IconButton(onClick = onExport) {
        Icon(Icons.Filled.Upload, contentDescription = "导出",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (onClear != null) IconButton(onClick = onClear) {
        Icon(Icons.Filled.Delete, contentDescription = "删除",
            tint = MaterialTheme.colorScheme.error)
    }
    if (onShare != null) IconButton(onClick = onShare) {
        Icon(Icons.Filled.Share, contentDescription = "分享",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (onExecute != null) IconButton(onClick = onExecute) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "执行",
            tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * 二级页「大标题折叠」骨架的懒加载版（MD 全量重构阶段2，仅 MATERIAL3 生效）。
 * 对应页面自带 `LazyColumn` 的详情页（黑名单 / 好友统计 / 日志等）。
 *
 * 与 [SesameDetailTitleScaffold] 同契约，只是 [content] 是 [LazyListScope]：
 * 把页面原来的 `LazyColumn { ... }` 花括号里的内容原样搬进来，滚动容器由 mochi 提供。
 *
 * @param listState 外部持有的列表状态（页面若要在滚动时读 firstVisibleItem 等可传入）
 */
@Composable
fun SesameLazyDetailTitleScaffold(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExecute: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: LazyListScope.() -> Unit
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> MochiLazyLargeTitleScaffold(
            title = title,
            background = sesameSurface(),
            listState = listState,
            navigationIcon = { SesameTitleBackIcon(onBack) },
            actions = {
                SesameTitleActions(
                    onImport = onImport, onExport = onExport,
                    onClear = onClear, onShare = onShare,
                    onExecute = onExecute, actions = actions
                )
            },
            content = content
        )

        UiStyle.MIUIX -> SesameScaffold(
            topBar = {
                SesameTopBar(
                    title = title, onBack = onBack,
                    onImport = onImport, onExport = onExport,
                    onClear = onClear, onShare = onShare,
                    onExecute = onExecute, actions = actions
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = contentPadding,
                flingBehavior = sesameFlingBehavior(),
                content = content
            )
        }
    }
}

/* ───────────────────────── 空态与加载 ───────────────────────── */

/**
 * 空态占位。
 *
 * M3 侧补了一条 `bodySmall` 的说明行与更大的垂直呼吸感——
 * 单纯的「(空)」在 MD3 语境里更像渲染失败，而非「确实没有内容」。
 */
@Composable
fun SesameEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            MiuixText(
                text = text,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        UiStyle.MATERIAL3 -> Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}
