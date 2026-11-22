package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.ui.theme.SesameIcons
import io.github.aw1y2z.sesame.ui.theme.sesameIconOf

/**
 * 分组图标：按 [ModelGroup] 的编码取一个语义贴切的图标。
 *
 * M3 的列表靠前导图标承担「快速识别」——先认形状再读文字。
 * 分类记录、配置分组目录共用这套映射，保证同一个分组在哪个页面都是同一个符号。
 *
 * 图标来源是 mochi 官方的 Ionicons 全集（见 [SesameIcons]），不用 Material Icons。
 *
 * - 未知编码回落到一个中性符号，不返回 null（避免行内出现 24dp 空洞）；
 * - 「基础」组本身没有具象的业务语义，同样用中性符号，不与某个具体分组抢识别度。
 *
 * @param context 取 Ionicons 矢量用（图标资源在 assets 里，需要 context 读）
 */
internal fun groupIconOf(context: Context, code: String): ImageVector = when (code) {
    ModelGroup.FOREST.code -> sesameIconOf(context, SesameIcons.Leaf)
    ModelGroup.FARM.code -> sesameIconOf(context, SesameIcons.Nutrition)
    ModelGroup.STALL.code -> sesameIconOf(context, SesameIcons.Home)
    ModelGroup.ORCHARD.code -> sesameIconOf(context, SesameIcons.Flower)
    ModelGroup.GOLDENBEANS.code -> sesameIconOf(context, SesameIcons.Cash)
    ModelGroup.SPORTS.code -> sesameIconOf(context, SesameIcons.Fitness)
    ModelGroup.MEMBER.code -> sesameIconOf(context, SesameIcons.Member)
    ModelGroup.OTHER.code -> sesameIconOf(context, SesameIcons.Menu)
    // 2026-10-09：上游 e811eb0f 拆出的 5 个新分组
    ModelGroup.PROTECT.code -> sesameIconOf(context, SesameIcons.Security)
    ModelGroup.SPECIES.code -> sesameIconOf(context, SesameIcons.Paw)
    ModelGroup.OCEAN.code -> sesameIconOf(context, SesameIcons.Fish)
    ModelGroup.GREEN_FINANCE.code -> sesameIconOf(context, SesameIcons.Wallet)
    ModelGroup.ANSWER_AI.code -> sesameIconOf(context, SesameIcons.Question)
    else -> sesameIconOf(context, SesameIcons.Options)
} ?: ImageVector.Builder("blank", 24.dp, 24.dp, 24f, 24f).build()
