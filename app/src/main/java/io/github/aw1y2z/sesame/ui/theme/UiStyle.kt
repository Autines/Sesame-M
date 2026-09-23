package io.github.aw1y2z.sesame.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aw1y2z.sesame.data.AppConfig

/**
 * 界面风格。App 内置两套设计语言，由「设置 → 界面风格」切换：
 *
 * - [MIUIX]     HyperOS / Miuix 观感，保持模块历史样式，选中时不改变任何既有视觉；
 * - [MATERIAL3] Material Design 3（Material You），Android 12+ 跟随壁纸动态取色，
 *               低版本回落到以品牌色 #216EEE 为种子生成的 M3 静态色板。
 *
 * 风格只影响「外观」，不涉及任何业务逻辑：所有页面通过 ui.theme 下的 Sesame* 组件
 * 与 sesame*() 语义色访问，因此同一份页面代码在两套风格下都成立。
 */
enum class UiStyle(
    val code: String,
    val label: String,
    val summary: String
) {
    MIUIX(
        code = AppConfig.UI_STYLE_MIUIX,
        label = "HyperOS 风格",
        summary = "小米澎湃 OS 观感，保持模块原有界面"
    ),
    MATERIAL3(
        code = AppConfig.UI_STYLE_MATERIAL3,
        label = "Material Design 3",
        summary = "Material You 设计语言，Android 12+ 跟随壁纸取色"
    );

    companion object {
        /** 缺省风格：保持历史观感，旧配置升级后视觉不变 */
        val DEFAULT = MIUIX

        fun fromCode(code: String?): UiStyle = entries.firstOrNull { it.code == code } ?: DEFAULT

        /** 当前生效的风格（读取 AppConfig；风格切换后由 Activity recreate 生效） */
        fun current(): UiStyle = fromCode(AppConfig.INSTANCE.uiStyle)
    }
}

/** 当前组合树内的界面风格，由 [SesameTheme] 注入 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.DEFAULT }
