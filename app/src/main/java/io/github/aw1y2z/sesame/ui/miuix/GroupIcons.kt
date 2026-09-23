package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aw1y2z.sesame.data.ModelGroup

/**
 * 分组图标：按 [ModelGroup] 的编码取一个语义贴切的图标。
 *
 * M3 的列表靠前导图标承担「快速识别」——先认形状再读文字。
 * 分类记录、配置分组目录共用这套映射，保证同一个分组在哪个页面都是同一个符号。
 *
 * - 未知编码回落到一个中性符号，不返回 null（避免行内出现 24dp 空洞）；
 * - 「基础」组本身没有具象的业务语义，同样用中性符号，不与某个具体分组抢识别度。
 */
internal fun groupIconOf(code: String): ImageVector = when (code) {
    ModelGroup.FOREST.code -> Icons.Outlined.Forest
    ModelGroup.FARM.code -> Icons.Outlined.Agriculture
    ModelGroup.STALL.code -> Icons.Outlined.Cottage
    ModelGroup.ORCHARD.code -> Icons.Outlined.Yard
    ModelGroup.GOLDENBEANS.code -> Icons.Outlined.MonetizationOn
    ModelGroup.SPORTS.code -> Icons.Outlined.DirectionsRun
    ModelGroup.MEMBER.code -> Icons.Outlined.CardMembership
    ModelGroup.OTHER.code -> Icons.Outlined.MoreHoriz
    else -> Icons.Filled.Tune
}
