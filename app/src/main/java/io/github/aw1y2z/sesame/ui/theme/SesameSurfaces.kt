package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ───────────────────────── 卡片模型 / 分组容器 / 日志卡片 / 列表卡 / 状态卡 ─────────────────────────
 * 本文件是「风格无关 UI 组件集」的一部分（整体约定与分层说明见 SesameFoundation.kt 顶部）。
 * 每个组件内部按 LocalUiStyle 分派到 Miuix / Material 3 两套实现，
 * 因此页面只依赖这些组件 —— 换肤不会触碰任何业务逻辑。
 */
/* ───────────────────────── 卡片模型 ───────────────────────── */

/**
 * M3 下卡片组的排布方式。
 *
 * - `true`：**每项一张独立卡**，卡与卡之间留 [M3CardGap]；
 * - `false`：**一组一张卡**，组内多行连续排布，层次由「组」建立而非「行」。
 *
 * 两种都成立，服务不同密度的页面：分组语义强、项目多的列表用后者
 * （一屏能容纳更多行、长列表不碎）；项与项之间没有共同语义时用前者。
 * 切换这一个常量即可整站生效。
 */
internal const val M3_PER_ITEM_CARDS = false

/**
 * M3 下「行是否自带卡片外观」。
 *
 * 同一个行组件会在两种容器里出现，而视觉模型完全相反：
 *
 * - **每项一张卡**（[M3_PER_ITEM_CARDS] 为 true）：行必须自己就是一张卡，容器只负责间距；
 * - **一组一张卡**（为 false）：卡由外层容器提供，行必须是裸的，否则会套出一层「卡中卡」。
 *
 * 标志由容器下发（[SesameCardGroup]），行组件据此决定要不要自绘卡片，页面无需关心。
 */
internal val LocalM3RowCarded = staticCompositionLocalOf { false }

/**
 * M3 行的卡片外观包装。
 *
 * [onClick] 非空时用 `Surface(onClick)`——水波纹才会被卡片圆角裁剪；
 * 为空时用普通容器，只读行不给涟漪，避免「点了没反应」的误导。
 * 未启用卡片模式时退化为透明包装，行为与改造前一致。
 */
@Composable
internal fun M3RowCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (!LocalM3RowCarded.current) {
        if (onClick == null) {
            content()
        } else {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) { content() }
        }
        return
    }
    val shape = sesameCardShape()
    val container = sesameSurfaceContainer()
    if (onClick == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(container)
        ) { content() }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            tonalElevation = 0.dp
        ) { content() }
    }
}

/**
 * 行内前导图标。[icon] 为空时不占位（不留下 24dp 空洞）。
 *
 * M3 的列表项用图标承担「快速识别」——一眼扫过去先认形状再读文字。
 * 图标取 onSurfaceVariant（中性灰）而非主色：它是索引，不是强调点。
 */
@Composable
internal fun M3LeadingIcon(icon: ImageVector?, enabled: Boolean = true) {
    if (icon == null) return
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) base else base.copy(alpha = M3DisabledContentAlpha),
        modifier = Modifier.size(M3LeadingIconSize)
    )
    Spacer(Modifier.width(M3LeadingIconGap))
}

/* ───────────────────────── 分组容器 ───────────────────────── */

/**
 * 卡片组容器。
 *
 * 两套风格对「一组设置项」的表达不同，这里按风格分派：
 *
 * - `MIUIX`：延续 HyperOS 的**一张大卡装多行**，行之间靠内部节奏分隔；
 * - `MATERIAL3`：由 [M3_PER_ITEM_CARDS] 决定是「每项一张独立卡」还是「一组一张卡」。
 *   当卡片外观由行组件承担时（每项独立卡），容器只提供间距并通过
 *   [LocalM3RowCarded] 下发标志——这样行组件在「共享一张卡」的列表里仍能退化为裸行。
 *
 * 注意：组内若放了**非行组件**（统计表、说明文字等），需要用 [SesameCard]
 * 显式包一层，它们不会自动获得卡片外观。
 */
@Composable
fun SesameCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Column(
            modifier = modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content
        )

        UiStyle.MATERIAL3 -> if (M3_PER_ITEM_CARDS) {
            CompositionLocalProvider(LocalM3RowCarded provides true) {
                Column(
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(M3CardGap),
                    content = content
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(sesameCardShape())
                    .background(sesameSurfaceContainer()),
                content = content
            )
        }
    }
}

/**
 * 单项卡片容器：给「不是行组件」的内容（统计表、说明段落、自定义块）一张卡。
 *
 * 只在「每项一张卡」的前提下才补背景——否则外层 [SesameCardGroup] 本身就是一张卡，
 * 再套一层会变成卡中卡。Miuix 下始终不着色。
 */
@Composable
fun SesameCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val needOwnCard = LocalUiStyle.current == UiStyle.MATERIAL3 &&
        M3_PER_ITEM_CARDS && LocalM3RowCarded.current
    if (needOwnCard) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(sesameCardShape())
                .background(sesameSurfaceContainer()),
            content = content
        )
    } else {
        Column(modifier = modifier.fillMaxWidth(), content = content)
    }
}

/* ───────────────────────── 日志卡片 ───────────────────────── */

/**
 * 单条日志卡片：标签 + 时间 + 正文。
 *
 * M3 侧改用 `surfaceContainerLow` + 12dp 圆角；标签用 tertiary
 * （MD3 中第三级强调色，不与主操作的 primary 抢注意力）。
 */
@Composable
fun SesameLogCard(tag: String, time: String, body: String) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixText(
                    text = tag,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary
                )
                MiuixText(
                    text = time,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                MiuixText(
                    text = body,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }

        UiStyle.MATERIAL3 -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = sesameSurfaceContainer(),
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/* ───────────────────────── 列表卡 ───────────────────────── */

/**
 * 列表型卡片容器：整块圆角背景，子内容自行决定内部留白。
 *
 * 用于好友统计、日志列表这类「一张卡一项」的场景，与 [SesameCardGroup]
 * （一行一项的设置分组）区分开。[shape] 让调用方可以「首尾圆角 / 中间直角」
 * 拼出连续卡片视觉。
 */
@Composable
fun SesameListCard(
    modifier: Modifier = Modifier,
    shape: Shape = sesameCardShape(),
    content: @Composable ColumnScope.() -> Unit
) {
    val container = sesameSurfaceContainer()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container),
        content = content
    )
}

/** 拼接用的形状梯度：连续卡片的首项 / 中间项 / 末项 */
fun sesameGroupedShape(first: Boolean, last: Boolean, radius: Dp = 16.dp): Shape = when {
    first && last -> RoundedCornerShape(radius)
    first -> RoundedCornerShape(topStart = radius, topEnd = radius)
    last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    else -> RectangleShape
}

/* ───────────────────────── 状态卡 ───────────────────────── */

/**
 * 首页模块状态卡。
 *
 * - `MIUIX`：沿用模块原有的绿色状态卡，视觉不变；
 * - `MATERIAL3`：**克制的状态条**——不是铺满 primaryContainer 的大色块，
 *   而是「细描边容器 + 状态圆点指示 + 一行说明」。理由：这块占位在首屏最上方，
 *   大色块会把视觉重心压在「状态」这个信息量很低的内容上，反而挤压了下面的
 *   数据统计；MD3 里这类「模块已就绪」属于低优先级的状态反馈，用 outlineVariant
 *   描边 + 小圆点即可表达，激活/未激活的区分由圆点与文字色承担。
 */
@Composable
fun SesameStatusCard(
    activated: Boolean,
    statusText: String,
    lines: List<String>,
    icon: ImageVector? = null
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> {
            val fg = Color(0xFF2E7D32)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        MiuixText(text = statusText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = fg)
                        Spacer(Modifier.height(4.dp))
                        lines.forEach { line ->
                            MiuixText(text = line, fontSize = 14.sp, color = fg)
                        }
                    }
                    if (activated && icon != null) {
                        Image(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            colorFilter = ColorFilter.tint(fg)
                        )
                    }
                }
            }
        }

        UiStyle.MATERIAL3 -> {
            // 激活 = 固定的浅绿卡面（见 sesameActivatedColors：不跟壁纸，绿是跨壁纸都成立的
            // 「通行」色，也让 MD3 与 HyperOS 两张卡观感一致）；
            // 未激活 = 中性卡面 + outline 圆点，刻意不用 errorContainer：
            // 模块未启用是用户自己的选择，不是错误。
            val activeColors = if (activated) sesameActivatedColors() else null
            val accent = activeColors?.content ?: MaterialTheme.colorScheme.outline
            val label = activeColors?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
            val secondary = activeColors?.content?.copy(alpha = 0.8f)
                ?: MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = sesameCardShape(),
                color = activeColors?.container ?: sesameSurfaceContainer(),
                tonalElevation = 0.dp,
                // 激活时底色本身就是状态提示，不再叠边框，免得浅绿底被灰线勾得发脏
                border = if (activated) null else androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态圆点：8dp 实心，是这张卡唯一的彩色元素
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = label
                        )
                        if (lines.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = lines.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = secondary
                            )
                        }
                    }
                    if (activated && icon != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
