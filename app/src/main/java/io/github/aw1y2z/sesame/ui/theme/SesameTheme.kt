package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 当前是否深色：先看是否跟随系统，再回落到模块的强制深色开关 */
@Composable
fun sesameIsDark(): Boolean {
    val followSystem = AppConfig.INSTANCE.followSystem != false
    return if (followSystem) isSystemInDarkTheme() else AppConfig.INSTANCE.darkMode == true
}

/**
 * App 统一主题入口。两套风格同时挂载：
 *
 * - [MiuixTheme] 始终生效，保证尚未迁移的页面（二级页等）在任何风格下都能正常取色；
 * - [MaterialTheme] 提供 M3 色板/字体/形状，供 [UiStyle.MATERIAL3] 下的 Sesame* 组件使用；
 * - 真实风格通过 [LocalUiStyle] 下发，页面据此选择对应实现。
 *
 * 因此风格可以逐页迁移，迁移期间不会出现取色失败或崩溃。
 */
@Composable
fun SesameTheme(
    style: UiStyle = UiStyle.current(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = sesameIsDark()
    val followSystem = AppConfig.INSTANCE.followSystem != false

    val controller = remember(dark) {
        ThemeController(
            colorSchemeMode = when {
                !followSystem -> if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                else -> ColorSchemeMode.System
            }
        )
    }
    val colorScheme = remember(context, dark) { SesameM3.colorScheme(context, dark) }

    CompositionLocalProvider(LocalUiStyle provides style) {
        MiuixTheme(controller) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = SesameM3.typography,
                shapes = SesameM3.shapes
            ) {
                content()
            }
        }
    }
}

/* ───────────────────────── 语义色 ─────────────────────────
 * 页面只使用下面这些「角色」而非具体色值，两套风格各自映射到自己的色板。
 * 这样同一份页面代码在 HyperOS 与 Material 3 下都能取到语义正确的颜色。
 */

/**
 * 页面背景。
 *
 * MD3 的 surface 是**等级化**的（`surfaceContainerLowest` … `surfaceContainerHighest`），
 * 而等级的方向随明暗翻转：浅色下等级越高越暗，深色下等级越高越亮。
 * 「卡片浮在页面底之上」这个关系要在两种明暗下都成立，就得按明暗分别挑角色——
 * 页面底永远取比卡片**更沉**的那一档：
 *
 * - 浅色：底 = `surfaceContainer`（带壁纸色调的浅灰），卡片 = 最亮的白；
 * - 深色：底 = `surfaceContainerLow`（接近最暗），卡片 = 抬高一级的灰。
 *
 * 早期实现把页面底取成 `background`（≈纯白）、卡片取成 `surfaceContainer`（偏灰），
 * 结果是「白底 + 灰卡」——关系正好反了，卡片看起来是凹陷的，而不是浮起的。
 */
@Composable
fun sesameSurface(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.surface
    UiStyle.MATERIAL3 -> if (sesameIsDark()) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
}

/**
 * 卡片 / 分组容器背景——页面底之上「浮起」的那一层。
 *
 * 与 [sesameSurface] 成对使用，两者必须来自**不同的等级档**，
 * 否则卡片和页面底同色，卡片就消失了。同样按明暗挑角色。
 *
 * 选档的理由：两档之间只差一档时，明度差约 9～10（实测浅色下
 * `surfaceContainer` → `surfaceContainerLowest` 为 #F3EDF7 → #FFFFFF）。
 * 这个差值刚好让卡片「浮起来但不刺眼」；直接跨两档会让对比翻倍，
 * 卡片虽然更白，整屏却会显得割裂。
 */
@Composable
fun sesameSurfaceContainer(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.surfaceContainer
    UiStyle.MATERIAL3 -> if (sesameIsDark()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
}

/**
 * 「已激活」正向状态的卡片配色：容器底色 + 内容色（文字/圆点/图标）。
 *
 * 为什么是**固定绿**而不是 `MaterialTheme.colorScheme.primary`：
 * MD3 主色跟随壁纸，换一张壁纸 primary 可能变成蓝、紫、橙 —— 「已激活」这个
 * 「一切都好、可以放心出门」的语义就没了。绿色是跨壁纸都成立的通行色，
 * 也和 HyperOS 分支那张浅绿卡片保持同一观感。
 *
 * 明暗各一套：浅色用很浅的绿底配深绿字；深色不能沿用浅底（刺眼），
 * 改成深绿底配浅绿字，对比度与深色环境一致。
 */
data class SesameStatusColors(val container: Color, val content: Color)

@Composable
fun sesameActivatedColors(): SesameStatusColors = if (sesameIsDark()) {
    SesameStatusColors(container = Color(0xFF1F3A25), content = Color(0xFF8FD9A0))
} else {
    SesameStatusColors(container = Color(0xFFE6F4EA), content = Color(0xFF1E6B33))
}

/**
 * 卡片圆角半径。同 [sesameCardShape]，单独导出是因为「拼接式分组卡」需要裸半径
 * （见 `sesameGroupedShape`），而不是一个成品 Shape。
 */
val SESAME_CARD_CORNER: Dp = 20.dp

/**
 * 卡片圆角。
 *
 * 比 M3 形状阶梯里的 `large`（16dp）再大一档：同样的 16dp 会让卡片显得方正，
 * 圆润度是这套观感辨识度的一部分。只用于**卡片容器**，
 * 不动 [MaterialTheme.shapes] 阶梯本身（按钮、输入框等仍按 M3 规范取形状）。
 */
@Composable
fun sesameCardShape(): Shape = RoundedCornerShape(SESAME_CARD_CORNER)

/** 主要文字 */
@Composable
fun sesameOnSurface(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.onBackground
    UiStyle.MATERIAL3 -> MaterialTheme.colorScheme.onSurface
}

/** 次要文字 */
@Composable
fun sesameOnSurfaceVariant(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    UiStyle.MATERIAL3 -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 强调色 */
@Composable
fun sesamePrimary(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.primary
    UiStyle.MATERIAL3 -> MaterialTheme.colorScheme.primary
}

/** 错误色：越界输入之类需要立刻被看见的提示 */
@Composable
fun sesameError(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.error
    UiStyle.MATERIAL3 -> MaterialTheme.colorScheme.error
}

/**
 * 分组卡片内「非行元素」（如统计表格、说明文字）需要补的水平内边距。
 *
 * Miuix 的容器自身带 16dp 内边距，行组件不含内边距；M3 反过来——容器不带内边距，
 * 由每个行组件自持 16dp（这样水波纹可以铺满卡片宽度）。两种模型下「非行元素」需要
 * 补的边距因此不同，这里统一出口，页面无需关心差异。
 */
@Composable
fun sesameGroupHorizontalPadding(): Dp =
    if (LocalUiStyle.current == UiStyle.MATERIAL3) 16.dp else 0.dp
