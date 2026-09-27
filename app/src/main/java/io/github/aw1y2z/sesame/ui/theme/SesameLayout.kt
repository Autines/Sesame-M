package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
