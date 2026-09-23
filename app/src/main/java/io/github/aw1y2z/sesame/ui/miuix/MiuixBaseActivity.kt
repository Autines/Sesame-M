package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.theme.SesameTheme
import io.github.aw1y2z.sesame.util.LanguageUtil

open class MiuixBaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewAppInfo.init(applicationContext)
        // ⚠️ 这里绝不能再调 Model.initAllModel()。
        // initAllModel() 会先 destroyAllModel() 把整张注册表清掉，再重新 new 出每个 Model，
        // 字段值随之回到默认值；只有 MiuixSettingsActivity 紧接着调 ConfigPreload.prepare()
        // 才会把用户已保存的配置重新灌回去。
        // 放在基类 = 每个页面一打开都会重置一遍，于是
        // MiuixGroupFieldsActivity / MiuixSelectionEditActivity 这类「依赖上一个页面已加载的
        // 注册表、自己不做加载」的页面会拿到全默认值，保存后把用户配置覆盖掉。
        // 需要注册表的只读页面请改用 Model.initAllModelIfNeeded()（非破坏性）。
        setupSystemBars()
    }

    /**
     * 所有页面统一用此方法设置内容：
     * - 深色模式跟随 AppConfig；
     * - 界面风格（HyperOS / Material 3）跟随 AppConfig，由 SesameTheme 下发。
     */
    protected fun setAppContent(content: @Composable () -> Unit) {
        setContent {
            SesameTheme {
                content()
            }
        }
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val isDark = if (AppConfig.INSTANCE.followSystem != false) {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            AppConfig.INSTANCE.darkMode == true
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}
