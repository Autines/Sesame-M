package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import ui.feice.mochi.effect.content.MochiThemeRevealHost
import ui.feice.mochi.effect.content.MochiThemeRevealState
import ui.feice.mochi.effect.content.revealIfShadeChanges
import ui.feice.mochi.effect.content.rememberMochiThemeRevealState
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.effect.glow.mochiPressGlow
import ui.feice.mochi.effect.glow.rememberMochiGlowState
import ui.feice.mochi.effect.overscroll.MochiOverscrollSpec
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.navigation.rememberMochiNavigator
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
 * 一级 Tab 分页容器（原 HorizontalPager 实现，仅供 MIUIX 旧 MainScreen 使用）。
 *
 * 方案A 起 MD 主界面走 `MochiMainScreen`（MochiTabScaffold 自带景深切换），
 * 不再调用本函数；mo chi 0.3.0 已删除 MochiPeerTransition，故这里统一为横滑实现。
 *
 * 契约：[selectedTab] 是唯一真源（点击底栏 → 外部改它 → 这里跟到对应页；手指切页 →
 * [onTabChange] 回灌）；每页内容由 [content] 提供，页内滚动/状态由调用方持有。
 */
@Composable
fun SesameTabPager(
    tabCount: Int,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
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

/* ─────────────────────── 滚动回弹 / 惯性（仅 MD 风格生效）───────────────────────
 *
 * mochi 的 ProvideMochiOverscroll 内部替换 Foundation 的 LocalOverscrollFactory，
 * 因此**包在根部后，子树里所有原生 verticalScroll / LazyColumn 自动获得 Cresto 回弹**，
 * 不需要逐个滚动容器传 overscrollEffect。MIUIX 分支不包，滚动观感零变化。
 */

/**
 * 滚动回弹 Provider：MD 风格包 [ProvideMochiOverscroll]，MIUIX 直出 content。
 *
 * 必须包在主题根部（[SesameTheme] 内），且只在 MD 分支包——否则 HyperOS 风格
 * 也会被迫用 mochi 的回弹引擎，违反「MIUIX 一行不动」。
 */
@Composable
fun SesameOverscroll(content: @Composable () -> Unit) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        ProvideMochiOverscroll(spec = MochiOverscrollSpec(), content = content)
    } else {
        content()
    }
}

/* ───────────────────────── 主题揭开（切明暗）─────────────────────────
 * mochi 规范：整套换装（明暗、风格）不要瞬切，用拍照 + 从点击处圆形揭开。
 * 只在 MD 分支挂 Host；MIUIX 直出 content，观感零变化。
 *
 * 结构：根部 SesameTheme 里 remember 一个 state → MochiThemeRevealHost 包住内容
 * → 用 CompositionLocal 把 state 下发给设置页；设置页切明暗时调
 * sesameRevealThemeChange(oldDark, newDark) { sesameApplyTheme(...) }，
 * 由 mochi 先拍屏、执行 change、再揭开新配色。
 */

/** 当下组合树里的主题揭开状态；MD 根部注入，非 MD / 未接 Host 时为 null（直接切换）。 */
val LocalThemeReveal = staticCompositionLocalOf<MochiThemeRevealState?> { null }

/**
 * 主题揭开 Host：MD 分支包 [MochiThemeRevealHost] 并下发 state；MIUIX 直出。
 * 必须包在主题内容的最外层（mochi 要用 PixelCopy 拍整帧、读 LocalView）。
 */
@Composable
fun SesameThemeReveal(content: @Composable () -> Unit) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        val reveal = rememberMochiThemeRevealState()
        CompositionLocalProvider(LocalThemeReveal provides reveal) {
            MochiThemeRevealHost(reveal, content = content)
        }
    } else {
        content()
    }
}

/**
 * 切换明暗时调用：MD 分支走 mochi 揭开（深浅真变了才揭，没变直接改），
 * MIUIX 分支直接执行 [change]（HyperOS 观感零变化）。
 *
 * 非 @Composable，可在点击回调里直接调；state 由调用方在 composable 顶部读
 * [LocalThemeReveal.current] 后传入。revealIfShadeChanges 是定义在可空接收者上的扩展，
 * state 为 null（MIUIX / 未接 Host）时内部直接执行 change。
 *
 * @param reveal 主题揭开状态（MD 根部注入；MIUIX 为 null）
 * @param fromDark 切换前的明暗（用于判断深浅是否真变了）
 * @param toDark 切换后的明暗
 * @param change 真正的主题改动（写 state + 落盘），由 mochi 在拍屏后执行
 */
fun sesameRevealThemeChange(
    reveal: MochiThemeRevealState?,
    fromDark: Boolean,
    toDark: Boolean,
    change: () -> Unit
) {
    reveal.revealIfShadeChanges(fromDark, toDark, change = change)
}

/* ───────────────────────── 页面导航（单 Activity 导航栈，仅 MD）─────────────────────────
 * 方案 A（彻底 mochi 风格）的双轨过渡：
 * - MATERIAL3：用 mochi 的 MochiNavHost 承载页面栈，支持卡片一镜到底 / 侧推 / 共享顶栏 / 返回手势。
 * - MIUIX：不挂 NavHost， SesameNavHost 直出 content —— 二级页仍走原 startActivity，
 *   观感与交互零变化（方案 A 全部页面迁移完、老板确认后才统一删 MIUIX）。
 *
 * 用法（MD 根页面）：
 *   SesameNavHost { route -> when (route) { "main" -> ...; "about" -> ... } }
 * 子页里拿导航器：val nav = LocalSesameNav.current；返回用 SesameBackButton()，
 * 打开子页用 nav?.push(route)（MIUIX 下 nav 为 null，调用方回退 startActivity）。
 */

/** mochi 导航器（MD 根部注入；MIUIX 为 null → 调用方走原 Activity 路径）。 */
val LocalSesameNav = staticCompositionLocalOf<ui.feice.mochi.navigation.MochiNavigator?> { null }

/**
 * 页面导航宿主：MD 分支按官方骨架建导航器 + MochiNavHost；MIUIX 直出、零变化。
 *
 * @param root 根路由（主界面），必须是 [content] 分派里的一支。
 * @param content 路由到页面的分派，签名与官方 MochiNavHost 一致。
 */
@Composable
fun SesameNavHost(
    root: String,
    modifier: Modifier = Modifier,
    content: @Composable (route: String) -> Unit
) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        val nav = rememberMochiNavigator(root)
        CompositionLocalProvider(LocalSesameNav provides nav) {
            ui.feice.mochi.navigation.MochiNavHost(nav, modifier, content = content)
        }
    } else {
        content(root)
    }
}

/**
 * 导航返回按钮：MD 分支用 mochi 的 MochiBackButton（pop，第一页交系统返回）；
 * MIUIX 分支直出 [fallback]（调用方给原 Activity 的返回按钮，观感零变化）。
 */
@Composable
fun SesameBackButton(
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {}
) {
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        ui.feice.mochi.navigation.MochiBackButton(modifier = modifier)
    } else {
        fallback()
    }
}

/**
 * 滚动惯性（fling）：MD 用 mochi 的 0.998 慢停曲线（接近静止才结束），
 * MIUIX 用 Foundation 默认——两者行为一致，保证 HyperOS 观感零变化。
 *
 * 用法：在原生 `verticalScroll` / `LazyColumn` 上写
 * `flingBehavior = sesameFlingBehavior()`。mochi 规范「自定义滚动容器显式传
 * flingBehavior」，这里按风格分派，避免在页面里写 if (style) 分支。
 */
@Composable
fun sesameFlingBehavior(): FlingBehavior =
    if (LocalUiStyle.current == UiStyle.MATERIAL3) {
        MochiScrollDefaults.flingBehavior()
    } else {
        ScrollableDefaults.flingBehavior()
    }

/**
 * 按压反馈（demo 手感的核心之一）：MD 风格按「按哪亮哪」的柔光 + 跟手形变，
 * MIUIX 直接返回原 Modifier——HyperOS 观感零变化。
 *
 * mochi 规范里 [mochiPressGlow] 与 [mochiPressDeform] 共用一套触摸节奏
 * （滚动容器里先等 90ms、手指没动才反馈，滚动接手就收回），两者都不消费触摸事件，
 * 可以和 `clickable` / `Surface(onClick)` 叠用；只改绘制图层，不重组、不重新布局。
 *
 * 用法：在可点的卡片/按钮 Modifier 链上（clickable 之前）加
 * `.then(sesamePressFeedback(shape))`。
 *
 * @param shape 高光/形变的裁剪形状，传卡片自己的圆角（G2 连续圆角由 [sesameShape] 给）
 * @param scale 按下时轻微缩小，0.96f 是 mochi 默认
 * @param follow 朝手指方向的跟手偏移，3.dp 是 mochi 默认
 */
@Composable
fun Modifier.sesamePressFeedback(
    shape: Shape,
    scale: Float = 0.96f,
    follow: Dp = 3.dp
): Modifier {
    if (LocalUiStyle.current != UiStyle.MATERIAL3) return this
    val glow = rememberMochiGlowState()
    return this
        .mochiPressGlow(glow, shape)
        .mochiPressDeform(scale = scale, follow = follow)
}
