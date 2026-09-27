package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference as MiuixCheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference as MiuixRadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ───────────────────────── 输入控件 / Chip / 对话框 ─────────────────────────
 * 本文件是「风格无关 UI 组件集」的一部分（整体约定与分层说明见 SesameFoundation.kt 顶部）。
 * 每个组件内部按 LocalUiStyle 分派到 Miuix / Material 3 两套实现，
 * 因此页面只依赖这些组件 —— 换肤不会触碰任何业务逻辑。
 */
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

/* ───────────────────────── 对话框 ───────────────────────── */

/**
 * 对话框动作按钮的**语义角色**。三个对话框（确认 / 输入 / 双动作）原本各自手写按钮，
 * M3 分支里 `TextButton { Text(...) }` 的配色与字重重复了 4 遍 —— 这里收敛成一处，
 * 顺便把「哪种动作该用什么色」变成显式角色，而不是散在每个对话框里各自判断。
 */
private enum class DialogActionRole {
    /** 主操作：主色 + 中等字重（如「确定」「保存」）。 */
    Primary,

    /** 次操作：弱化文字色，**不显式给字重** —— 沿用 TextButton 自带 labelLarge 的字重。 */
    Neutral,

    /** 破坏性主操作：错误色（如「删除」）。 */
    Destructive
}

/**
 * 对话框里的文字按钮。按 [role] 分派配色/字重，两套风格各走自己的实现。
 *
 * MIUIX 分支不区分角色（Miuix 的对话框按钮本来就只有一种形态，改造前就是如此），
 * 以免为了「统一」反而改动 HyperOS 侧的观感。
 */
@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    role: DialogActionRole = DialogActionRole.Primary
) {
    when (LocalUiStyle.current) {
        UiStyle.MIUIX -> MiuixTextButton(text = text, onClick = onClick)

        UiStyle.MATERIAL3 -> TextButton(onClick = onClick) {
            Text(
                text = text,
                color = when (role) {
                    DialogActionRole.Primary -> MaterialTheme.colorScheme.primary
                    DialogActionRole.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                    DialogActionRole.Destructive -> MaterialTheme.colorScheme.error
                },
                // 中性项传 null = 继承 TextButton 的默认字重，与改造前逐像素一致；
                // 强调项才显式给 Medium。
                fontWeight = if (role == DialogActionRole.Neutral) null else FontWeight.Medium
            )
        }
    }
}

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
                        DialogActionButton(
                            text = dismissText,
                            onClick = onDismiss,
                            role = DialogActionRole.Neutral
                        )
                        Spacer(Modifier.width(8.dp))
                        DialogActionButton(text = confirmText, onClick = onConfirm)
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
                DialogActionButton(
                    text = confirmText,
                    onClick = onConfirm,
                    role = if (destructive) DialogActionRole.Destructive else DialogActionRole.Primary
                )
            },
            dismissButton = {
                DialogActionButton(
                    text = dismissText,
                    onClick = onDismiss,
                    role = DialogActionRole.Neutral
                )
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
                            DialogActionButton(
                                text = secondaryAction.first,
                                onClick = secondaryAction.second,
                                role = DialogActionRole.Neutral
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        DialogActionButton(text = primaryAction.first, onClick = primaryAction.second)
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
                        DialogActionButton(
                            text = secondaryAction.first,
                            onClick = secondaryAction.second,
                            role = DialogActionRole.Neutral
                        )
                    }
                    DialogActionButton(text = primaryAction.first, onClick = primaryAction.second)
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
