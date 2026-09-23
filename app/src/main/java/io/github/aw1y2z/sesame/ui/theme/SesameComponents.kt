package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference as MiuixCheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference as MiuixRadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 风格无关的 UI 组件集。每个组件内部按 [LocalUiStyle] 分派到 Miuix 或 Material 3 实现，
 * 因此同一份页面代码在两套风格下都成立：
 *
 * - `MIUIX` 分支严格保持模块原有外观，不做任何视觉改动；
 * - `MATERIAL3` 分支按 M3 规范重绘（角色化取色、8dp 栅格、56dp 最小行高、形状阶梯）。
 *
 * 页面只允许使用这里（以及 [SesameTheme] 中 sesame*() 语义色）的能力，
 * 以便「换肤」不触碰任何业务逻辑。
 */

/* ───────────────────────── M3 度量常量 ─────────────────────────
 * MD3 的「像不像」主要来自节奏而非配色：行高、留白、字重三者一致，
 * 观感才成立。这里把度量收敛成常量，页面与组件都不再写魔法数字。
 *
 * 关键取舍：列表行的**行高由内容撑开**（min height 只作为下限），
 * 行自身垂直内边距保持 4dp（M3 ListItem 的单行/双行呼吸感来自
 * 上下行之间的合计间距，而不是每行内部的厚内边距）。
 */

/**
 * 可点按列表项的最小高度 —— 56dp 是 MD3 为**触摸目标**定的下限，
 * 只对能点的行（[SesameClickRow] / 开关 / 单选 / 复选）生效。
 */
private val M3RowMinHeight = 56.dp

/** 双行（标题 + 摘要）可点按列表项最小高度 */
private val M3TwoLineRowMinHeight = 64.dp

/**
 * 不可用行的内容透明度。
 *
 * 0.38 是 MD3 对 disabled 内容的规范值（`onSurface` 38% 叠在容器上）。
 * 只有**整行不可用**时才用它；单个控件不可用由控件自身的 disabled 配色负责，
 * 两者叠加会让文字淡到看不清。
 */
private val M3DisabledContentAlpha = 0.38f

/**
 * 只读信息行的垂直内边距。
 *
 * 只读行（如 [SesameInfoRow]「模块状态 / 已激活」这类）没有点击区，
 * 不该继承 56dp 的触摸目标高度 —— 那是给手指用的，不是给眼睛用的。
 * 之前的做法是 `heightIn(min = 56.dp)` 硬撑，结果单行文字只有 ~13dp，
 * 上下各留 ~21dp 空白，整屏看起来「行距很大、内容很空」。
 * 改为由**内容自然撑开**（16sp 文字 + 上下 8dp ≈ 40dp），节奏立刻收紧。
 */
private val M3InfoRowVerticalPadding = 8.dp

/** 行内前后内边距 */
private val M3RowStartPadding = 16.dp
private val M3RowEndPadding = 16.dp

/**
 * 带前导控件（RadioButton / Checkbox）的行左内边距。
 *
 * 这类行不能让**行的左边缘**对齐 16dp，而要让**控件的视觉左缘**对齐 16dp ——
 * 因为 M3 的 RadioButton/Checkbox 自带约 2dp 的内部留白，
 * 若行也用 16dp，控件视觉上会往右偏出 2dp，与同页文字行对不齐。
 *
 * 实测（density 440）：行内边距 12dp → 圆钮视觉左缘落在 14.2dp，
 * 比文字行的 16dp 少了 1.8dp，肉眼可见「控件比文字更靠左」。
 * 14dp 让控件左缘精确落到 16dp。
 */
private val M3ControlRowStartPadding = 14.dp

/** 可点按行自身的垂直内边距（配合卡片容器 8dp 形成节奏） */
private val M3RowVerticalPadding = 4.dp

/**
 * 相邻独立卡片之间的间距。
 *
 * MD3 里卡片的分隔靠**间距**而不是分隔线：8dp 既能让两张卡清楚分开，
 * 又不会让一屏列表被切得过散。
 */
private val M3CardGap = 8.dp

/** 行内前导图标尺寸（MD3 ListItem 规范值 24dp） */
private val M3LeadingIconSize = 24.dp

/**
 * 前导图标与文字之间的间距。
 *
 * MD3 ListItem 规范里 leading 元素与 label 间距 16dp；但控件行
 * （RadioButton/Checkbox）自带约 2dp 视觉留白，这里对图标统一用 16dp，
 * 保证图标右缘与文字左缘的关系在所有行上一致。
 */
private val M3LeadingIconGap = 16.dp

/** 分组标题上下留白：上方需要与上一张卡拉开，下方贴近自己的卡片 */
private val M3SectionTitleTopPadding = 24.dp
private val M3SectionTitleBottomPadding = 8.dp

/** 行尾图标尺寸（MD3 的 chevron 视觉尺寸） */
private val M3TrailingIconSize = 20.dp

/**
 * 层级子项的度量（[SesameClickRow] 的 `isChild`）。
 *
 * 布局：`[起点 16] [引导线 2×20] [间距 14] [子项图标 20] [间距 16] [标题]`
 * → 子项图标落在 32dp、标题落在 68dp。
 *
 * 相对父项（图标 16dp、标题 56dp）**整体内缩一档**，同时图标小一号、字号降一档、
 * 行高矮一档 —— 四个信号叠加，层级才立得住；只靠「标题对齐」是看不出来的。
 */
private val M3ChildGuideGap = 14.dp
private val M3ChildLeadingIconSize = 20.dp
private val M3ChildLeadingIconGap = 16.dp
private const val M3ChildIconAlpha = 0.75f

/** MIUIX 下子项的外层缩进（库组件 ArrowPreference 没有缩进参数，只能从外层让位） */
private val MiuixChildRowIndent = 24.dp

/** 行尾开关与箭头之间的间距（两者并存时用，让开关不贴住箭头） */
private val M3SwitchArrowGap = 4.dp

/**
 * 层级子项（[SesameClickRow] 的 `isChild`）的度量。
 *
 * 子项的层级信号靠**三件事叠加**，单靠缩进是看不出来的：
 * ① 标题缩进到与父项标题左对齐；② 字号降一档 + 转次要色（体量差）；
 * ③ 行高比父项矮一档（父项 56dp、子项 44dp）。
 */
private val M3ChildRowMinHeight = 44.dp
private val M3ChildRowVerticalPadding = 2.dp

/** 子项左侧的层级引导竖线 */
private val M3ChildGuideWidth = 2.dp
private val M3ChildGuideHeight = 20.dp
private const val M3ChildGuideAlpha = 0.28f

/** 子项的行尾箭头比父项小一号 */
private val M3ChildTrailingIconSize = 16.dp

/** MD3 底部导航图标标准尺寸（规范值 24dp） */
private val M3NavBarIconSize = 24.dp

/** MD3 底部导航容器高度（规范值 80dp） */
private val M3NavBarHeight = 80.dp

/** MD3 选中指示器（规范值 64×32，全圆角） */
private val M3NavIndicatorWidth = 64.dp
private val M3NavIndicatorHeight = 32.dp

/** MD3 图标与标签之间的间距（规范值 4dp） */
private val M3NavIconLabelGap = 4.dp

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

/* ───────────────────────── 文本与标题 ───────────────────────── */

/** 通用文本。颜色缺省时使用当前风格的主要文字色。 */
@Composable
fun SesameText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixText(
            text = text,
            modifier = modifier,
            color = if (color == Color.Unspecified) MiuixTheme.colorScheme.onBackground else color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines
        )

        UiStyle.MATERIAL3 -> Text(
            text = text,
            modifier = modifier,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            // 显式指定字号时同步给出成比例的行高，避免继承 bodyLarge 的 24sp 行高把表格/小字撑得过松
            lineHeight = (fontSize.value * 1.4f).sp,
            maxLines = maxLines
        )
    }
}

/** 一级页大标题 */
@Composable
fun SesamePageTitle(text: String, bottomPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixText(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = bottomPadding)
        )

        UiStyle.MATERIAL3 -> Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            // 不加粗：MD3 的 headline 系列规范字重是 Regular，
            // 层级靠字号（32sp）+ 字距建立，加粗会立刻滑向 HyperOS 观感。
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = bottomPadding)
        )
    }
}

/** 分组小节标题 */
@Composable
fun SesameSectionTitle(text: String) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixSmallTitle(text = text)

        UiStyle.MATERIAL3 -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 与卡片左边缘对齐（MD3 分组标题的常规做法）。
            // MD3 里分组标题是**弱化**的标签而非彩色强调，故用 onSurfaceVariant；
            // 上 24 / 下 8 的留白把「标题贴着自己的卡片、与上一组拉开」的关系表达出来。
            modifier = Modifier.padding(
                start = 0.dp,
                top = M3SectionTitleTopPadding,
                bottom = M3SectionTitleBottomPadding
            )
        )
    }
}

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
private fun M3RowCard(
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
private fun M3LeadingIcon(icon: ImageVector?, enabled: Boolean = true) {
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

/* ───────────────────────── 列表行 ───────────────────────── */

/** 左标签 / 右取值的信息行（只读） */
@Composable
fun SesameInfoRow(label: String, value: String) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixText(text = label, fontSize = 15.sp, color = MiuixTheme.colorScheme.onBackground)
            MiuixText(text = value, fontSize = 15.sp, color = MiuixTheme.colorScheme.primary)
        }

        UiStyle.MATERIAL3 -> M3RowCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 只读行由内容自然撑开（≈40dp），不套 56dp 的触摸目标下限。
                    // 点击区是给手指的，只读行只需要眼睛舒服的呼吸感。
                    .padding(
                        start = M3RowStartPadding,
                        end = M3RowEndPadding,
                        top = M3InfoRowVerticalPadding,
                        bottom = M3InfoRowVerticalPadding
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    // 取值是这一行的「结论」，用 onSurfaceVariant 略低于标签但仍可读；
                    // MD3 不用颜色区分信息层级，靠字重与字色明度差。
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 可点按条目行：点击跳转 / 展开。
 *
 * [icon] 只在 M3 下渲染为前导图标（Miuix 分支严格保持模块原有外观，不接受图标）。
 *
 * [isChild] 用于**层级子项**：标题内缩到父项标题下方、字号降一档、行高矮一档，
 * 并在前导图标位画一根短竖线 —— 表示「本项没有自己的独立开关，随上一行一起生效」，
 * 只保留查看入口。日志页的「分类记录」用它表达「新村/农场 与 庄园 共用同一个日志文件」。
 */
@Composable
fun SesameClickRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    isChild: Boolean = false
) {
    when (LocalUiStyle.current) {
        // 库组件 ArrowPreference 没有缩进参数，只能从外层让位；形状仍完全交给库组件。
        UiStyle.MIUIX -> Box(
            modifier = if (isChild) Modifier.padding(start = MiuixChildRowIndent) else Modifier
        ) {
            MiuixArrowPreference(title = title, summary = summary, onClick = onClick)
        }

        UiStyle.MATERIAL3 -> M3RowCard(onClick = onClick) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = when {
                            isChild -> M3ChildRowMinHeight
                            summary.isNullOrBlank() -> M3RowMinHeight
                            else -> M3TwoLineRowMinHeight
                        }
                    )
                    .padding(
                        start = M3RowStartPadding,
                        end = M3RowEndPadding,
                        top = if (isChild) M3ChildRowVerticalPadding else M3RowVerticalPadding,
                        bottom = if (isChild) M3ChildRowVerticalPadding else M3RowVerticalPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isChild) {
                    // 树干引导线：一眼看出「这一行挂在上面那行之下」
                    Box(
                        modifier = Modifier
                            .width(M3ChildGuideWidth)
                            .height(M3ChildGuideHeight)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = M3ChildGuideAlpha),
                                RoundedCornerShape(M3ChildGuideWidth / 2)
                            )
                    )
                    Spacer(Modifier.width(M3ChildGuideGap))
                    // 子项保留自己的图标（少了它左侧会空一大块、像占位符），
                    // 但小一号并压暗，配合整体的内缩形成「轻」的一档
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = M3ChildIconAlpha),
                            modifier = Modifier.size(M3ChildLeadingIconSize)
                        )
                        Spacer(Modifier.width(M3ChildLeadingIconGap))
                    }
                } else {
                    M3LeadingIcon(icon)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        // 子项降一档字号并转次要色：体量差本身就是最有效的层级信号
                        style = if (isChild) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = if (isChild) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (!summary.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isChild) M3ChildTrailingIconSize else M3TrailingIconSize)
                )
            }
        }
    }
}

/**
 * 开关行。
 *
 * [onClick] 为空时整行点击即切换开关（Miuix 用原生 SwitchPreference 保持一致）；
 * 传入 [onClick] 时整行点击走该回调、右侧开关独立负责切换——用于「点行进详情、开关管记录」的日志页。
 *
 * [enabled] 为 false 时整行置灰且不响应任何点击（文字、图标、开关一起变淡）。
 * 用于「本项由上一个开关接管」的场景，例如「跟随系统设置」开启后的「深色模式」——
 * 此时该开关改了也不会生效，必须让它看起来就是不可改的，否则用户会反复点它找原因。
 */
@Composable
fun SesameSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    /**
     * 传入 [onClick]、即「整行可点进详情」时，是否在开关之后再画一个行尾箭头。
     *
     * **两者并存会显得拥挤**（行尾挤两个控件），而 MD3 里开关本身通常就是行尾控件。
     * 仅当同一张卡里**混排**了「有开关的行」与「纯可点行」、需要统一提示可点性时才打开。
     */
    showArrow: Boolean = true
) {
    if (onClick == null) {
        when (LocalUiStyle.current) {
            UiStyle.MIUIX -> MiuixSwitchPreference(
                title = title,
                summary = summary,
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )

            UiStyle.MATERIAL3 -> M3RowCard(
                // 不可用时传 null：既没有水波纹，也不会响应点击
                onClick = if (enabled) ({ onCheckedChange(!checked) }) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (summary.isNullOrBlank()) M3RowMinHeight else M3TwoLineRowMinHeight)
                        .padding(
                            start = M3RowStartPadding,
                            end = 12.dp,
                            top = M3RowVerticalPadding,
                            bottom = M3RowVerticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3LeadingIcon(icon, enabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.let {
                                if (enabled) it else it.copy(alpha = M3DisabledContentAlpha)
                            }
                        )
                        if (!summary.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                    if (enabled) it else it.copy(alpha = M3DisabledContentAlpha)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        enabled = enabled
                    )
                }
            }
        }
        return
    }

    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixText(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onBackground
            )
            MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            if (showArrow) {
                MiuixIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = sesameOnSurfaceVariant(),
                    modifier = Modifier
                        .padding(start = M3SwitchArrowGap)
                        .size(M3TrailingIconSize)
                )
            }
        }

        UiStyle.MATERIAL3 -> M3RowCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = M3RowMinHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文字区承载整行的点击热区（右侧开关独立负责切换）。
                // 注意 clickable 必须在 padding **之前**，否则内边距不算进热区，
                // 手指点在行首/行尾的留白上会没有反应。
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = enabled, onClick = onClick)
                        .padding(
                            start = M3RowStartPadding,
                            end = 12.dp,
                            top = M3RowVerticalPadding + 8.dp,
                            bottom = M3RowVerticalPadding + 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3LeadingIcon(icon, enabled)
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.let {
                                if (enabled) it else it.copy(alpha = M3DisabledContentAlpha)
                            }
                        )
                        if (!summary.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                    if (enabled) it else it.copy(alpha = M3DisabledContentAlpha)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
                if (showArrow) {
                    // 箭头留在最右缘，所有行的右边界因此对齐（开关只是排在箭头左边）
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.let {
                            if (enabled) it else it.copy(alpha = M3DisabledContentAlpha)
                        },
                        modifier = Modifier
                            .padding(start = M3SwitchArrowGap, end = M3RowEndPadding)
                            .size(M3TrailingIconSize)
                    )
                } else {
                    // 开关自己就是行尾控件：不加内边距会贴住卡片边缘
                    Spacer(Modifier.width(M3RowEndPadding))
                }
            }
        }
    }
}

/**
 * 单选行：用于互斥选项（如界面风格）。
 *
 * M3 下选择控件放在**行尾**：左侧留给「图标 + 名称」，与开关行、跳转行保持同一套
 * 阅读轴线（左边永远是「这是什么」，右边永远是「能对它做什么」）。
 * 控件与信息混在左侧时，一屏里两种行的心智模型不一致，扫读会不断重新定位。
 */
@Composable
fun SesameRadioRow(title: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixRadioButtonPreference(
            title = title,
            selected = selected,
            onClick = onClick
        )

        UiStyle.MATERIAL3 -> M3RowCard(onClick = onClick) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = M3RowMinHeight)
                    .padding(
                        start = M3RowStartPadding,
                        // 控件行的行尾内边距用控件行的对齐值，让圆钮的**视觉右缘**同样落在 16dp 网格上
                        end = M3ControlRowStartPadding,
                        top = M3RowVerticalPadding,
                        bottom = M3RowVerticalPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3LeadingIcon(icon)
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

/* ───────────────────────── 底部导航 ───────────────────────── */

/** 底部导航项 */
data class SesameNavItem(val icon: ImageVector, val label: String)

/** 底部导航栏 */
@Composable
fun SesameNavBar(items: List<SesameNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixNavigationBar {
            items.forEachIndexed { index, item ->
                MiuixNavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = item.icon,
                    label = item.label
                )
            }
        }

        UiStyle.MATERIAL3 -> {
            // 不用 material3 的 NavigationBarItem：它把图标当作「可缩放内容」处理好，
            // 但在大字号 / 高密度设备上图标会被撑到 40dp+，选中胶囊（64×32）装不下，
            // 且图标与文字的垂直间距会被拉得很开——正是「不像 MD3」的直观来源。
            // 这里按 M3 规范手写，度量完全可控：
            //   容器高 80dp / 图标 24dp / 指示器 64×32 全圆角 / 图标-文字间距 4dp
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(M3NavBarHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = selected == index
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(index) }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 选中指示器：64×32 全圆角胶囊（M3 规范）。
                            // 必须用 RoundedCornerShape(高/2) 而不是 CircleShape——
                            // CircleShape 会强制正圆，把 64×32 撑成 64×64 的大圆。
                            Box(
                                modifier = Modifier
                                    .size(width = M3NavIndicatorWidth, height = M3NavIndicatorHeight)
                                    .clip(RoundedCornerShape(M3NavIndicatorHeight / 2))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(M3NavBarIconSize)
                                )
                            }
                            Spacer(Modifier.height(M3NavIconLabelGap))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────────────── 顶部栏 ───────────────────────── */

/**
 * 二级/三级/四级页通用顶部栏：返回 + 标题 + 可选操作图标。
 *
 * - `MIUIX`：沿用模块原有的 56dp 顶部栏与 marquee 标题，长标题不会被截断；
 * - `MATERIAL3`：Medium Top App Bar 规格（64dp、surface 容器色、titleLarge、24dp 图标），
 *   操作图标用 M3 IconButton 承载，触摸热区满足 48dp 无障碍下限。
 *
 * 操作图标语义（与原实现一致）：[onImport] 用旋转 180° 的 Upload 表示「导入」，
 * [onExport] 用正向 Upload 表示「导出」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SesameTopBar(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExecute: (() -> Unit)? = null
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Column(
            Modifier
                .fillMaxWidth()
                .background(sesameSurface())
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixIconButton(onClick = onBack) {
                    MiuixIcon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = sesameOnSurface()
                    )
                }
                MiuixText(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = sesameOnSurface(),
                    maxLines = 1
                )
                if (onImport != null) {
                    MiuixIconButton(onClick = onImport) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = "导入",
                            tint = sesameOnSurface(),
                            modifier = Modifier.rotate(180f)
                        )
                    }
                }
                if (onExport != null) {
                    MiuixIconButton(onClick = onExport) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Upload,
                            contentDescription = "导出",
                            tint = sesameOnSurface()
                        )
                    }
                }
                if (onClear != null) {
                    MiuixIconButton(onClick = onClear) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = sesameOnSurface()
                        )
                    }
                }
                if (onShare != null) {
                    MiuixIconButton(onClick = onShare) {
                        MiuixIcon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "分享",
                            tint = sesameOnSurface()
                        )
                    }
                }
                if (onExecute != null) {
                    MiuixIconButton(onClick = onExecute) {
                        MiuixIcon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "执行",
                            tint = sesameOnSurface()
                        )
                    }
                }
            }
        }

        UiStyle.MATERIAL3 -> Surface(
            modifier = Modifier.fillMaxWidth(),
            // 顶栏跟随页面底色，滚动时靠内容与卡片的明暗差建立层次，
            // 而不是靠给顶栏换一块色（换色会让顶栏看起来像一条悬浮的横条）。
            color = sesameSurface(),
            tonalElevation = 0.dp
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = title,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (onImport != null) {
                        IconButton(onClick = onImport) {
                            Icon(
                                imageVector = Icons.Filled.Upload,
                                contentDescription = "导入",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    }
                    if (onExport != null) {
                        IconButton(onClick = onExport) {
                            Icon(
                                imageVector = Icons.Filled.Upload,
                                contentDescription = "导出",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onClear != null) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onExecute != null) {
                        IconButton(onClick = onExecute) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "执行",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────────────── 输入控件 ───────────────────────── */

/**
 * 搜索输入框（带前置放大镜、非空时可一键清空）。
 *
 * M3 侧用 `OutlinedTextField` 的搜索形态：圆角 28dp、surfaceContainerHigh 底色、
 * 无边框，贴近 MD3 规范里的 Search Bar 观感。
 */
@Composable
fun SesameSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索",
    modifier: Modifier = Modifier
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            label = "",
            modifier = modifier,
            leadingIcon = {
                MiuixIcon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = sesameOnSurfaceVariant(),
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    MiuixText(
                        text = "×",
                        fontSize = 16.sp,
                        color = sesameOnSurfaceVariant(),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { onValueChange("") }
                    )
                }
            }
        )

        UiStyle.MATERIAL3 -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.extraLarge,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = sesameSurfaceContainer(),
                unfocusedContainerColor = sesameSurfaceContainer(),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/** 文字按钮：对话框与内联编辑区的动作入口 */
@Composable
fun SesameTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = true
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixTextButton(text = text, onClick = onClick, modifier = modifier)

        UiStyle.MATERIAL3 -> TextButton(onClick = onClick, modifier = modifier) {
            Text(
                text = text,
                color = if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * 紧贴在某一行下方展开的内联编辑区（配合 [SesameExpandRow] 使用）。
 *
 * **输入即生效**：没有「取消 / 保存」按钮，每次输入直接经 [onValueChange] 落位，
 * 展开只是为了把注意力聚到这一行。输入不合法时不要弹窗打断节奏——
 * 由调用方通过 [isError] / [supportingText] 就地表达（M3 走 error 描边 + supportingText，
 * MIUIX 侧在输入框下方补一行同语义的小字）。
 */
@Composable
fun SesameInlineEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                SesameText(
                    text = supportingText,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 12.sp,
                    color = if (isError) sesameError() else sesameOnSurfaceVariant()
                )
            }
        }

        UiStyle.MATERIAL3 -> Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = if (label.isBlank()) null else {
                        { Text(text = label, style = MaterialTheme.typography.bodySmall) }
                    },
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = isError,
                    supportingText = if (supportingText.isNullOrBlank()) null else {
                        { Text(text = supportingText, style = MaterialTheme.typography.bodySmall) }
                    },
                    shape = MaterialTheme.shapes.small
                )
            }
        }
    }
}

/**
 * 「可展开编辑」的行：右侧文案为当前值，点击后在下方展开 [editor]。
 *
 * [expandable] 为 false 时退化为只读行（对应 READ_TEXT / URL_TEXT），
 * 此时不显示展开箭头——M3 的 List Item 规范里，没有后续动作的条目不应给出箭头暗示。
 */
@Composable
fun SesameExpandRow(
    title: String,
    summary: String?,
    expandable: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixArrowPreference(
            title = title,
            summary = summary,
            onClick = onClick
        )

        UiStyle.MATERIAL3 -> {
            val rowModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (summary.isNullOrBlank()) M3RowMinHeight else M3TwoLineRowMinHeight)
                .padding(
                    start = M3RowStartPadding,
                    end = M3RowEndPadding,
                    top = M3RowVerticalPadding,
                    bottom = M3RowVerticalPadding
                )
            val rowContent: @Composable RowScope.() -> Unit = {
                M3LeadingIcon(icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!summary.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (expandable) {
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(M3TrailingIconSize)
                    )
                }
            }
            // 不可展开的行不给任何点击反馈（只读语义），此时连卡片也不套点击层，
            // 避免出现「点了没反应」的涟漪。可展开的行用 M3RowCard(onClick) 保证
            // 涟漪被裁剪在卡片圆角内。
            M3RowCard(onClick = if (expandable) onClick else null) {
                Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                    rowContent()
                }
            }
        }
    }
}

/** 复选行：用于多选场景（选填编辑页的好友勾选） */
@Composable
fun SesameCheckRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixCheckboxPreference(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        UiStyle.MATERIAL3 -> M3RowCard(onClick = { onCheckedChange(!checked) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = M3RowMinHeight)
                    .padding(
                        start = M3RowStartPadding,
                        // 与单选行一致：选择控件落在行尾，视觉右缘对齐 16dp 网格
                        end = M3ControlRowStartPadding,
                        top = M3RowVerticalPadding,
                        bottom = M3RowVerticalPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

/** 列表内的单选行（左侧圆形选择框），用于选填编辑页的单选字段 */
@Composable
fun SesameRadioListRow(title: String, selected: Boolean, onClick: () -> Unit) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixRadioButtonPreference(
            title = title,
            selected = selected,
            onClick = onClick
        )

        UiStyle.MATERIAL3 -> M3RowCard(onClick = onClick) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = M3RowMinHeight)
                    .padding(
                        start = M3RowStartPadding,
                        // 与单选行一致：选择控件落在行尾，视觉右缘对齐 16dp 网格
                        end = M3ControlRowStartPadding,
                        top = M3RowVerticalPadding,
                        bottom = M3RowVerticalPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

/** 滑杆行：用于数值型选择（选填编辑页的「数量」） */
@Composable
fun SesameSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixSliderPreference(
            title = title,
            value = value,
            valueRange = valueRange,
            valueText = valueText,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished
        )

        UiStyle.MATERIAL3 -> M3RowCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = M3RowStartPadding,
                        end = M3RowEndPadding,
                        top = 12.dp,
                        bottom = 4.dp
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    onValueChangeFinished = { onValueChangeFinished() }
                )
            }
        }
    }
}

/* ───────────────────────── Chip ───────────────────────── */

/**
 * 过滤 Chip。两套风格都取「药丸 + 计数」的形态，
 * 但选中态的着色角色不同：Miuix 用 primaryContainer，M3 用 secondaryContainer
 * （MD3 里 secondaryContainer 才是 filter chip 的选中态角色）。
 */
@Composable
fun SesameFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg: Color
    val fg: Color
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        bg = if (selected) MiuixTheme.colorScheme.primaryContainer
        else MiuixTheme.colorScheme.surfaceContainer
        fg = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else sesameSurfaceContainer()
        fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            color = fg.copy(alpha = 0.7f)
        )
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

/* ───────────────────────── 对话框 ───────────────────────── */

/**
 * 确认对话框。
 *
 * - `MIUIX`：沿用模块原有的 16dp 圆角卡 + 两个文字按钮；
 * - `MATERIAL3`：换成标准 `AlertDialog`——标题/正文/按钮三层字号阶梯，
 *   确定性动作使用 primary 文字色、取消动作降级为 onSurfaceVariant，
 *   危险动作（[destructive]）使用 error 色，形成明确的视觉优先级。
 */
@Composable
fun SesameConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    MiuixText(text = title, color = MiuixTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    MiuixText(text = text, color = MiuixTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MiuixTextButton(text = dismissText, onClick = onDismiss)
                        Spacer(Modifier.width(8.dp))
                        MiuixTextButton(text = confirmText, onClick = onConfirm)
                    }
                }
            }
        }

        UiStyle.MATERIAL3 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = confirmText,
                        color = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/**
 * 带单行输入的对话框（扩展功能页的「自定义走路路径」）。
 *
 * 按钮数量在两套风格下都是动态的：M3 侧把这些动作统一收敛到
 * AlertDialog 的 `confirmButton` 槽位里横向排列，承载 1~2 个动作，
 * 避免为「清空队列」额外造一个自定义布局。
 */
@Composable
fun SesameInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    primaryAction: Pair<String, () -> Unit>,
    label: String = "",
    secondaryAction: Pair<String, () -> Unit>? = null
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    MiuixText(text = title, color = MiuixTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    MiuixTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = label,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (secondaryAction != null) {
                            MiuixTextButton(
                                text = secondaryAction.first,
                                onClick = secondaryAction.second
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        MiuixTextButton(text = primaryAction.first, onClick = primaryAction.second)
                    }
                }
            }
        }

        UiStyle.MATERIAL3 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = if (label.isBlank()) null else {
                        { Text(text = label, style = MaterialTheme.typography.bodySmall) }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.small
                )
            },
            confirmButton = {
                Row {
                    if (secondaryAction != null) {
                        TextButton(onClick = secondaryAction.second) {
                            Text(
                                text = secondaryAction.first,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = primaryAction.second) {
                        Text(
                            text = primaryAction.first,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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
