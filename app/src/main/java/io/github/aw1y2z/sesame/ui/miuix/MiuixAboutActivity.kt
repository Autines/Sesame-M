package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.ui.theme.SesameIcons
import io.github.aw1y2z.sesame.ui.theme.sesameIconHere
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.Agreement
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameDetailTitleScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.sesamePrimary
import io.github.aw1y2z.sesame.util.ToastUtil

/** 关于应用二级页：图标 + 项目名 + 版本 + 链接/维护者信息，与其它二级页统一风格。 */
class MiuixAboutActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            AboutScreen(this)
        }
    }
}

@Composable
fun AboutScreen(activity: MiuixAboutActivity) {
    SesameDetailTitleScaffold(
        title = "关于",
        onBack = { activity.finish() }
    ) {
        // mochi 脚手架已自带 verticalScroll / 状态栏 / 16dp 水平留白 / 顶栏高度；
        // 这里只保留本页特有的居中对齐与顶部呼吸感（底栏留白由脚手架 contentBottomPadding 提供）。
        Column(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(sesamePrimary())
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.height(16.dp))
            SesameText(
                text = ViewAppInfo.getAppTitle(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            SesameText(
                text = "版本: ${ViewAppInfo.getAppVersion()}",
                fontSize = 13.sp,
                color = io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant()
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                SesameCardGroup {
                    SesameClickRow(
                        title = "在 GitHub 查看源码",
                        icon = sesameIconHere(SesameIcons.Code),
                        onClick = { openWebUrl(activity, "https://github.com/aw1y2z/Sesame-M") }
                    )
                    // 上游 v1.6.1：用户协议入口（同意时间只落在本机，这里回读展示）
                    SesameClickRow(
                        title = "用户协议",
                        // 两个事实各自一行，不用 "·" 粘在一起（mochi 规范明令禁止）
                        summary = "条款版本 ${Agreement.VERSION}\n" +
                                (Agreement.acceptedAtText()?.let { "同意于 $it" } ?: "未记录同意时间"),
                        icon = sesameIconHere(SesameIcons.Document),
                        onClick = { openWebUrl(activity, TERMS_URL) }
                    )
                }
            }
        }
    }
}

/** 条款文档地址：路径含中文，用百分号编码，避免个别浏览器或应用解析失败 */
private const val TERMS_URL =
    "https://github.com/aw1y2z/Sesame-M/blob/main/docs/%E5%90%88%E8%A7%84%E4%B8%8E%E5%85%8D%E8%B4%A3.md"

private fun openWebUrl(activity: MiuixBaseActivity, url: String) {
    try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        ToastUtil.show(activity, "无法打开链接")
    }
}
