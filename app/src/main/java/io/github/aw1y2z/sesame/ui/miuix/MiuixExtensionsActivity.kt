package io.github.aw1y2z.sesame.ui.miuix

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.TokenConfig
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameConfirmDialog
import io.github.aw1y2z.sesame.ui.theme.SesameCompactTitleScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameInputDialog
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.ui.HostBridge
import io.github.aw1y2z.sesame.util.ToastUtil

class MiuixExtensionsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            ExtensionsScreen(
                onBack = { finish() },
                onSend = { type, method, data -> sendItemsBroadcast(this, type, method, data) },
            )
        }
    }
}

/**
 * 扩展页的受限通道：这里只做「翻译」，白名单在宿主侧（forestQuery 7 个、walkPath 3 个 method），
 * 传什么都退不成任意 method + data 的旧 rpctest。
 *
 * 抽成顶层函数：MD 单 Activity 导航下 route 页（MiuixMainActivity）与旧 Activity 都调用它。
 */
fun sendItemsBroadcast(
    context: android.content.Context,
    type: String,
    method: String,
    data: String?
) {
    when (type) {
        "antForest" -> HostBridge.send(context, "forestQuery", null, method, data, false)
        "setCustomWalkPathIdList" ->
            HostBridge.send(context, "walkPath", null, "addCustomWalkPathId", data, false)
        "setCustomWalkPathIdQueue" ->
            HostBridge.send(context, "walkPath", null, method, data, false)
        else -> ToastUtil.show(context, "未知的扩展请求：$type")
    }
}

/** 扩展页内容（与外壳解耦，供旧 Activity 与 MD route 复用）。 */
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    onSend: (type: String, method: String, data: String?) -> Unit,
) {
    val context = LocalContext.current
    var showDishDialog by remember { mutableStateOf(false) }
    var inputMode by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }

    SesameCompactTitleScaffold(
        title = "扩展功能",
        onBack = onBack
    ) {
            SesameCardGroup(header = "森林查询") {
                val forestButtons = listOf(
                    "查询浇水列表" to ("antForest" to "getWateredItems"),
                    "查询被浇列表" to ("antForest" to "getWateringItems"),
                    "查询古树列表" to ("antForest" to "getTreeItems"),
                    "查询新古树" to ("antForest" to "getNewTreeItems"),
                    "查询区域古树" to ("antForest" to "queryAreaTrees"),
                    "查询可解锁古树" to ("antForest" to "getUnlockTreeItems"),
                    "填入浇水好友" to ("antForest" to "fillWateredFriendList")
                )
                forestButtons.forEach { (label, pair) ->
                    SesameClickRow(
                        title = label,
                        icon = Icons.Outlined.Search,
                        onClick = {
                            onSend(pair.first, pair.second, null)
                            ToastUtil.show(context, "已发送查询请求，请在森林日志查看结果！")
                        }
                    )
                }
            }
            SesameCardGroup(header = "自定义走路路径") {
                SesameClickRow(
                    title = "设置自定义走路路径(list)",
                    icon = Icons.Outlined.DirectionsWalk,
                    onClick = { inputMode = "list"; inputText = "" }
                )
                SesameClickRow(
                    title = "设置自定义走路路径(queue)",
                    icon = Icons.Outlined.DirectionsWalk,
                    onClick = { inputMode = "queue"; inputText = "" }
                )
            }
            SesameCardGroup(header = "其他") {
                SesameClickRow(
                    title = "清空光盘行动图片",
                    icon = Icons.Outlined.Image,
                    onClick = { showDishDialog = true }
                )
            }

            if (showDishDialog) {
                SesameConfirmDialog(
                    title = "清空光盘行动图片",
                    text = "确认清空 ${TokenConfig.getDishImageCount()} 组光盘行动图片？",
                    destructive = true,
                    onConfirm = {
                        showDishDialog = false
                        if (TokenConfig.clearDishImage()) {
                            ToastUtil.show(context, "光盘行动图片清空成功")
                        } else {
                            ToastUtil.show(context, "光盘行动图片清空失败")
                        }
                    },
                    onDismiss = { showDishDialog = false }
                )
            }

            if (inputMode != null) {
                val mode = inputMode
                SesameInputDialog(
                    title = if (mode == "list") "设置自定义走路路径(list)"
                    else "设置自定义走路路径(queue)",
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = "路径ID",
                    onDismiss = { inputMode = null },
                    secondaryAction = if (mode == "queue") {
                        "清空队列" to {
                            onSend(
                                "setCustomWalkPathIdQueue",
                                "clearCustomWalkPathIdQueue",
                                null
                            )
                            inputMode = null
                        }
                    } else null,
                    primaryAction = "添加" to {
                        val text = inputText.trim()
                        if (mode == "list") {
                            onSend("setCustomWalkPathIdList", "addCustomWalkPathId", text)
                        } else {
                            onSend("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", text)
                        }
                        inputMode = null
                    }
                )
            }
    }
}
