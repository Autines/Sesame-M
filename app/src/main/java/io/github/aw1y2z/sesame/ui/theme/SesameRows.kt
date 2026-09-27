package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/* ───────────────────────── 列表行 ─────────────────────────
 * 本文件是「风格无关 UI 组件集」的一部分（整体约定与分层说明见 SesameFoundation.kt 顶部）。
 * 每个组件内部按 LocalUiStyle 分派到 Miuix / Material 3 两套实现，
 * 因此页面只依赖这些组件 —— 换肤不会触碰任何业务逻辑。
 */
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
 * 下拉选择行：右侧显示**当前取值**，点击弹出浮层菜单选择候选项。
 *
 * 与 [SesameRadioRow] 的区别是「候选集是否常驻页面」：
 * - [SesameRadioRow] 把候选项一条条铺在卡片里（选项少、且想让用户一眼看到全部时合适）；
 * - 本组件把候选项收进菜单，行上只留当前值——**行数恒定**、页面更短，
 *   与系统设置里「界面风格 / 主题样式」这类单值选项是对应的交互。
 *
 * [options] 与 [selectedIndex] 用**索引**对应，调用方传展示文案即可
 * （界面风格的枚举 code 与展示名并不相同，不适合直接比较字符串）。
 *
 * Miuix 分支没有对等的「带取值 + 浮层菜单」组件，这里用手工行承载；
 * 菜单本身仍用 material3 的浮层——浮层是瞬时出现的覆盖层，不参与卡片的静态观感，
 * 因此不会破坏 HyperOS 风格的一致性。
 */
@Composable
fun SesameSelectRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    /** 菜单宽度。默认值按「HyperOS 风格 / Material」这类短文案定；过长可覆写 */
    menuWidth: Dp = 116.dp
) {
    var expanded by remember { mutableStateOf(false) }
    // 记录最近一次点击在行内的局部像素坐标，作为浮层菜单的锚点（从点击处弹出，而非固定从行底）
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    val selectedLabel = options.getOrNull(selectedIndex).orEmpty()
    val dismiss = { expanded = false }

    when (LocalUiStyle.current) {
        // pointerInput 必须挂在 Box（= Popup 的锚点容器）上：pressOffset 才与 Popup offset
        // 同一坐标系。若挂在带 padding 的 Row 内层，坐标会差出一个 padding 量，
        // 锚点整体偏离指尖（实测 M3 分支偏 16dp 横向 / 4dp 纵向）。
        UiStyle.MIUIX -> Box(
            modifier = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            pressOffset = event.changes.first().position
                        }
                    }
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = true }
                    // 内边距与库行组件**完全同值**：BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)。
                    // 外层 SesameCardGroup 已给 16dp，库的行组件自身又带一份 16dp；本行原本只有外层那一份，
                    // 于是同一张卡里只有本行少缩进一档（实测差 44px ≈ 16dp）、行高也矮一截。
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // ⚠️ 字号/字重必须取库的排版 token，不要硬写 sp。
                    // 库的行组件（MiuixSwitchPreference / MiuixArrowPreference）都建在
                    // BasicComponent 上，它的排版是【固定】的：
                    //   标题 = textStyles.headline1 + FontWeight.Medium；摘要 = textStyles.body2，
                    //   且标题与摘要之间【不再加 Spacer】（直接相邻）。
                    // 硬写 16sp/13sp 会得到"比同卡片其它行细一档、小一号"的字，
                    // 一张卡里两种字体观感，看起来就是"不搭"。
                    MiuixText(
                        text = title,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    if (!summary.isNullOrBlank()) {
                        MiuixText(
                            text = summary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                MiuixText(
                    text = selectedLabel,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            // 浮层以点击点为锚：直接作为 Box 子项，由 Popup 的 TopStart 对齐 + offset 定位到点击处
            SesameSelectMenu(
                expanded = expanded,
                options = options,
                selectedIndex = selectedIndex,
                width = menuWidth,
                onDismiss = dismiss,
                onSelect = onSelect,
                anchorPx = pressOffset
            )
        }

        UiStyle.MATERIAL3 -> M3RowCard(onClick = { expanded = true }) {
            // 同 MIUIX 分支：pointerInput 挂在 Box（Popup 锚点）上，坐标与 Popup offset 同系
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press) {
                                pressOffset = event.changes.first().position
                            }
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (summary.isNullOrBlank()) M3RowMinHeight else M3TwoLineRowMinHeight)
                        .padding(
                            start = M3RowStartPadding,
                            end = M3RowEndPadding,
                            top = M3RowVerticalPadding,
                            bottom = M3RowVerticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(Modifier.width(12.dp))
                    // 当前取值是这一行的「结论」：用主题强调色标出，与只读信息行的次要色区分开，
                    // 一眼能看出「这一行是可以改的，现在选的是它」
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // 浮层以点击点为锚：作为 Box 子项，由 Popup 的 TopStart 对齐 + offset 定位到点击处
                SesameSelectMenu(
                    expanded = expanded,
                    options = options,
                    selectedIndex = selectedIndex,
                    width = menuWidth,
                    onDismiss = dismiss,
                    onSelect = onSelect,
                    anchorPx = pressOffset
                )
            }
        }
    }
}

/**
 * [SesameSelectRow] 的浮层菜单：选中项 = 圆角色块 + 对勾。
 *
 * 为什么自己用 Popup + Surface 搭，而不用 material3 的 DropdownMenu：
 * 1. DropdownMenu 的容器 shape 是 MD3 规范的 extra-small（4dp），在手机上几乎看不出圆角，
 *    且它的签名**不暴露 shape / containerColor**，改不了；
 * 2. DropdownMenuItem 自带 112dp 最小宽和固定左右内边距，菜单会明显偏宽。
 * 自己搭之后宽度、圆角、色块、对勾全部可控。
 *
 * 对勾：选中/未选中**都占位**（未选中时把 tint 设为透明），
 * 否则未选中项会因为没有勾而整体左移，逐项文字不在一条竖线上。
 *
 * 宽度固定（不随最长文案伸缩）：候选文案长度不一（「跟随系统」vs「深色」），
 * 自适应会让菜单在不同取值下来回变宽，观感不稳定。
 */
@Composable
internal fun SesameSelectMenu(
    expanded: Boolean,
    options: List<String>,
    selectedIndex: Int,
    width: Dp,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    /** 点击点（Popup 锚点容器 Box 的局部像素坐标，与 [Popup] offset 同一坐标系）。菜单右缘与顶边对齐它，并从该处缩放淡入。 */
    anchorPx: Offset
) {
    if (!expanded) return
    val density = LocalDensity.current
    // 进场动画：从 0.92 缩放到 1、透明度 0→1。先用 appear 标志在首帧后置 true，
    // 保证从「起点状态」开始动，而不是合成即终态（那样看不到动画）。
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val progress by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "selectMenuPop"
    )
    val s = 0.92f + 0.08f * progress
    val menuWpx = with(density) { width.roundToPx() }
    // 以点击点为锚：菜单右缘对齐点击 X（从手指处向左展开），并夹在行宽内不溢出左边
    val x = (anchorPx.x - menuWpx).coerceAtLeast(0f)
    val y = anchorPx.y
    Popup(
        // 锚点 = 行左上（Box 包裹 Row，TopStart 对齐到行左上），再用 offset 移到点击处
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        // focusable 才能接收返回键；外部点击 / 返回键都会走 onDismissRequest
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier.scale(s).alpha(progress)
        ) {
        Surface(
            modifier = Modifier.width(width),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp
        ) {
            // 容器内边距与行内间距同步收紧：宽度变窄后若沿用原来的留白，
            // 只会让文字两侧空得更多，看不出「变窄」的效果
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 顺序有讲究——padding 在最外层，背景作用于内缩后的区域，
                            // 写反了色块就会顶满整行、贴住菜单边缘
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (selected) {
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(index) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
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
                // 与库行组件**同值**：BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)。
                // 原来只有垂直 8dp：日志页同一张卡里，这些「整行可点」的开关行会比
                // 库行（抓包记录等）少缩进 16dp、行高也矮一截，看起来不是一套。
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixText(
                text = title,
                modifier = Modifier.weight(1f),
                // 字号/字重同样取库的排版 token（标题 = headline1 + Medium）；
                // 硬写 16sp 且不带字重，字就比库行细一档、小一号。
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
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
