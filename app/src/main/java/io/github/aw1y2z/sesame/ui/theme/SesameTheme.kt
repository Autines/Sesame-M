package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import ui.feice.mochi.theme.MochiTheme

/**
 * 主题模式的**可观察快照**：「跟随系统」与「固定浅/深」合成**一个** state。
 *
 * 为什么必须合成为一个 state（而不是分成两个独立的值）：
 * `sesameIsDark()` 里一旦写成 `if (followSystem) return ...` 这种**提前返回**，
 * 「跟随系统」这条分支就**不会读到**另一个 state → Compose 不会把该作用域注册为它的观察者
 * → 用户从「跟随系统」切到「固定深色」时**不触发重组 → 设置完全无效**。
 * （2026-10-03 实测踩到：玻璃开关修好了，主题样式却"设置后无效"，就是这个原因。）
 *
 * 合成一个 state 后，读取方**无条件读它**即可，永远有观察者。
 *
 * ⚠️ 另外：「跟随系统」时**不能**把深色值固化下来 —— 必须实时读 [isSystemInDarkTheme]，
 * 否则用户切了系统深色，App 会停在切换前的值上不再跟随。
 */
private data class SesameThemeMode(val followSystem: Boolean, val darkMode: Boolean)

private var SesameThemeModeState by mutableStateOf(
    SesameThemeMode(
        followSystem = AppConfig.INSTANCE.followSystem != false,
        darkMode = AppConfig.INSTANCE.darkMode == true
    )
)

/**
 * 设置页切明暗时调用：落盘 AppConfig + 更新可观察快照（不再需要 recreate）
 */
fun sesameApplyTheme(followSystem: Boolean, darkMode: Boolean) {
    AppConfig.INSTANCE.followSystem = followSystem
    AppConfig.INSTANCE.darkMode = darkMode
    SesameThemeModeState = SesameThemeMode(followSystem, darkMode)
}

/**
 * 从 AppConfig **重新同步**主题快照。
 *
 * 用于「配置被外部整体替换」的场景 —— 典型是**恢复完整备份**：
 * `performRestore` 里调 `AppConfig.load()` 把配置整份换掉，此时主题快照还是旧值，
 * 必须重新同步，否则界面主题与配置不符（快照是进程级 state，不会自己重读）。
 *
 * 放在 [io.github.aw1y2z.sesame.ui.miuix.MiuixBaseActivity.onCreate] 里调用：
 * 任何 Activity 重建（含恢复备份后的 recreate）都会经过那里，覆盖最全。
 * 值相同时 `mutableStateOf` 不会触发重组，重复调用无副作用。
 */
fun sesameSyncThemeFromConfig() {
    SesameThemeModeState = SesameThemeMode(
        followSystem = AppConfig.INSTANCE.followSystem != false,
        darkMode = AppConfig.INSTANCE.darkMode == true
    )
}

/**
 * 当前是否深色。
 *
 * - 跟随系统 → **实时**读 [isSystemInDarkTheme]（系统切深色 App 立刻跟随）；
 * - 固定浅/深 → 用快照里的值，切换时重组生效、不重建 Activity。
 *
 * ⚠️ 必须**先无条件读出快照**再分支，别写成 `if (followSystem) return ...` 提前返回 ——
 * 那样"跟随系统"时不会读到 state，之后切到固定模式就不触发重组（设置无效）。
 */
@Composable
fun sesameIsDark(): Boolean {
    val mode = SesameThemeModeState
    return if (mode.followSystem) isSystemInDarkTheme() else mode.darkMode
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
    // 一次读出主题模式快照，dark 与 followSystem 都从它派生 ——
    // 保证两者**同源**，不会出现"dark 已变、followSystem 还是旧值"的中间态。
    val themeMode = SesameThemeModeState
    val followSystem = themeMode.followSystem
    val dark = if (followSystem) isSystemInDarkTheme() else themeMode.darkMode

    // ⚠️ key 必须同时含 followSystem：否则「固定深色(dark=true)」切到「跟随系统且系统也是深色
    // (dark 仍为 true)」时，dark 没变 → controller 不重建 → colorSchemeMode 停在 Dark，
    // 用户以为切了"跟随系统"却还是被钉在深色。
    val controller = remember(dark, followSystem) {
        ThemeController(
            colorSchemeMode = when {
                !followSystem -> if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light
                else -> ColorSchemeMode.System
            }
        )
    }
    val colorScheme = remember(context, dark) { SesameM3.colorScheme(context, dark) }

    CompositionLocalProvider(LocalUiStyle provides style) {
        // 滚动回弹 Provider（规范 Required baseline）：MD 风格用 mochi 的 Cresto 回弹，
        // 包在根部后子树所有原生 verticalScroll/LazyColumn 自动生效；MIUIX 直出零变化。
        SesameOverscroll {
        MiuixTheme(controller) {
            // ── Material 3 Expressive ───────────────────────────────────────────────
            // 1.4.0 时 Expressive API（`MaterialExpressiveTheme` / `MotionScheme`）全部是库
            // internal，外部调不到；升到 1.5.0-alpha22 后已对外开放，这里正式接入。
            //
            // 显式传入项目自己的色板 / 形状 / 字体，**保持现有观感不变**；真正切换的是
            // **动效方案**：`MotionScheme.expressive()` 会把全部 M3 组件（开关、勾选、滑块、
            // 对话框、Chip…）的默认动画换成 Expressive 的弹性曲线。
            //
            // ⚠️ 库层面只开放到这一步：1.5.0-alpha22 里 **没有** ExpressiveShapes /
            // ExpressiveTypography（形状与字体阶梯仍是老的 4/8/12/16/28）。形状与色板的
            // Expressive 观感需要在项目层自定义（见 SesameM3.shapes / SesameColor.kt）。
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                motionScheme = MotionScheme.expressive(),
                shapes = SesameM3.shapes,
                typography = SesameM3.typography
            ) {
                // ── Mochi Compose（仅 MD 风格）──────────────────────────────────────
                // 按 mochi 的接入规范只在**根部**挂一次主题，页面里不写外观分支；
                // 这里用项目已有的 M3 色板桥接（MochiBridge），不重新取色，
                // 因此 mochi 只接管形状 / 字阶 / 动效 / 组件，动态取色与层次保持不变。
                //
                // systemBarAppearance = false：系统栏图标深浅仍由项目自己的 Activity 控制，
                // 避免两套主题同时写窗口导致来回抖动。
                // MIUIX 分支完全不挂 MochiTheme —— HyperOS 观感一行不动。
                if (style == UiStyle.MATERIAL3) {
                    MochiTheme(
                        colors = remember(colorScheme, dark) {
                            sesameMochiColorScheme(colorScheme, dark)
                        },
                        systemBarAppearance = false,
                        content = content
                    )
                } else {
                    content()
                }
            }
        }
        } // SesameOverscroll
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
        // 浅色页面底：Expressive 化后取 surfaceContainerLow。
        // 为什么不是更高的档：卡片已取 surfaceContainerHigh（#E6E8F1，彩度 28.2%），
        // 若底也用 container/High 两者会太接近 —— 实测两色亮度差只有 5.3，
        // 卡片"浮起"的立体感几乎没了。改回 Low(#F0F1F8) 后亮度差回到 11 左右，
        // 兼顾"有色调"与"看得出层次"。这是本轮实测调出来的唯一平衡点。
        MaterialTheme.colorScheme.surfaceContainerLow
    }
}

/**
 * 卡片 / 分组容器背景——页面底之上「浮起」的那一层。
 *
 * 与 [sesameSurface] 成对使用，两者必须来自**不同的等级档**，
 * 否则卡片和页面底同色，卡片就消失了。同样按明暗挑角色。
 *
 * 选档的理由（2026-10-02 实机重新量过）：
 * 改前浅色下卡片取 `surfaceContainerLowest`，实测纯白 `#FFFFFF`、**彩度 0%**，
 * 而它占了整屏 **65.1%** —— 三分之二画面是一块死白，这就是"看着寡淡"的量化原因。
 * Expressive 化后取 **`surfaceContainerHigh`**：实测 #ECEDF6 彩度 **35.7%**（淡蓝紫），
 * 与页面底 #F3F3FA 形成"浅而有色 / 深而近白"的层次，卡片既浮起又带颜色。
 *
 * 为什么不用 `surface`（更白、彩度更低）：那正是改前的问题本身，等于没改。
 * 为什么不再往上跨到 Highest：实测会让卡片与页面底的明度差过大、整屏发"花"。
 *
 * 深色分支保持原样：深色下 surface 阶梯本身已带蓝紫调，再动会显脏。
 */
@Composable
fun sesameSurfaceContainer(): Color = when (LocalUiStyle.current) {
    UiStyle.MIUIX -> MiuixTheme.colorScheme.surfaceContainer
    // 明暗同档：这一组里 High 是唯一"带明确色调"的一档，深色下它本就是
    // 「比 surface 亮一级」的正确语义，浅色下则是实测彩度 35.7% 的淡蓝紫。
    UiStyle.MATERIAL3 -> MaterialTheme.colorScheme.surfaceContainerHigh
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
 * HyperOS 分支的卡片圆角半径（20dp）。
 *
 * ⚠️ MD 风格接入 mochi 后走 [MochiBridge.CARD_CORNER]（28dp）；
 * 这个常量保留给 `sesameGroupedShape` 拼接式分组卡与 MIUIX 分支，别直接改它。
 */
val SESAME_CARD_CORNER: Dp = 20.dp

/**
 * 卡片圆角。
 *
 * - `MATERIAL3`：28dp **G2 连续曲率**（mochi `extraLarge`），见 [sesameShape]；
 * - `MIUIX`：保持原有的 20dp 普通圆角，视觉零变化。
 *
 * 只用于**卡片容器**，不动 [MaterialTheme.shapes] 阶梯本身。
 */
@Composable
fun sesameCardShape(): Shape = sesameShape(
    if (LocalUiStyle.current == UiStyle.MATERIAL3) MochiBridge.CARD_CORNER else SESAME_CARD_CORNER
)

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
