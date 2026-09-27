package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Text as MiuixText

// 悬浮玻璃 dock（液态玻璃底栏）：库 API 见 https://github.com/Kyant0/AndroidLiquidGlass
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/* ───────────────────────── 底部导航 / 顶部栏 ─────────────────────────
 * 本文件是「风格无关 UI 组件集」的一部分（整体约定与分层说明见 SesameFoundation.kt 顶部）。
 * 每个组件内部按 LocalUiStyle 分派到 Miuix / Material 3 两套实现，
 * 因此页面只依赖这些组件 —— 换肤不会触碰任何业务逻辑。
 */
/* ───────────────────────── 底部导航 ───────────────────────── */

/** 底部导航项 */
data class SesameNavItem(val icon: ImageVector, val label: String)

/**
 * 底部导航栏。
 *
 * @param transparent 背景是否透明。开启「底栏液态玻璃」时传 true：
 *   MD3 分支不再画 surfaceContainer 实色底，把外观交给外层的玻璃面（liquidGlass），
 *   否则实色底会把玻璃层整个盖住，玻璃效果不可见（曾因此被误判为「功能没生效」）。
 * @param compact 紧凑胶囊模式（液态玻璃 dock 用，参考 Legado 阅读底栏形态）：
 *   宽度包住图标组而不是通栏铺满，item 不用 weight 均分而用固定左右内边距。
 *   注意 wrap-content 容器里 weight 会把子项压成 0 宽，所以两种模式的 item 修饰符不同。
 */
@Composable
fun SesameNavBar(
    items: List<SesameNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    transparent: Boolean = false,
    compact: Boolean = false
) {
    // 紧凑模式 = 悬浮玻璃 dock 的内容层：**两套皮肤共用同一份渲染**。
    // 不能让 Miuix 走库的 NavigationBar —— 它自带不透明容器和一套自己的度量，
    // 会把外面的玻璃整个盖住（实测：玻璃面看不到任何模糊/折射，只剩一条通栏白底栏），
    // 高度/间距/选中态也都与 M3 分支对不上。
    // 玻璃才是这套观感的主角，内容层的容器必须让位。
    if (compact) {
        CompactNavBar(items = items, selected = selected, onSelect = onSelect)
        return
    }
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
                color = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(M3NavBarHeight),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = selected == index
                        // 选中=深色内容（参考 Legado：选中态只靠浅灰椭圆区分，不靠变色）
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
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
                                    // 紧凑模式指示器收窄为 48×32，让胶囊整体更贴近参考 dock 的紧凑比例
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

/**
 * 悬浮玻璃 dock 的内容层：紧凑胶囊排布（[SesameNavBar] 的 `compact = true` 专用）。
 *
 * **两套皮肤共用同一份渲染**。它是一个**自定义形态**（既不是 Miuix 的底栏，也不是 M3 的
 * NavigationBar），所以刻意不走任何库组件：
 * - 库组件都自带不透明容器，会把外面的玻璃整个盖住（Miuix 尤其明显 —— 实测玻璃看不出
 *   任何模糊/折射，屏幕上只是一条通栏白底栏）；
 * - 颜色一律取 `sesame*` 语义色，所以换肤时自动跟随，不需要为两套皮肤各写一遍。
 *
 * 度量（与 M3 分支的历史值一致，保证玻璃开关前后观感不跳）：
 * 行高 56dp / 相邻 tab 只留 1px / 图标指示器 44×32 / 选中态用浅灰椭圆包住「图标 + 文字」。
 */
@Composable
private fun CompactNavBar(
    items: List<SesameNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val selectedContent = sesameOnSurface()
    val idleContent = sesameOnSurfaceVariant()
    Row(
        // ⚠️ 不加 fillMaxWidth：胶囊宽度只包住内容（参考 dock 的紧凑比例）
        modifier = Modifier.height(56.dp),
        // 相邻 tab 之间只留 1 像素。用 Row 的 spacing 统一提供，**不要**改成 item 左右内边距，
        // 否则两侧各算一份、实际间距是内边距的两倍。
        horizontalArrangement = Arrangement.spacedBy(with(LocalDensity.current) { 1.toDp() }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selected == index
            val contentColor = if (isSelected) selectedContent else idleContent
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) }
                    )
                    // 选中态：浅灰中性椭圆（不用 primary/secondaryContainer 的彩底，
                    // 避免在玻璃上压出一块彩色），底色由文字色 8% 叠出，明暗两套主题自动成立。
                    .background(
                        if (isSelected) selectedContent.copy(alpha = 0.08f) else Color.Transparent,
                        RoundedCornerShape(50)
                    )
                    // ⚠️ 内边距必须【恒定】——未选中也留同样的 padding，只是底色透明。
                    // 否则只有选中项变宽 → 整排 item 重新分配位置，切页时按钮会"挪一下"。
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = CompactNavIndicatorHeight)
                        .clip(RoundedCornerShape(CompactNavIndicatorHeight / 2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(M3NavBarIconSize)
                    )
                }
                Spacer(Modifier.height(CompactNavIconLabelGap))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    // 行高压到等于字号：labelMedium 的 lineHeight 比 fontSize 大，
                    // 文字**上方**会因此多出一截空白 —— 只缩 Spacer 的话这截一直存在，
                    // 看起来就是"间距压不下去"。压平行高后文字才真正贴住图标。
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

/* ───────────────────────── 悬浮玻璃 dock ───────────────────────── */

/**
 * 悬浮玻璃 dock：把「液态玻璃底栏」的**配方**收敛到设计系统里。
 *
 * 为什么放在这里而不是 Activity：整块内容都是**视觉设计决策** ——
 * 磨砂强度、折射量、色散开关、表面白度、亮边宽度。改观感不该去翻 Activity。
 *
 * 与 [SesameNavBar] 是一对：调用方把 `compact = true, transparent = true` 的导航栏
 * 作为 [content] 传进来，由本组件负责它外面的玻璃面与滚动收放。
 *
 * @param backdrop 折射源句柄（由 `rememberLayerBackdrop()` 创建）。
 *   ⚠️ 同一个实例必须同时挂在**内容层**上（`Modifier.layerBackdrop(backdrop)`），
 *   否则采样不到任何东西 —— 玻璃面会退化成一层半透明白纱（换库前就是这样）。
 * @param offsetY 滚动驱动的纵向收放；收起时传一个大于 dock 高度的值让它完全出屏。
 * @param alpha 与 [offsetY] 配套的淡出（只改 offset 会有"半截露在屏外"的观感）。
 * @param content dock 里的内容，通常就是 [SesameNavBar]。
 */
@Composable
fun SesameGlassDock(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    offsetY: Dp = 0.dp,
    alpha: Float = 1f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 收/放只改**绘制**位置与透明度，不改变布局尺寸 ——
            // 内容区依旧是全屏铺满的折射源，不会因为 dock 收放而抖动。
            .offset(y = offsetY)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // 距屏底 20dp：悬浮感来自这段空隙，而不是靠阴影
                //（阴影落在玻璃身后会被采样进来、把整条染灰）
                .padding(bottom = 20.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    // 全圆角胶囊：紧凑、不通栏，只包住图标组
                    shape = { CircleShape },
                    // effects 的书写顺序即叠加顺序：先增艳 → 再模糊 → 最后折射
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(
                            refractionHeight = 14.dp.toPx(),
                            refractionAmount = 28.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    // 亮边：库自带的描边 + 斜向高光着色器（iOS 26 的"高光边"），
                    // 比自己画一圈均匀白描边更贴近参考观感。
                    // ⚠️ 不要再额外叠 Modifier.border，否则会双描边。
                    highlight = { Highlight(width = 1.dp, blurRadius = 0.5.dp) },
                    // ⚠️ 必须传 null：阴影画在玻璃**正后方**，会被 backdrop 一起采样进来，
                    // 把整条 dock 染灰（白底页面上尤其明显，早期用别的库时踩过）。
                    shadow = null,
                    // 真实模糊已经挡住了背后内容，这层白只需托一点文字可读性；
                    // 不像"模糊失效"时期那样要靠 0.78 的高不透明度硬盖。
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.30f)) }
                )
                // 玻璃面比导航栏内容再外扩一圈，胶囊才有呼吸感
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            content()
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

/* ───────────────────────── 缺口状态提示条 ───────────────────────── */

/**
 * 「缺少文件权限」提示条。
 *
 * **为什么需要它**：模块的配置与日志写在**宿主包名的 media 目录**
 * （`/sdcard/Android/media/com.eg.android.AlipayGphone/sesame-M/`），Android 11+ 访问该目录必须持有
 * 「所有文件访问权限」。而系统在**卸载重装后会清空这个权限、并且不会自动再询问一次** ——
 * 用户看到的现象却是「设置改了不保存、界面还回弹」，完全无从判断原因。
 * 所以在所有一级页的顶栏常驻一条提示，把「当前状态 + 后果 + 一键修复」放在首屏第一眼。
 *
 * **视觉**：走 Material 的 error-container 套路（淡色底 + 同色系图标与强调文字），
 * 而**不用实色红底** —— 权限缺失是"需要处理的状态"，不是需要立刻停止操作的报错，
 * 实色红会把整屏注意力都吃掉。淡底由 [sesameError] 取 10% alpha 得到，明暗两套主题自动成立。
 *
 * ⚠️ 本组件属于**顶栏区域**（要挡住从下方滚上来的内容），因此自带**不透明**底色；
 * 调用方不要再把它塞进半透明容器。
 *
 * @param onClick 点击整条时执行的动作（通常是跳系统设置页申请权限）。
 */
@Composable
fun SesamePermissionNotice(onClick: () -> Unit) {
    val accent = sesameError()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 与顶栏同色不透明底：本组件在 topBar 区域，必须把下方滚上来的内容挡住
            .background(sesameSurface())
            // 左右与顶栏的水平内边距对齐（16dp），保证和标题落在同一条左缘
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sesameCardShape())
                .background(accent.copy(alpha = 0.10f))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "缺少文件权限",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent
                )
                Text(
                    text = "设置与日志无法保存，点此开启",
                    fontSize = 12.sp,
                    color = sesameOnSurfaceVariant()
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "去开启",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = accent
            )
        }
    }
}
