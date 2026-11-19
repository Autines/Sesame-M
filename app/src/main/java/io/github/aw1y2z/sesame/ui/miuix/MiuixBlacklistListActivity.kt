package io.github.aw1y2z.sesame.ui.miuix

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.entity.AutoBlackTaskEntry
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameConfirmDialog
import io.github.aw1y2z.sesame.ui.theme.SesameEmptyState
import io.github.aw1y2z.sesame.ui.theme.SesameLazyCompactTitleScaffold
import io.github.aw1y2z.sesame.util.MessageUtil
import io.github.aw1y2z.sesame.util.ToastUtil

/**
 * 任务黑名单（二级）：某个**黑名单列表**（如「活力值黑名单」）里被自动拉黑的任务。
 *
 * 由一级页 [MiuixBlacklistActivity] 传入 fieldCode 进来 —— 层级与「扩展功能」页一致：
 * 一级是模块分组，二级是列表，这里是列表里的明细。点某一行即对该任务单独解禁。
 */
class MiuixBlacklistListActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_FIELD_CODE = "fieldCode"
        const val EXTRA_LIST_NAME = "listName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 同 MiuixBlacklistActivity：本页同样依赖「已灌入配置的字段表」来翻中文名 / 判断是否还在列表里。
        Model.initAllModelIfNeeded()
        ConfigPreload.prepareCurrentUser()
        val fieldCode = intent.getStringExtra(EXTRA_FIELD_CODE).orEmpty()
        val listName = intent.getStringExtra(EXTRA_LIST_NAME).orEmpty().ifEmpty { "黑名单" }
        setAppContent {
            ListScreen(fieldCode, listName, onBack = { finish() })
        }
    }
}

/**
 * 黑名单明细页内容（与外壳解耦，供旧 Activity 与 MD route 复用）。
 *
 * 列表数据用 fieldCode 作 key：route 每次 push 进来是全新组合，天然读到最新数据；
 * 旧 Activity 路径下从别处返回仍由外层 finish/重建兜底。页内解禁后会就地重读 entries。
 */
@Composable
fun ListScreen(
    fieldCode: String,
    listName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var entries by remember(fieldCode) {
        mutableStateOf(MessageUtil.listAutoBlackEntries().filter { it.fieldCode == fieldCode })
    }
    var pendingRelease by remember { mutableStateOf<AutoBlackTaskEntry?>(null) }
    var pendingReleaseAll by remember { mutableStateOf(false) }

    // 阈值与"今天是第几天"只在进入页面时取一次，保证整屏的倒计时口径一致
    val retryDays = remember { MessageUtil.getBlacklistRetryDays() }
    val confirmHits = remember { MessageUtil.getBlacklistConfirmHits() }
    val maxRetry = remember { MessageUtil.getBlacklistMaxRetry() }
    val today = remember { MessageUtil.currentDayIndex() }

    SesameLazyCompactTitleScaffold(
        title = listName,
        onBack = onBack
    ) {
        if (entries.isEmpty()) {
            item(key = "empty") {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SesameEmptyState(
                        text = "这个列表里没有被拉黑的任务",
                        hint = "解禁之后条目会消失；模块再次拉黑时会重新出现在这里"
                    )
                }
            }
        } else {
                item(key = "tasks") {
                    // 一张卡片装下整个列表（行间分隔线由库组件自动加），点某行即解禁该任务
                    SesameCardGroup {
                        entries.forEach { entry ->
                            SesameClickRow(
                                title = entry.taskName,
                                summary = statusText(entry, retryDays, confirmHits, maxRetry, today),
                                onClick = { pendingRelease = entry }
                            )
                        }
                    }
                }
                item(key = "action-card") {
                    SesameCardGroup(header = "操作") {
                        SesameClickRow(
                            title = "全部解禁",
                            summary = "本列表 ${entries.size} 项",
                            icon = Icons.Outlined.LockOpen,
                            onClick = { pendingReleaseAll = true }
                        )
                    }
                }
        }
    }

    pendingRelease?.let { entry ->
        SesameConfirmDialog(
            title = "解禁「${entry.taskName}」",
            text = "解禁后模块会重新尝试该任务。若它仍然无法自动完成，会被再次拉黑。",
            confirmText = "解禁",
            onConfirm = {
                pendingRelease = null
                val ok = MessageUtil.releaseAutoBlack(entry.key)
                ToastUtil.show(context, if (ok) "已解禁" else "解禁失败")
                entries = MessageUtil.listAutoBlackEntries().filter { it.fieldCode == fieldCode }
            },
            onDismiss = { pendingRelease = null }
        )
    }

    if (pendingReleaseAll) {
        SesameConfirmDialog(
            title = "全部解禁",
            text = "将解禁本列表的 ${entries.size} 项。无法自动完成的任务会被重新拉黑。",
            confirmText = "全部解禁",
            destructive = true,
            onConfirm = {
                pendingReleaseAll = false
                var released = 0
                entries.forEach { if (MessageUtil.releaseAutoBlack(it.key)) released++ }
                entries = MessageUtil.listAutoBlackEntries().filter { it.fieldCode == fieldCode }
                ToastUtil.show(context, "已解禁 $released 项")
            },
            onDismiss = { pendingReleaseAll = false }
        )
    }
}

/**
 * 一行状态文案：说明这个任务**为什么被停、停了多久、什么时候会被放出来**。
 *
 * 三种状态：
 * - 观察中：「观察中 1/3 次连续命中」——模糊错误要连续命中 3 天才真拉黑；
 * - 已拉黑：「已拉黑 2026-10-01，2 天后解禁，重试 0/3 次」；
 * - 永久：「已永久拉黑（重试 3 次仍失败）」。
 *
 * 用词刻意精简：「1 天后解禁」不写「自动」、重试次数不写「已解禁」前缀 ——
 * 语义没有损失（解禁本来就是模块自动做的），但每行能省 4 个字，避免折行。
 *
 * `inList == false` 时补一句「已不在黑名单列表」：这条只是历史记录，已经不影响任务了。
 * `inList == null` 表示字段表没拿到、无法判断，此时**什么都不补**，避免误报。
 */
private fun statusText(
    entry: AutoBlackTaskEntry,
    retryDays: Int,
    confirmHits: Int,
    maxRetry: Int,
    today: Long
): String {
    val gone = if (entry.inList == false) "（已不在黑名单列表）" else ""
    return when {
        entry.isPermanent -> "已永久拉黑（重试 $maxRetry 次仍失败）$gone"
        entry.isWatching -> "观察中 ${entry.hits}/$confirmHits 天连续命中"
        else -> {
            val remain = (retryDays - (today - entry.blackDay)).coerceAtLeast(0)
            val date = MessageUtil.formatBlackDay(entry.blackDay)
            val day = if (date.isEmpty()) "" else " $date"
            // 到期但还没轮到跨天检查时，别再说"0 天后解禁"，直接说清楚现在的处境
            val release = if (remain > 0) "$remain 天后解禁" else "已到期，下次运行解禁"
            "已拉黑$day，$release，重试 ${entry.retry}/$maxRetry 次$gone"
        }
    }
}
