package io.github.aw1y2z.sesame.ui.theme

import io.github.aw1y2z.sesame.ui.theme.SesameIcons
import io.github.aw1y2z.sesame.ui.theme.sesameIconHere
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.getValue

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
internal val M3RowMinHeight = 56.dp

/** 双行（标题 + 摘要）可点按列表项最小高度 */
internal val M3TwoLineRowMinHeight = 64.dp

/**
 * 不可用行的内容透明度。
 *
 * 0.38 是 MD3 对 disabled 内容的规范值（`onSurface` 38% 叠在容器上）。
 * 只有**整行不可用**时才用它；单个控件不可用由控件自身的 disabled 配色负责，
 * 两者叠加会让文字淡到看不清。
 */
internal val M3DisabledContentAlpha = 0.38f

/**
 * 只读信息行的垂直内边距。
 *
 * 只读行（如 [SesameInfoRow]「模块状态 / 已激活」这类）没有点击区，
 * 不该继承 56dp 的触摸目标高度 —— 那是给手指用的，不是给眼睛用的。
 * 之前的做法是 `heightIn(min = 56.dp)` 硬撑，结果单行文字只有 ~13dp，
 * 上下各留 ~21dp 空白，整屏看起来「行距很大、内容很空」。
 * 改为由**内容自然撑开**（16sp 文字 + 上下 8dp ≈ 40dp），节奏立刻收紧。
 */
internal val M3InfoRowVerticalPadding = 8.dp

/** 行内前后内边距 */
internal val M3RowStartPadding = 16.dp
internal val M3RowEndPadding = 16.dp

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
internal val M3ControlRowStartPadding = 14.dp

/** 可点按行自身的垂直内边距（配合卡片容器 8dp 形成节奏） */
internal val M3RowVerticalPadding = 4.dp

/**
 * 相邻独立卡片之间的间距。
 *
 * MD3 里卡片的分隔靠**间距**而不是分隔线：8dp 既能让两张卡清楚分开，
 * 又不会让一屏列表被切得过散。
 */
internal val M3CardGap = 8.dp

/** 行内前导图标尺寸（MD3 ListItem 规范值 24dp） */
internal val M3LeadingIconSize = 24.dp

/**
 * 前导图标与文字之间的间距。
 *
 * MD3 ListItem 规范里 leading 元素与 label 间距 16dp；但控件行
 * （RadioButton/Checkbox）自带约 2dp 视觉留白，这里对图标统一用 16dp，
 * 保证图标右缘与文字左缘的关系在所有行上一致。
 */
internal val M3LeadingIconGap = 16.dp

/**
 * 分组标题上下留白（MD 全量重构阶段4）。
 * 有分组小标题的页面统一走 CompactTitle 脚手架 + SesameCardGroup(header)：
 * - 组间 28dp 由脚手架 spacedBy(MochiListDefaults.SectionSpacing) 管 → 这里 top 设 0，避免双重叠加；
 * - 标题→卡片 10dp = mochi 规范 HeaderGap → bottom 设 10dp。
 * 仅剩的独立使用场景（如搜索空态"无匹配字段"）也落在同一节奏里。
 */
private val M3SectionTitleTopPadding = 0.dp
private val M3SectionTitleBottomPadding = 10.dp

/**
 * 一级 Tab（首页/日志/配置/设置）的小标题顶部额外留白。
 *
 * 一级 Tab 的脚手架显式设了 `contentSpacing = 0.dp`（关掉 mochi 默认 28dp 组间距），
 * 组间距全靠小标题自己的 padding 兜；而 [M3SectionTitleTopPadding] 为 0（原本假设脚手架
 * 会给 28dp），两处叠加导致小标题直接贴死上方卡片。二级页用脚手架默认 28dp，不受影响。
 *
 * 默认值 0 → 所有页面行为不变；只有 MD 一级 Tab 通过 CompositionLocalProvider 下发正值。
 * MIUIX 分支渲染 MiuixSmallTitle，根本不读这个值，天然不受影响。
 */
val LocalSectionTitleTopGap = compositionLocalOf { 0.dp }

/** 行尾图标尺寸（MD3 的 chevron 视觉尺寸） */
internal val M3TrailingIconSize = 20.dp

/**
 * 层级子项的度量（[SesameClickRow] 的 `isChild`）。
 *
 * 布局：`[起点 16] [引导线 2×20] [间距 14] [子项图标 20] [间距 16] [标题]`
 * → 子项图标落在 32dp、标题落在 68dp。
 *
 * 相对父项（图标 16dp、标题 56dp）**整体内缩一档**，同时图标小一号、字号降一档、
 * 行高矮一档 —— 四个信号叠加，层级才立得住；只靠「标题对齐」是看不出来的。
 */
internal val M3ChildGuideGap = 14.dp
internal val M3ChildLeadingIconSize = 20.dp
internal val M3ChildLeadingIconGap = 16.dp
internal const val M3ChildIconAlpha = 0.75f

/** MIUIX 下子项的外层缩进（库组件 ArrowPreference 没有缩进参数，只能从外层让位） */
internal val MiuixChildRowIndent = 16.dp

/** 行尾开关与箭头之间的间距（两者并存时用，让开关不贴住箭头） */
internal val M3SwitchArrowGap = 4.dp

/**
 * 层级子项（[SesameClickRow] 的 `isChild`）的度量。
 *
 * 子项的层级信号靠**三件事叠加**，单靠缩进是看不出来的：
 * ① 标题缩进到与父项标题左对齐；② 字号降一档 + 转次要色（体量差）；
 * ③ 行高比父项矮一档（父项 56dp、子项 44dp）。
 */
internal val M3ChildRowMinHeight = 44.dp
internal val M3ChildRowVerticalPadding = 2.dp

/** 子项左侧的层级引导竖线 */
internal val M3ChildGuideWidth = 2.dp
internal val M3ChildGuideHeight = 20.dp
internal const val M3ChildGuideAlpha = 0.28f

/** 子项的行尾箭头比父项小一号 */
internal val M3ChildTrailingIconSize = 16.dp

/** MD3 底部导航图标标准尺寸（规范值 24dp） */
internal val M3NavBarIconSize = 24.dp

/** MD3 底部导航容器高度（规范值 80dp） */
internal val M3NavBarHeight = 80.dp

/**
 * 本项目实际使用的底栏高度：**60dp**（规范 80dp 收窄 20dp）。
 *
 * 为什么偏离 M3 规范：
 * 规范 80dp 配「指示器 32dp + 文字」这套排布后，上下各剩一段空槽（实测 80dp 里
 * 有 12dp 纯属空白），视觉上表现为「内容被底栏压住、底部空一截」，
 * 页面因此显得被压矮。
 *
 * 内部排布已同步压紧（指示器与文字零间距、文字行高等于字号），
 * 内容实际只占约 46dp；留 60dp 是给上下各留约 7dp 呼吸位。
 * 再压就会开始显得局促（图标/文字贴边），失去"这是一条底栏"的观感。
 *
 * ⚠️ 留白必须与本值**同步**：MiuixMainActivity 的
 * `padding(bottom = SesameNavBarHeight)` 引用它。底栏矮了而留白不变，
 * 内容反而会被盖住。
 */
internal val SesameNavBarHeight = 60.dp

/** MD3 选中指示器（规范值 64×32，全圆角） */
internal val M3NavIndicatorWidth = 64.dp
internal val M3NavIndicatorHeight = 32.dp

/** MD3 图标与标签之间的间距（规范值 4dp） */
internal val M3NavIconLabelGap = 4.dp

/**
 * 紧凑 dock 专用度量（与上面「底部导航」的 M3 规范值**分开**，不要互相替换）。
 *
 * dock 是自定义的紧凑形态、行高只有 56dp：若沿用 M3 的 32dp 指示器 + 4dp 间距，
 * 图标（24dp）在该框里上下各空 4dp，再加 4dp 间距 —— 图标与文字之间实际会空出约 8dp，
 * 在这么矮的一条胶囊里显得"图标和字分家了"。
 *
 * 现在：指示器 = 图标高度（上下不留白）+ 间距 **0dp** → 图标与文字直接贴合。
 * ⚠️ 光把间距设 0 还不够：`labelMedium` 的行高比字号大，文字**上方**还留着排版自带的空白，
 * 所以 CompactNavBar 里那行 label 显式把 `lineHeight` 设成等于 `fontSize`，两边一起压才是真的零间距。
 *
 * 指示器在紧凑模式下只作定位框、不带底色，所以收矮不影响选中态（选中椭圆画在整列上）。
 */
internal val CompactNavIndicatorHeight = 24.dp
internal val CompactNavIconLabelGap = 0.dp

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

/**
 * 一级页大标题。
 *
 * @param topPadding 文字上方的留白。默认 `null` = 用本风格的既有值（MIUIX 8dp / M3 16dp）；
 *   一级页的**常驻标题栏**（[SesamePageTitleBar]）会显式传更小的值 —— 标题栏自身已经吃过
 *   `statusBarsPadding()`，再叠 8/16dp 会把大标题顶得太低（实测 66dp，明显偏下）。
 * @param bottomPadding 文字下方的留白；标题栏里传小值可以收紧「大标题 → 第一个小标题」的间距。
 */
@Composable
fun SesamePageTitle(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixText(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = topPadding ?: 8.dp, bottom = bottomPadding)
        )

        UiStyle.MATERIAL3 -> Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            // 不加粗：MD3 的 headline 系列规范字重是 Regular，
            // 层级靠字号（32sp）+ 字距建立，加粗会立刻滑向 HyperOS 观感。
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = topPadding ?: 16.dp, bottom = bottomPadding)
        )
    }
}

/**
 * 一级页「常驻大标题栏」：把 [SesamePageTitle] 钉在顶部，滚动时内容从它下方穿过。
 *
 * 为什么需要它：一级页的大标题原本是**滚动内容的第一项**（写在各 Tab 的 Column 里），
 * 手指一滑就跟着滚走，页面失去标题锚点；而且它紧贴状态栏，整页看起来"顶得太靠上"。
 *
 * 形态对标 SukiSU Ultra 等三方应用（实机测量）：
 * - 标题**恒定停在顶栏**、位置不随滚动移动（参考 App 标题恒在 y=181px，滚动前后不动）；
 * - 未滚动时顶栏底色 = 页面底色（视觉上"没有栏"，与页面融为一体）；
 * - 滚动后顶栏**微微加深约 6%**，把从下方穿过的内容压住
 *   （参考 App 实测 249 → 237 ≈ −5%；这里取 6%，浅色下 242 → 约 229，同一档）。
 *   刻意**不换整块色、不加分割线** —— 那会让顶栏看起来像一条悬浮的横条。
 *
 * ⚠️ 顶栏自己吃 [statusBarsPadding]，接进 `SesameScaffold(topBar = ...)` 后
 * 不要再由外部补状态栏内边距（与 [SesameTopBar] 的做法一致）。
 *
 * @param scrolled 页面是否已滚动（由调用方订阅当前页 ScrollState 得出）。只影响顶栏底色深浅。
 */
@Composable
fun SesamePageTitleBar(title: String, scrolled: Boolean = false) {
    val container = sesameSurface()
    // 「加深一档」用**向 onSurface 插值**表达：明暗两套主题都自动成立，也不依赖具体色槽
    // （注意 sesameSurfaceContainer() 是**卡片色**、比页面底更亮，语义不对，不能拿来当"加深"）。
    val deepened = lerp(container, sesameOnSurface(), 0.06f)
    val deepen by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "pageTitleBarDeepen"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 必须**不透明**：顶栏就是靠这块底色把从下方滚过的内容挡住
            .background(lerp(container, deepened, deepen))
            .statusBarsPadding()
            // 不额外加竖向内边距：标题自己的上下留白由下面 SesamePageTitle 显式给，
            // 一处控制、避免"栏留一点 + 标题再留一点"叠起来。
            // 四次实测迭代（标题墨迹顶部距屏幕顶；K80 Pro 状态栏 inset = 50dp）：
            //   改造前 36dp（太靠上）→ 加 16dp 后 82dp（太靠下）→ 66dp（仍偏下）→ 现在 56dp ✓
            // 参考 App（SukiSU Ultra）实测 64dp；本方案比它略高，是老板逐轮看过后的选择。
            .padding(horizontal = 16.dp)
    ) {
        // 复用一级页大标题的排版（32sp / headlineMedium），与改造前保持同一套字号体系。
        // 上下留白显式传小值（不再用 SesamePageTitle 的 8/16dp 默认）：
        //   上 6dp → 标题抬高；
        //   下 4dp → 收紧「大标题 → 第一个小标题」的间距（原 12dp，实测该间距 59dp 偏大）。
        SesamePageTitle(text = title, topPadding = 6.dp, bottomPadding = 4.dp)
    }
}

/** 分组小节标题 */
@Composable
fun SesameSectionTitle(text: String) {
    when (LocalUiStyle.current) {
        // MIUIX 沿用库组件：它的留白与字号就是这一风格下"分组标题"的定义，不要自己重画
        UiStyle.MIUIX -> MiuixSmallTitle(text = text)

        UiStyle.MATERIAL3 -> Box(
            Modifier.padding(
                start = 0.dp,
                // 一级 Tab 注入额外顶部留白，避免小标题贴死上方卡片；其它页面默认 0 不变。
                top = M3SectionTitleTopPadding + LocalSectionTitleTopGap.current,
                bottom = M3SectionTitleBottomPadding
            )
        ) {
            SesameSectionTitleLabel(text)
        }
    }
}

/**
 * 分组标题的**文字本体**（不含上下留白）。
 * <p>留给需要自己控制行内排版的场景复用，例如可折叠标题 —— 那里要让文字与箭头在同一行里
 * 垂直居中，不能吃标题自带的不对称留白（MD3 是上 24 / 下 8，会让文字整体偏下）。
 */
@Composable
private fun SesameSectionTitleLabel(text: String) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixText(
            text = text,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        UiStyle.MATERIAL3 -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 可展开 / 收起的分组标题。
 *
 * 与 [SesameSectionTitle] 只差两点：**整行可点**、右侧多一个随状态旋转的箭头。
 * 用于条目很多的分组列表 —— 先让用户看到有哪些分组，按需展开，不必一路滑到底。
 *
 * 展开状态由调用方持有（组件本身不存状态）；标题文字里建议带上条目数（如「农场肥料任务（18）」），
 * 这样收起时也能一眼看到组里有多少条。
 */
@Composable
fun SesameExpandableSectionTitle(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 先裁形状、再挂点击：按压高亮跟着变成圆角块。
            // 不裁的话，Material 默认的 indication 是**通栏直角**灰底，在 Miuix 风格下很突兀。
            .clip(SectionTitleRowShape)
            .clickable(onClick = onToggle)
            // 行内只留**对称**的上下留白，文字与箭头才能真正处在同一水平线上；
            // 分组之间的呼吸感由这个值给，而不是标题自带的不对称留白。
            .padding(vertical = SectionTitleRowPaddingV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            SesameSectionTitleLabel(text)
        }
        val tint = sesameOnSurfaceVariant()
        val arrow = Modifier
            .rotate(if (expanded) 180f else 0f)
            .size(M3TrailingIconSize)
        val desc = if (expanded) "收起" else "展开"
        when (LocalUiStyle.current) {
            // 与 SesameRows 的惯例一致：MIUIX 走库图标、M3 走 material3 图标，
            // 避免在 Miuix 主题下依赖 MaterialTheme 是否可用
            UiStyle.MIUIX -> MiuixIcon(
                imageVector = sesameIconHere(SesameIcons.ChevronDown),
                contentDescription = desc,
                tint = tint,
                modifier = arrow
            )

            UiStyle.MATERIAL3 -> Icon(
                imageVector = sesameIconHere(SesameIcons.ChevronDown),
                contentDescription = desc,
                tint = tint,
                modifier = arrow
            )
        }
    }
}

/** 可折叠分组标题：行内对称的上下留白（文字与箭头都以这一行居中） */
private val SectionTitleRowPaddingV = 12.dp

/** 可折叠分组标题：按压高亮的圆角（配合 clip，避免通栏直角灰块） */
private val SectionTitleRowShape = RoundedCornerShape(14.dp)
