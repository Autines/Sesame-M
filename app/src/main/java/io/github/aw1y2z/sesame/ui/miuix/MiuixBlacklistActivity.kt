package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.ui.theme.sesameFlingBehavior
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameConfirmDialog
import io.github.aw1y2z.sesame.ui.theme.SesameDetailScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameEmptyState
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.util.MessageUtil
import io.github.aw1y2z.sesame.util.ToastUtil

/**
 * 任务黑名单（一级）：按**模块**分组，每组列出该模块下的**黑名单列表**。
 *
 * 层级与「扩展功能」页一致：上面是分组小标题（模块），下面一张卡片里的每一行是一个黑名单列表
 * （活力值黑名单 / 抽抽乐黑名单…），点进去才是那个列表里的具体任务（见 [MiuixBlacklistListActivity]）。
 *
 * 这样层级天然收得住：一级页总共十来行，不需要折叠就能一屏看全；任务明细放到下一级按需查看。
 *
 * 数据来源只有 `AutoBlackListMap`（模块自己写的记录）；**用户在设置页手动加的黑名单不在这里**，
 * 那部分属于各模块的「黑名单列表」字段，两者互补。
 */
class MiuixBlacklistActivity : MiuixBaseActivity() {

    /**
     * 回到本页时重新读一次数据。
     *
     * 从二级页返回走的是 onResume（Activity 没有重建），而列表数据存在 `remember` 里 ——
     * 不刷新的话外层显示的还是进去之前的旧快照：**在明细里解禁了几条，返回后数量不变**。
     *
     * 用自增计数当 key 触发重读即可，不必 `recreate()`（那个会闪一下、还会丢失滚动位置）。
     */
    private val refreshTick = mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 本页挂在主界面上，而主界面只建注册表、**不执行 ConfigPreload**（那个只在设置页调）。
        // 但本页有两处依赖「已灌入配置的字段表」：
        //   ① 任务键 → 中文名（金豆这类按英文稳定键拉黑的列表，全靠它翻译）；
        //   ② 判断该键是否还在黑名单列表里。
        // 少了这一步：金豆整片显示英文键，而且每条都会被误标成「已不在黑名单列表」。
        // 做法与设置页一致，只把 initAllModel() 换成**非破坏性**的 initAllModelIfNeeded()
        // （initAllModel() 会重建注册表、把字段值清成默认值，基类注释里有详细说明）。
        // prepare() 只读磁盘（载入映射 + 灌配置），不写盘；主界面也没有 ConfigV2 的编辑入口，
        // 因此不会覆盖任何未落盘的改动。
        // 「当前账号」由 Java 侧的 prepareCurrentUser() 取 —— Kotlin 看不到 Lombok 生成的静态 getter。
        Model.initAllModelIfNeeded()
        ConfigPreload.prepareCurrentUser()
        setAppContent {
            BlacklistScreen(this, refreshTick.value)
        }
    }
}

@Composable
fun BlacklistScreen(activity: MiuixBlacklistActivity, refreshTick: Int) {
    val context = LocalContext.current

    // 列表数据是普通 Java 静态方法的快照（非 Compose state），所以要以 refreshTick 为 key 手动重读：
    // 每次 onResume 都会 +1，用来接住"从二级页解禁完返回"这类外部改动。
    var entries by remember(refreshTick) { mutableStateOf(MessageUtil.listAutoBlackEntries()) }
    var pendingReleaseAll by remember { mutableStateOf(false) }

    // 一级 = 模块；二级 = 该模块下的黑名单列表。两者都由 MessageUtil 侧排好序，
    // groupBy 保持插入顺序，所以这里不再需要自己排序。
    val grouped = remember(entries) { entries.groupBy { it.groupName } }

    SesameDetailScaffold(
        title = "任务黑名单",
        onBack = { activity.finish() }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                SesameEmptyState(
                    text = "暂无被自动拉黑的任务",
                    hint = "模块发现某个任务无法自动完成时会自动把它拉黑，届时会出现在这里"
                )
            }
        } else {
            // 与「扩展功能」页同一套构造：分组小标题在上，紧跟一张装多行的卡片。
            // 刻意**不加** spacedBy：分组之间的呼吸感由 SesameSectionTitle 自带的上下留白给。
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                flingBehavior = sesameFlingBehavior()
            ) {
                grouped.forEach { (groupName, group) ->
                    item(key = "group::$groupName") {
                        SesameSectionTitle(text = groupName)
                    }
                    item(key = "card::$groupName") {
                        SesameCardGroup {
                            // 同一模块下按黑名单列表再分一层：一行一个列表，点进去看明细
                            group.groupBy { it.listName }.forEach { (listName, listEntries) ->
                                SesameClickRow(
                                    title = "$listName（${listEntries.size}）",
                                    onClick = {
                                        context.startActivity(
                                            Intent(context, MiuixBlacklistListActivity::class.java).apply {
                                                putExtra(
                                                    MiuixBlacklistListActivity.EXTRA_FIELD_CODE,
                                                    listEntries.first().fieldCode
                                                )
                                                putExtra(
                                                    MiuixBlacklistListActivity.EXTRA_LIST_NAME,
                                                    listName
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                item(key = "action-title") {
                    SesameSectionTitle(text = "操作")
                }
                item(key = "action-card") {
                    // 不用顶栏的"清空"位：它渲染的是垃圾桶图标，语义是"删除"，套在"全部解禁"上会误导
                    SesameCardGroup {
                        SesameClickRow(
                            title = "全部解禁",
                            summary = "共 ${entries.size} 项",
                            icon = Icons.Outlined.LockOpen,
                            onClick = { pendingReleaseAll = true }
                        )
                    }
                }
            }
        }
    }

    if (pendingReleaseAll) {
        SesameConfirmDialog(
            title = "全部解禁",
            text = "将解禁当前 ${entries.size} 项被自动拉黑的任务。无法自动完成的任务会被重新拉黑。",
            confirmText = "全部解禁",
            destructive = true,
            onConfirm = {
                pendingReleaseAll = false
                val released = MessageUtil.releaseAllAutoBlack()
                entries = MessageUtil.listAutoBlackEntries()
                ToastUtil.show(context, "已解禁 $released 项")
            },
            onDismiss = { pendingReleaseAll = false }
        )
    }
}
