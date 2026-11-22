package io.github.aw1y2z.sesame.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.rememberMochiIonicon

/**
 * Sesame 的图标统一入口 —— 全部走 mochi 官方的 Ionicons 图标集。
 *
 * ## 为什么要有这一层
 *
 * mochi 官方规范（`llms.txt` / `docs/guide/design-principles.md`）明确要求：
 * 「图标一律用 `MochiIonicon`（Ionicons 全集），**不要用 Material Icons**、
 * 手绘路径或 emoji 当图标」。
 *
 * 但本项目的图标从建项目起就是「`ImageVector` 作为参数层层传递」的架构
 * （`SesameClickRow(icon = …)`、`SesameNavItem(icon = …)`、`BarAction(icon = …)`、
 * `MAIN_TABS` 等十几处）。若把 `MochiIonicon` 直接塞进这些签名，会把 composable
 * 当数据传，牵动十几条调用链。
 *
 * 这里取**改动最小**的一条路：mochi-icons 的
 * [ui.feice.mochi.icons.MochiIonicons.vector] 是**普通函数**（不是 composable），
 * 按名字直接产出标准 `ImageVector`。于是「图标来源」从 Material Icons 换成
 * Ionicons，而**所有以 `ImageVector` 传参的接口一字不动**。
 *
 * ## 怎么用
 *
 * 页面里不再写 `Icons.Outlined.Foo`，改写：
 *
 * ```kotlin
 * SesameClickRow(title = "成员", icon = sesameIconOf(context, SesameIcons.People))
 * ```
 *
 * 描边 / 实心两种画法按场景选：
 * - 行首符号、工具栏按钮、未选中标签 → 描边（[MochiIconStyle.Outline]）
 * - 图标块、目录卡、需要强调的地方 → 实心（[MochiIconStyle.Filled]）
 */
object SesameIcons {
    /* ── 导航 ── */
    const val ArrowBack = "arrow-back"
    const val ChevronForward = "chevron-forward"
    const val ChevronDown = "chevron-down"
    const val Home = "home"
    const val Search = "search"
    const val Settings = "settings"
    const val Menu = "ellipsis-horizontal"

    /* ── 状态与动作 ── */
    const val Check = "checkmark"
    const val CheckCircle = "checkmark-circle"
    const val CheckAll = "checkmark-done"
    const val Close = "close"
    const val Trash = "trash"
    const val Warning = "warning"
    const val Info = "information-circle"
    const val Add = "add"

    /* ── 内容 ── */
    const val Document = "document"
    const val Code = "code"
    const val Terminal = "terminal"
    const val Image = "image"
    const val Folder = "folder-open"
    const val Share = "share"
    const val Upload = "cloud-upload"
    const val Archive = "archive"
    const val Refresh = "refresh"
    const val Sync = "sync"
    const val Options = "options"
    const val Play = "play"
    const val SwapHorizontal = "swap-horizontal"
    const val SwapVertical = "swap-vertical"

    /* ── 业务 ── */
    const val Account = "person-circle"
    const val People = "people"
    const val Chat = "chatbubble"
    const val Member = "id-card"
    const val Security = "shield"
    const val LockOpen = "lock-open"
    const val EyeOff = "eye-off"
    const val Ban = "ban"
    const val Bug = "bug"
    const val Sparkles = "sparkles"
    const val Palette = "color-palette"
    const val Moon = "moon"
    const val Extension = "extension-puzzle"
    const val Question = "help-circle"
    const val Wallet = "wallet"
    const val Cash = "cash"
    const val Leaf = "leaf"
    const val Nutrition = "nutrition"
    const val Fish = "fish"
    const val Flower = "flower"
    const val Trail = "trail-sign"
    const val Paw = "paw"
    const val Fitness = "fitness"
    const val Walk = "walk"
    const val Footsteps = "footsteps"
    const val Water = "water"
    const val Wifi = "wifi"
    const val BatteryHalf = "battery-half"
    const val BatteryCharging = "battery-charging"
    const val GitHub = "logo-github"
}

/**
 * 取一个 Ionicons 图标（`ImageVector`），供现有以 `ImageVector` 传参的组件使用。
 *
 * 非 composable —— 数据类字段（如 `MAIN_TABS`、`BarAction`）也能直接用。
 * 内部有 LRU 缓存（按 名字/画法/线宽/光学归一 缓存 512 个），
 * 同一个图标反复取不会重复解析路径。
 *
 * @param name Ionicons 基础名（见 [SesameIcons] 或 `MochiIonicons.catalog()`）
 * @param style 画法：描边 / 实心 / 直角
 */
fun sesameIconOf(
    context: Context,
    name: String,
    style: MochiIconStyle = MochiIconStyle.Outline
): ImageVector? = ui.feice.mochi.icons.MochiIonicons.vector(context, name, style)

/**
 * composable 版本的取图标：走 [rememberMochiIonicon]，会跟随
 * `LocalMochiSymbolWeight`（列表行标题字重变化时描边粗细跟着变）。
 *
 * 返回非空（解析失败给空白矢量兜底，理由同 [sesameIconHere]）。
 */
@Composable
fun sesameIcon(
    name: String,
    style: MochiIconStyle = MochiIconStyle.Outline
): ImageVector = rememberMochiIonicon(name, style)
    ?: ImageVector.Builder("blank", 24.dp, 24.dp, 24f, 24f).build()

/**
 * 当前 CompositionLocal 下的上下文版本 —— composable 里最常用。
 *
 * 返回**非空**矢量：miuix / material3 的 `Icon(imageVector = …)` 都要求非空参数，
 * 逐个调用点写 `?: 兜底` 会把噪声撒得到处都是；这里统一兜底，
 * 解析失败时给一张 24×24 的空白矢量（图标位留空而不是崩）。
 */
@Composable
fun sesameIconHere(
    name: String,
    style: MochiIconStyle = MochiIconStyle.Outline
): ImageVector {
    val context = LocalContext.current
    return remember(name, style, context) {
        sesameIconOf(context, name, style) ?: BLANK_ICON_VECTOR
    }
}

/**
 * 空矢量（24×24）：图标解析失败时的兜底 —— 图标位留空，绝不崩、也不画出错位的东西。
 */
private val BLANK_ICON_VECTOR: ImageVector =
    ImageVector.Builder("blank", 24.dp, 24.dp, 24f, 24f).build()
