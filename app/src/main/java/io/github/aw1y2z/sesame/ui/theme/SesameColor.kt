package io.github.aw1y2z.sesame.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 色彩 / 字体 / 形状，仅用于 [UiStyle.MATERIAL3]。
 *
 * 取色策略：**动态取色优先，静态色板兜底**
 * - Android 12（API 31）及以上：读取系统壁纸生成的 Material You 色板；
 * - 更低版本：使用下方静态色板——以模块品牌色 #216EEE 为种子，
 *   用 **HCT（CAM16 色相/彩度 + CIELAB L*）** 按 M3 的额定色调与彩度推导，
 *   生成脚本见 `tools/m3_palette.py`（可用 `--check` 以官方基线种子 #6750A4 自校验）。
 *
 * ⚠️ 关键点：**secondaryContainer 必须明显区别于 primaryContainer**。
 * 两者色调都是 90，但额定彩度一个是 16、一个是 48，肉眼才能分出
 * 「中性承托色」与「品牌强调色」。若给成同一个值（早期版本都是 #DFE0FD），
 * 依赖 secondaryContainer 的组件——filter chip 选中态、底部导航选中指示器——
 * 就会和 primaryContainer 撞色，看起来像「换了角色却没变化」。
 */
object SesameM3 {

    /** 品牌种子色：现有主色，用于低版本静态色板 */
    const val SEED = 0xFF216EEE

    /** M3 默认形状阶梯（4 / 8 / 12 / 16 / 28） */
    val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    /**
     * M3 默认字体阶梯。保持 Roboto / 系统无衬线，中文由系统字体回退覆盖，
     * 不额外打包字体以控制包体积。
     */
    val typography: Typography = Typography()

    /** 静态兜底色板（浅色）—— 由 tools/m3_palette.py 以种子 #216EEE 生成 */
    private val FallbackLight: ColorScheme = lightColorScheme(
        primary = Color(0xFF375CA9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001165),
        inversePrimary = Color(0xFFB0C6FF),
        secondary = Color(0xFF575E71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE2F9),
        onSecondaryContainer = Color(0xFF141B2C),
        tertiary = Color(0xFF725572),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFDD7FA),
        onTertiaryContainer = Color(0xFF2A122C),
        error = Color(0xFFB42722),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD5),
        onErrorContainer = Color(0xFF4C0000),
        background = Color(0xFFFCFCFC),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFFCFCFC),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE2E2E2),
        onSurfaceVariant = Color(0xFF46464A),
        surfaceTint = Color(0xFF375CA9),
        surfaceDim = Color(0xFFDBD9DD),
        surfaceBright = Color(0xFFF9F9F9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F3F3),
        surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE8E8E8),
        surfaceContainerHighest = Color(0xFFE2E2E2),
        outline = Color(0xFF757780),
        outlineVariant = Color(0xFFC5C6D0),
        inverseSurface = Color(0xFF1B1B1F),
        inverseOnSurface = Color(0xFFF1F1F1),
        scrim = Color(0xFF000000)
    )

    /** 静态兜底色板（深色）—— 由 tools/m3_palette.py 以种子 #216EEE 生成 */
    private val FallbackDark: ColorScheme = darkColorScheme(
        primary = Color(0xFFB0C6FF),
        onPrimary = Color(0xFF002B78),
        primaryContainer = Color(0xFF1A438F),
        onPrimaryContainer = Color(0xFFD9E2FF),
        inversePrimary = Color(0xFF375CA9),
        secondary = Color(0xFFC0C6DC),
        onSecondary = Color(0xFF293042),
        secondaryContainer = Color(0xFF404659),
        onSecondaryContainer = Color(0xFFDCE2F9),
        tertiary = Color(0xFFE0BBDD),
        onTertiary = Color(0xFF412742),
        tertiaryContainer = Color(0xFF593D59),
        onTertiaryContainer = Color(0xFFFDD7FA),
        error = Color(0xFFFFB3AA),
        onError = Color(0xFF6F0000),
        errorContainer = Color(0xFF91070D),
        onErrorContainer = Color(0xFFFFDAD5),
        background = Color(0xFF1B1B1F),
        onBackground = Color(0xFFE2E2E2),
        surface = Color(0xFF1B1B1F),
        onSurface = Color(0xFFE2E2E2),
        surfaceVariant = Color(0xFF46464A),
        onSurfaceVariant = Color(0xFFC7C6CA),
        surfaceTint = Color(0xFFB0C6FF),
        surfaceDim = Color(0xFF000001),
        surfaceBright = Color(0xFF39393C),
        surfaceContainerLowest = Color(0xFF000001),
        surfaceContainerLow = Color(0xFF1B1B1F),
        surfaceContainer = Color(0xFF1F1F23),
        surfaceContainerHigh = Color(0xFF292A2D),
        surfaceContainerHighest = Color(0xFF343438),
        outline = Color(0xFF8F9099),
        outlineVariant = Color(0xFF44464F),
        inverseSurface = Color(0xFFE2E2E2),
        inverseOnSurface = Color(0xFF303034),
        scrim = Color(0xFF000000)
    )

    /**
     * 取得当前应使用的 M3 色板：API 31+ 动态取色，否则静态兜底。
     * 明暗由模块自身设置（跟随系统 / 强制深色）决定，与系统深色开关解耦。
     */
    fun colorScheme(context: Context, dark: Boolean): ColorScheme {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        return if (dark) FallbackDark else FallbackLight
    }
}
