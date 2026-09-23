package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.aw1y2z.sesame.ui.theme.SesameDetailScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameInputDialog
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.util.ToastUtil

class MiuixExtensionsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            ExtensionsScreen(this)
        }
    }

    fun sendItemsBroadcast(type: String, method: String, data: String?) {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
        intent.putExtra("type", type)
        intent.putExtra("method", method)
        intent.putExtra("data", data)
        sendBroadcast(intent)
    }
}

@Composable
fun ExtensionsScreen(activity: MiuixExtensionsActivity) {
    val context = LocalContext.current
    var showDishDialog by remember { mutableStateOf(false) }
    var inputMode by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }

    SesameDetailScaffold(
        title = "扩展功能",
        onBack = { activity.finish() }
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SesameSectionTitle(text = "森林查询")
            SesameCardGroup {
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
                            activity.sendItemsBroadcast(pair.first, pair.second, null)
                            ToastUtil.show(context, "已发送查询请求，请在森林日志查看结果！")
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            SesameSectionTitle(text = "自定义走路路径")
            SesameCardGroup {
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
            Spacer(Modifier.height(4.dp))

            SesameSectionTitle(text = "其他")
            SesameCardGroup {
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
                            activity.sendItemsBroadcast(
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
                            activity.sendItemsBroadcast("setCustomWalkPathIdList", "addCustomWalkPathId", text)
                        } else {
                            activity.sendItemsBroadcast("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", text)
                        }
                        inputMode = null
                    }
                )
            }
        }
    }
}
