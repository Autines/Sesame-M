package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.runtime.Composable
import io.github.aw1y2z.sesame.ui.theme.SesameConfirmDialog

/**
 * 确认对话框（兼容入口）。
 *
 * 实现已迁到风格无关组件集 [SesameConfirmDialog]：Miuix 风格下外观与本文件
 * 原有实现完全一致，Material 3 风格下自动切换为标准 AlertDialog。
 * 保留这个薄封装是为了不改动既有调用点。
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SesameConfirmDialog(
        title = title,
        text = text,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
