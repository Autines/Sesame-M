package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.effect.content.MochiMorphIcon
import ui.feice.mochi.navigation.MochiPeerTransition
import ui.feice.mochi.navigation.MochiPeerTransitionSpec
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiColorScheme

/**
 * MD 风格接入 Mochi Compose 的桥接层。
 *
 * 设计取舍（2026-11-17）：
 *
 * 1. **不重新取色**。mochi 自带 `ThemeController` / materialkolor 生成配色，
 *    但本项目 MD 侧的动态取色 + `surfaceContainerLow`/`High` 层次是实机量过像素调出来的
 *    （浅色卡片彩度 35.7%、与页面底亮度差约 11），换掉会把已确认的观感推翻。
 *    所以这里把项目现成的 [ColorScheme] 逐角色映射到 [MochiColorScheme]，
 *    mochi 只提供形状 / 字阶 / 动效 / 组件。
 *    副作用：顺带绕开 materialkolor 被 Gradle 抬到 5.0.1 的跨大版本兼容风险。
 *
 * 2. **MIUIX 一行不动**。[sesameShape] 按 [LocalUiStyle] 分派：
 *    只有 MD 分支拿 G2 连续圆角，HyperOS 分支仍是普通 [RoundedCornerShape]。
 *
 * 3. mochi 的形状阶梯见 `MochiShapes`：4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full。
 *    卡片与面板统一用 `extraLarge`（28dp），比本项目原先的 20dp 更接近 MD3 Expressive。
 */
object MochiBridge {

    /** mochi 规范：卡片 / 面板 / 分组容器的圆角 */
    val CARD_CORNER: Dp = 28.dp

    /** mochi 规范：分组行内部的图标块等二级容器 */
    val INNER_CORNER: Dp = 12.dp

    /** 胶囊（标签、开关轨道、芯片） */
    val CAPSULE_CORNER: Dp = 999.dp
}

/**
 * 按当前风格给出圆角形状。
 *
 * MD 分支返回 **G2 连续曲率圆角**（直边 → 贝塞尔过渡 → 圆弧，曲率连续），
 * 这是 mochi 最有辨识度的一点；普通 `RoundedCornerShape` 在圆弧起点会有一次曲率突变。
 * MIUIX 分支保持普通圆角，视觉零变化。
 */
@Composable
fun sesameShape(radius: Dp = MochiBridge.CARD_CORNER): Shape =
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        MochiRoundedRectangle(radius)
    } else {
        RoundedCornerShape(radius)
    }

/**
 * 把项目已有的 M3 色板桥接成 mochi 色板。
 *
 * 只做角色搬运，不改任何一个色值；[ColorScheme] 缺少的 `shadow` 用黑色补
 * （mochi 的阴影档位自带透明度，`shadow` 只作为取色基底）。
 */
fun sesameMochiColorScheme(m3: ColorScheme, dark: Boolean): MochiColorScheme = MochiColorScheme(
    isDark = dark,
    primary = m3.primary,
    onPrimary = m3.onPrimary,
    primaryContainer = m3.primaryContainer,
    onPrimaryContainer = m3.onPrimaryContainer,
    inversePrimary = m3.inversePrimary,
    secondary = m3.secondary,
    onSecondary = m3.onSecondary,
    secondaryContainer = m3.secondaryContainer,
    onSecondaryContainer = m3.onSecondaryContainer,
    tertiary = m3.tertiary,
    onTertiary = m3.onTertiary,
    tertiaryContainer = m3.tertiaryContainer,
    onTertiaryContainer = m3.onTertiaryContainer,
    background = m3.background,
    onBackground = m3.onBackground,
    surface = m3.surface,
    onSurface = m3.onSurface,
    surfaceVariant = m3.surfaceVariant,
    onSurfaceVariant = m3.onSurfaceVariant,
    surfaceTint = m3.surfaceTint,
    inverseSurface = m3.inverseSurface,
    inverseOnSurface = m3.inverseOnSurface,
    error = m3.error,
    onError = m3.onError,
    errorContainer = m3.errorContainer,
    onErrorContainer = m3.onErrorContainer,
    outline = m3.outline,
    outlineVariant = m3.outlineVariant,
    scrim = m3.scrim,
    shadow = Color.Black,
    surfaceBright = m3.surfaceBright,
    surfaceDim = m3.surfaceDim,
    surfaceContainerLowest = m3.surfaceContainerLowest,
    surfaceContainerLow = m3.surfaceContainerLow,
    surfaceContainer = m3.surfaceContainer,
    surfaceContainerHigh = m3.surfaceContainerHigh,
    surfaceContainerHighest = m3.surfaceContainerHighest,
)

/* ─────────────────────── 场景动效桥接（仅 MD 风格生效）───────────────────────
 *
 * 这三个包装组件内部按 [LocalUiStyle] 分派：MATERIAL3 用 mochi 的内容变换组件
 * （数字逐位滚动 / 文字模糊替换 / 图标原位变形），MIUIX 回退到普通静态展示。
 * 调用点因此可以写在公共 Composable 里而不破坏 HyperOS 观感。
 *
 * 严格遵循 mochi《内容变换》规范：只对**变化的状态**做过渡，静止数值用 MochiCounterText
 * 也与普通 Text 像素重合（库保证 tnum 等宽 + 静止无模糊）。
 */

/** 数字：MD 用 mochi 逐位滚动计数器；MIUIX 回退静态文本。 */
@Composable
fun SesameCounterText(
    value: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null
) {
    val textStyle = TextStyle(fontSize = fontSize, fontWeight = fontWeight)
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        MochiCounterText(
            value = value,
            modifier = modifier,
            color = color,
            style = textStyle
        )
    } else {
        androidx.compose.material3.Text(
            text = value.toString(),
            modifier = modifier,
            color = color,
            style = textStyle
        )
    }
}

/** 状态文字：MD 用 mochi 模糊替换（切换时旧字糊化淡出、新字清晰）；MIUIX 回退静态文本。 */
@Composable
fun SesameBlurText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null
) {
    val textStyle = TextStyle(fontSize = fontSize, fontWeight = fontWeight)
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        MochiBlurText(
            text = text,
            modifier = modifier,
            color = color,
            style = textStyle
        )
    } else {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            style = textStyle
        )
    }
}

/** 图标：MD 用 mochi 原位变形（图标切换时笔画一一对应挪过去）；MIUIX 回退静态图标。 */
@Composable
fun SesameMorphIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
    size: Dp = 24.dp
) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        MochiMorphIcon(icon = icon, modifier = modifier, tint = tint, size = size)
    } else {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
    }
}

/**
 * 一级 Tab 分页容器：MD 风格用 mochi 的 [MochiPeerTransition] 景深样式
 * （[MochiPeerTransitionSpec.Depth]：不横移，旧页原地后退缩小淡出、新页从后面放大淡入，
 * 与 GitHub demo 首页标签切换一致）；
 * MIUIX 保持原 [HorizontalPager]（整页平滑滑动，老板调过的观感零变化）。
 *
 * 两个分支都遵守同一个契约：
 * - [selectedTab] 是唯一真源（点击底栏 → 外部改它 → 这里跟到对应页；手指切页 → [onTabChange] 回灌）；
 * - 每页内容由 [content] 提供，页内滚动/状态由调用方持有，切走再回来保持。
 */
@Composable
fun SesameTabPager(
    tabCount: Int,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        val pages = remember(tabCount) { (0 until tabCount).toList() }
        MochiPeerTransition(
            targetState = selectedTab,
            modifier = modifier,
            pages = pages,
            onPageChange = onTabChange,
            spec = MochiPeerTransitionSpec.Depth,
            content = content
        )
    } else {
        val pagerState = rememberPagerState(initialPage = selectedTab) { tabCount }
        var isProgrammaticScroll by remember { mutableStateOf(false) }
        LaunchedEffect(selectedTab) {
            if (pagerState.currentPage == selectedTab) return@LaunchedEffect
            isProgrammaticScroll = true
            try {
                pagerState.animateScrollToPage(selectedTab)
            } finally {
                isProgrammaticScroll = false
            }
        }
        LaunchedEffect(pagerState.currentPage) {
            if (!isProgrammaticScroll && selectedTab != pagerState.currentPage) {
                onTabChange(pagerState.currentPage)
            }
        }
        HorizontalPager(state = pagerState, modifier = modifier) { page ->
            content(page)
        }
    }
}
