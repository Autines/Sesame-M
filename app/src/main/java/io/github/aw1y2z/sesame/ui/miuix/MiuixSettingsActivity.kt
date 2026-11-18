package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import io.github.aw1y2z.sesame.ui.HostBridge
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.EmptyModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField
import io.github.aw1y2z.sesame.ui.theme.SesameCardGroup
import io.github.aw1y2z.sesame.ui.theme.SesameClickRow
import io.github.aw1y2z.sesame.ui.theme.SesameConfirmDialog
import io.github.aw1y2z.sesame.ui.theme.SesameDetailTitleScaffold
import io.github.aw1y2z.sesame.ui.theme.SesameExpandRow
import io.github.aw1y2z.sesame.ui.theme.SesameInlineEditor
import io.github.aw1y2z.sesame.ui.theme.SesameRadioListRow
import io.github.aw1y2z.sesame.ui.theme.SesameSectionTitle
import io.github.aw1y2z.sesame.ui.theme.SesameSwitchRow
import io.github.aw1y2z.sesame.ui.theme.SesameText
import io.github.aw1y2z.sesame.ui.theme.sesameOnSurfaceVariant
import io.github.aw1y2z.sesame.ui.theme.sesameGroupHorizontalPadding
import io.github.aw1y2z.sesame.util.BackupUtil
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountOneModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectOneModelField
import io.github.aw1y2z.sesame.entity.IdAndName
import io.github.aw1y2z.sesame.entity.KVNode
import io.github.aw1y2z.sesame.util.JsonUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.StringUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixSettingsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        // 必须「先建注册表，再加载配置」，两步缺一不可：
        //   initAllModel()  重建出各 Model 实例（字段为默认值）；
        //   ConfigPreload.prepare() 末尾的 ConfigV2.load() 从 Model.getAllModelConfig() 取表，
        //   把用户已保存的值灌回去。
        // 少了 initAllModel() → 注册表为空 → ConfigV2 无表可填 → 配置页空白/默认值。
        Model.initAllModel()
        ConfigPreload.prepare(userId)
        // 返回键走 OnBackPressedDispatcher，不再覆盖 onBackPressed()：
        // targetSdk 35 起系统默认启用「预测式返回」，36 起更是不再调用 onBackPressed()，
        // 覆盖那个方法会静默失效（保存逻辑整段不执行，还不报错）。
        // 注册时机早于 Compose 内部的 BackHandler，所以弹窗打开时返回键仍优先关弹窗。
        onBackPressedDispatcher.addCallback(this) {
            save()
            finish()
        }
        setAppContent {
            SettingsContent(this, userId)
        }
    }

    /** 顶部返回按钮与系统返回统一入口：先保存再退出（与三级/四级保持一致）。 */
    fun saveAndFinish() {
        save()
        finish()
    }

    /**
     * 统一落盘入口（二级/三级/四级同款）：无改动直接短路，有改动走 force=true，
     * 免掉 save() 内部的全量序列化比较。
     * <p>写盘失败必须出声，否则改动无声消失。
     */
    fun save() {
        if (!ConfigV2.hasFieldChanges()) return
        if (ConfigV2.save(userId, true)) {
            ToastUtil.show(this, "保存成功！")
            sendRestartIfNeeded()
        } else {
            ToastUtil.show(this, "保存失败，改动未落盘")
        }
    }

    private fun sendRestartIfNeeded() {
        if (!StringUtil.isEmpty(userId)) {
            HostBridge.send(this, "restart", userId, null, false)
        }
    }

    /** 打开搜索命中：选择型进四级编辑页，其余进三级页并把字段名当搜索词带过去 */
    internal fun openFieldHit(hit: FieldHit) {
        if (hit.field.type in SELECTION_FIELD_TYPES) {
            startActivity(
                Intent(this, MiuixSelectionEditActivity::class.java).apply {
                    putExtra(MiuixSelectionEditActivity.EXTRA_USER_ID, userId)
                    putExtra(MiuixSelectionEditActivity.EXTRA_GROUP_CODE, hit.groupCode)
                    putExtra(MiuixSelectionEditActivity.EXTRA_FIELD_CODE, hit.field.code)
                    putExtra(MiuixSelectionEditActivity.EXTRA_MODEL_CODE, hit.modelCode)
                }
            )
        } else {
            startActivity(
                Intent(this, MiuixGroupFieldsActivity::class.java).apply {
                    putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                    putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, hit.groupCode)
                    putExtra(MiuixGroupFieldsActivity.EXTRA_SEARCH_QUERY, hit.field.name ?: hit.field.code)
                }
            )
        }
    }
}

@Composable
fun SettingsContent(activity: MiuixSettingsActivity, userId: String?) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 待恢复的备份包与它的清单信息：选完文件先停在"确认"这一步，用户点了才真正覆盖。
    // 用显式 MutableState（而不是 by 委托）是因为 stageZip 这个局部函数要写它。
    val pendingZip = remember { mutableStateOf<File?>(null) }
    val pendingInfo = remember { mutableStateOf<BackupUtil.Info?>(null) }

    /** 把选中的备份包落到缓存目录再处理 —— Content Uri 只能顺序读一次，而恢复要读两遍（预检 + 解包） */
    fun stageBackup(uri: android.net.Uri) {
        try {
            val tmp = File(context.cacheDir, RESTORE_TMP_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: run {
                ToastUtil.show(context, "读取失败")
                return
            }
            val info = BackupUtil.inspect(tmp)
            if (!info.ok) {
                ToastUtil.show(context, info.message)
                tmp.delete()
                return
            }
            pendingZip.value = tmp
            pendingInfo.value = info
        } catch (e: Exception) {
            Log.printStackTrace(e)
            ToastUtil.show(context, "读取失败！")
        }
    }

    // ---- 单账号配置（json）：只含当前账号的模块配置，用于分享 / 替换 ----
    val exportConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ConfigPreload.getConfigFile(userId).inputStream().use { it.copyTo(os) }
                }
                ToastUtil.show(context, "已导出当前账号配置（不含设置项与其它账号）")
            } catch (e: Exception) {
                ToastUtil.show(context, "导出失败！")
            }
        }
    }

    // ---- 完整备份（zip）：设置项 + 所有账号 + 需要长期养出来的状态 ----
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val result = context.contentResolver.openOutputStream(uri)?.use { os -> BackupUtil.export(os) }
            when {
                result == null -> ToastUtil.show(context, "导出失败：无法写入所选位置")
                result.ok -> ToastUtil.show(context, "已备份 ${result.fileCount} 个配置文件")
                else -> ToastUtil.show(context, result.message)
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
            ToastUtil.show(context, "导出失败！")
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) stageBackup(uri)
    }

    // 顶栏「导入」：一个入口吃两种格式 —— zip 走完整恢复，json 走单账号配置覆盖。
    // 这样历史导出的 .json 依然能直接导入，不用让用户去分辨按钮。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = ConfigPreload.getConfigFile(userId)
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                // 必须是合法的 config_v2（顶层含 modelFieldsMap）：只验 JSON 语法挡不住误选其它 JSON
                if (text.isNullOrBlank()) throw IllegalArgumentException("empty config")
                val node = JsonUtil.toNode(text) as? com.fasterxml.jackson.databind.JsonNode
                if (node?.has("modelFieldsMap") != true) throw IllegalArgumentException("not a config_v2 json")
                // 覆盖前先备份现有配置：即时快照与每日滚动备份各自独立
                FileUtil.backupConfigV2BeforeWrite(userId ?: "")
                FileUtil.backupConfigV2WithRolling(userId ?: "")
                if (!FileUtil.write2File(text, file)) throw IllegalStateException("write config failed")
                if (!StringUtil.isEmpty(userId)) {
                    HostBridge.send(context, "restart", userId, null, false)
                }
                Model.initAllModel()
                ConfigPreload.prepare(userId)
                notifyModuleReload(context, userId)
                ToastUtil.show(context, "已导入当前账号配置")
                activity.recreate()
            }
            catch (e: Exception) {
                Log.printStackTrace(e)
                ToastUtil.show(context, "导入失败！")
            }
        }
    }

    // ============ 二级:分组目录 ============
    SesameDetailTitleScaffold(
        title = "配置设置",
        onBack = { activity.saveAndFinish() },
        // 顶栏刻意**不放**导入/导出：它们与下方「备份与恢复」是同一动作的两个入口
        // （导出=完整备份、导入=zip 恢复），属于历史遗留的重复。已统一收敛到下方
        // 那套带文字说明的入口，顶栏只留「删除」。（2026-10-07 定）
        onClear = { showDeleteDialog = true }
    ) {
        // 上游 0cfb2abb：全局字段搜索 —— 搜到任意分组下的配置项，直接跳到可编辑处，
        // 免去「进分组→进三级页→再搜一次」的来回切换（如清理各模块黑名单）
        var searchQuery by remember { mutableStateOf("") }
        val query = searchQuery.trim()
        // 命中项连同归属一起攒好，用于结果行显示与跳转
        val hits = remember(query) {
            if (query.isEmpty()) emptyList() else buildFieldHits(query)
        }
            // 搜索框：与三级/四级页同款
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "",
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                "×",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp).clickable { searchQuery = "" }
                            )
                        }
                    }
                )
            }

            if (query.isNotEmpty()) {
                // ============ 搜索结果 ============
                if (hits.isEmpty()) {
                    SesameSectionTitle(text = "无匹配字段")
                } else {
                    // 标题是字段名、summary 给模型名，两者合起来足以区分同名字段。
                    // 容器与行沿用本地组件集（SesameCardGroup / SesameClickRow），不退回上游的 CardColumn/ArrowPreference
                    SesameCardGroup {
                        hits.forEach { hit ->
                            SesameClickRow(
                                title = hit.field.name ?: hit.field.code,
                                summary = hit.modelName,
                                icon = groupIconOf(hit.groupCode),
                                onClick = { activity.openFieldHit(hit) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                // ============ 配置分组目录 ============
                SesameCardGroup {
                    ModelGroup.values().forEach { g ->
                        if (Model.getGroupModelConfig(g).isNotEmpty()) {
                            SesameClickRow(
                                title = g.getName(),
                                icon = groupIconOf(g.getCode()),
                                onClick = {
                                    activity.startActivity(
                                        Intent(activity, MiuixGroupFieldsActivity::class.java).apply {
                                            putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                                            putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, g.name)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))

            // ============ 备份与恢复（整份搬）============
            SesameSectionTitle(text = "备份与恢复")
            SesameCardGroup {
                SesameClickRow(
                    title = "完整备份",
                    summary = "导出全部配置为 .zip（含设置项与所有账号）",
                    icon = Icons.Outlined.Backup,
                    onClick = { exportBackupLauncher.launch(fullBackupFileName()) }
                )
                SesameClickRow(
                    title = "从备份恢复",
                    summary = "选择之前导出的 .zip，覆盖还原全部配置",
                    icon = Icons.Outlined.Restore,
                    onClick = { restoreLauncher.launch("*/*") }
                )
            }
            Spacer(Modifier.height(16.dp))

            // ============ 配置分享（单账号）============
            SesameSectionTitle(text = "配置分享")
            SesameCardGroup {
                SesameClickRow(
                    title = "导出当前账号配置",
                    summary = "仅模块配置 .json，用于分享或替换单个账号",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        exportConfigLauncher.launch("[" + (userId ?: "默认") + "]-config_v2.json")
                    }
                )
                // 顶栏移除导入后，单账号 .json 的导入入口收在这里，与上面的「导出」成对。
                // 只接受 config_v2 结构（顶层含 modelFieldsMap），导入前会自动备份现有配置。
                SesameClickRow(
                    title = "导入当前账号配置",
                    summary = "选择 .json，覆盖当前账号的模块配置",
                    icon = Icons.Outlined.Restore,
                    onClick = { importLauncher.launch("*/*") }
                )
            }
            // 末尾留白：mochi 脚手架自带 contentBottomPadding，这里只补一份呼吸感 16dp。
            // （原写法 padding.calculateBottomPadding() 是给 bottomBar 让位；本页无 bottomBar，
            //   该值本来就是 0，换成固定 16dp 两风格等价。）
            Spacer(Modifier.height(16.dp))

            // 恢复确认：把包里有什么、要发生什么一次讲清楚，别让用户凭图标猜
            val info = pendingInfo.value
            if (info != null && pendingZip.value != null) {
                SesameConfirmDialog(
                    title = "恢复完整备份",
                    text = buildString {
                        append("备份时间：").append(info.createdAt ?: "未知")
                        append("\n包含 ").append(info.fileCount).append(" 个配置文件")
                        if (info.accountDirs.isNotEmpty()) {
                            append("、").append(info.accountDirs.size).append(" 个账号")
                        }
                        append("\n\n将覆盖当前全部配置（含界面设置与所有账号）。")
                        append("覆盖前会自动把当前配置存一份快照到 bak 目录，可回退。")
                    },
                    confirmText = "恢复",
                    destructive = true,
                    onConfirm = {
                        val zip = pendingZip.value
                        pendingZip.value = null
                        pendingInfo.value = null
                        if (zip != null) {
                            performRestore(activity, context, zip, userId)
                        }
                    },
                    onDismiss = {
                        pendingZip.value?.delete()
                        pendingZip.value = null
                        pendingInfo.value = null
                    }
                )
            }

            if (showDeleteDialog) {
                // 删的是不是「当前登录账号」？决定了这次删除是真删、还是等于"重置"
                val isCurrent = !userId.isNullOrEmpty() && userId == ConfigPreload.currentUserId()
                SesameConfirmDialog(
                    title = "警告",
                    text = if (isCurrent) {
                        "这是「当前正在使用」的账号。\n\n" +
                            "删除后模块会立刻为它重建一份默认配置（等于只做了重置），" +
                            "要彻底删掉，请先在支付宝里切换到其它账号再删。\n\n仍要继续吗？"
                    } else {
                        "确认彻底删除该账号？\n\n" +
                            "将删除：整个账号配置目录、该账号的全部滚动备份、账号序号登记。\n" +
                            "其它账号不受影响。"
                    },
                    destructive = true,
                    onConfirm = {
                        showDeleteDialog = false
                        if (ConfigPreload.purgeAccount(userId)) {
                            ToastUtil.show(context, "该账号配置已彻底删除")
                        } else {
                            ToastUtil.show(context, "该账号配置本就不存在")
                        }
                        activity.finish()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }
    }

}

/** 可在四级页编辑的字段类型（与三级页的分派保持一致） */
private val SELECTION_FIELD_TYPES = setOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE")

/** 一条搜索命中：字段本身 + 它所属的分组与模型（跳转与展示都需要） */
internal data class FieldHit(
    val groupCode: String,
    val modelCode: String,
    val modelName: String,
    val field: ModelField<*>
)

/**
 * 在全部模块的可见字段里搜关键字。
 * <p>可见性沿用三级页判据（父字段未激活则不可见），保证搜到的字段点进去能看到。
 */
private fun buildFieldHits(query: String): List<FieldHit> {
    val result = ArrayList<FieldHit>()
    ModelGroup.values().forEach { group ->
        Model.getGroupModelConfig(group).values.forEach { modelConfig ->
            val visible = modelConfig.fields.values.filter { it.isVisible(modelConfig) }
            visible.forEach { field ->
                if (fieldMatchesSearch(query, field)) {
                    result.add(
                        FieldHit(
                            groupCode = group.name,
                            modelCode = modelConfig.getCode(),
                            modelName = modelConfig.name ?: "",
                            field = field
                        )
                    )
                }
            }
        }
    }
    return result
}

/** 恢复前把备份包复制到缓存目录时用的落地名（Content Uri 只能顺序读一次） */
private const val RESTORE_TMP_NAME = "sesame-restore.zip"

/** 顶栏「导入」选中文件后的落地名 */
private const val IMPORT_TMP_NAME = "sesame-import.bin"

/**
 * 完整备份的文件名建议值。
 *
 * 带时间戳：短时间内连导两次不会互相覆盖，用户在文件管理器里也一眼能挑出最新的那份。
 * **刻意用纯 ASCII**：中文名在 adb / 部分 Windows 工具链上会因编码被截断
 * （实测 `Sesame-M-完整备份-20260919-140741.zip` 拉到电脑上变成 `...-1407`，后缀都没了），
 * 备份文件本来就是拿来搬来搬去的，名字越朴素越不容易出岔子。
 */
private fun fullBackupFileName(): String =
    "Sesame-M-backup-" +
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date()) +
        ".zip"

/**
 * 执行恢复，并把内存态刷新成磁盘上的新内容。
 *
 * 顺序不能反：共享设置 → 模块注册表 → 各 IdMap 与账号配置。
 * `ConfigPreload.prepare()` 末尾的 `ConfigV2.load()` 要从注册表取表，注册表没重建就取不到。
 */
private fun performRestore(
    activity: MiuixSettingsActivity,
    context: Context,
    zip: File,
    userId: String?
) {
    try {
        val result = BackupUtil.restore(zip)
        if (!result.ok) {
            ToastUtil.show(context, result.message)
            return
        }
        AppConfig.load()
        Model.initAllModel()
        ConfigPreload.prepare(userId)
        notifyModuleReload(context, null)
        ToastUtil.show(context, "已恢复 ${result.fileCount} 个配置文件")
        // 界面风格 / 深色模式可能整片变了，重建一次让这一页立刻按新设置呈现
        activity.recreate()
    } catch (t: Throwable) {
        Log.printStackTrace(t)
        ToastUtil.show(context, "恢复失败！")
    } finally {
        zip.delete()
    }
}

/**
 * 通知注入进程重读配置。
 *
 * 两条广播各管一段，缺一不可：
 * - `reloadConfig`：让宿主进程重新加载 `appConfig.json`（界面风格、日志开关等共享设置）；
 * - `restart`：让宿主进程重建模块上下文、重读账号配置。
 *
 * [scopeUserId] 传 null 表示「不限定账号」——接收方把 userId 为空判定为当前登录账号，
 * 完整恢复要的正是这个语义（所有账号都变了，让活着那个重载即可）；
 * 单账号配置导入则传入具体 userId，不去打扰别的账号。
 */
private fun notifyModuleReload(context: Context, scopeUserId: String?) {
    try {
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
    } catch (t: Throwable) {
        Log.printStackTrace(t)
    }
    try {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
        if (!StringUtil.isEmpty(scopeUserId)) {
            intent.putExtra("userId", scopeUserId)
        }
        context.sendBroadcast(intent)
    } catch (t: Throwable) {
        Log.printStackTrace(t)
    }
}

/**
 * 配置字段编辑项（原地展开编辑）。变更只调 setObjectValue() 写内存，落盘交给页面退出流程，
 * 否则每次拨开关、每次提交输入都要做一次全量序列化 + 写盘 + 备份检查。
 */
@Composable
fun FieldItem(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldItemBody(field, onFieldChanged)
        // 上游新增：字段补充说明统一在这里渲染（BOOLEAN 的说明由开关行 summary 承载，不重复）。
        // 渲染走本地 SesameText，MD3 风格下不会跳出观感。
        val description = field.description
        if (field.type != "BOOLEAN" && !description.isNullOrBlank()) {
            SesameText(
                text = description,
                fontSize = 12.sp,
                color = sesameOnSurfaceVariant(),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun FieldItemBody(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    // 用字段名唯一标识展开状态，避免 LazyColumn 复用导致错位；
    // 只有「当前字段」与「上次展开的字段」一致时才保持展开，切换字段自动收起上一行。
    val fieldKey = "${field.type}:${field.name}"
    var expandedFieldKey by remember { mutableStateOf<String?>(null) }
    val expanded = expandedFieldKey == fieldKey

    fun openEditor() {
        expandedFieldKey = if (expanded) null else fieldKey
    }

    when {
        field.type == "BOOLEAN" -> {
            // 以字段编码为 key：行被复用/重排时不要沿用上一个字段的勾选状态
            var checked by remember(field.code) { mutableStateOf(field.value as? Boolean ?: false) }
            SesameSwitchRow(
                title = field.name ?: "",
                summary = field.description,
                checked = checked,
                onCheckedChange = {
                    checked = it
                    field.setObjectValue(it)
                    onFieldChanged?.invoke()
                }
            )
        }

        field.type in listOf("INTEGER", "MULTIPLY_INTEGER") -> {
            val imf = field as? IntegerModelField
            val maxLimit = imf?.maxLimit
            val lowerLimit = imf?.minLimit
            val current = (field as? IntegerModelField.MultiplyIntegerModelField)?.getConfigValue()?.toInt()
                ?: (field.value as? Int ?: 0)
            val limitHint = when {
                lowerLimit == null && maxLimit == null -> ""
                lowerLimit != null && lowerLimit < 0 -> "（-1 表示按最大额度）"
                lowerLimit != null && maxLimit != null -> "（${lowerLimit}~${maxLimit}）"
                maxLimit != null -> "（上限 ${maxLimit}）"
                else -> "（下限 ${lowerLimit}）"
            }
            SesameExpandRow(
                title = field.name ?: "",
                summary = if (limitHint.isEmpty()) current.toString() else "$current$limitHint",
                expandable = true,
                onClick = { openEditor() }
            )
            if (expanded) {
                var text by remember { mutableStateOf(current.toString()) }
                val parsed = text.trim().toIntOrNull()
                val outOfRange = parsed != null &&
                    ((lowerLimit != null && parsed < lowerLimit) || (maxLimit != null && parsed > maxLimit))
                val rangeText = when {
                    lowerLimit != null && maxLimit != null -> "$lowerLimit ~ $maxLimit"
                    lowerLimit != null -> "不小于 $lowerLimit"
                    maxLimit != null -> "不大于 $maxLimit"
                    else -> null
                }
                SesameInlineEditor(
                    value = text,
                    // 输入即生效：合法就立刻写回内存配置；越界期间只把文本留在编辑框里，
                    // 由下方 supportingText 指出范围，不再用 Toast 打断输入。
                    onValueChange = { input ->
                        val filtered = input.filterIndexed { index, c ->
                            c.isDigit() || (c == '-' && index == 0)
                        }
                        text = filtered
                        val v = filtered.trim().toIntOrNull()
                        if (v != null &&
                            (lowerLimit == null || v >= lowerLimit) &&
                            (maxLimit == null || v <= maxLimit)
                        ) {
                            field.setConfigValue(v.toString())
                            onFieldChanged?.invoke()
                        }
                    },
                    isError = text.isNotEmpty() && (parsed == null || outOfRange),
                    supportingText = when {
                        text.isEmpty() -> rangeText?.let { "范围 $it" }
                        parsed == null || outOfRange ->
                            rangeText?.let { "请输入 $it 范围内的整数" } ?: "请输入有效整数"
                        else -> rangeText?.let { "范围 $it" }
                    }
                )
            }
        }

        field.type in listOf("STRING", "TEXT") -> {
            SesameExpandRow(
                title = field.name ?: "",
                summary = field.configValue,
                expandable = true,
                onClick = { openEditor() }
            )
            if (expanded) {
                var text by remember { mutableStateOf(field.configValue ?: "") }
                SesameInlineEditor(
                    value = text,
                    // 输入即生效
                    onValueChange = { input ->
                        text = input
                        field.setObjectValue(input)
                        onFieldChanged?.invoke()
                    }
                )
            }
        }

        // 只读字段：不给展开箭头，避免暗示「还能点进去改」
        field.type == "READ_TEXT" || field.type == "URL_TEXT" -> {
            SesameExpandRow(
                title = field.name ?: "",
                summary = field.configValue,
                expandable = false,
                onClick = {}
            )
        }

        field.type in listOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE") -> {
            // 由 GroupFieldsPage 处理
        }

        field.type == "EMPTY" -> {
            val emf = field as? EmptyModelField
            SesameClickRow(
                title = field.name ?: "",
                onClick = { emf?.clickRunner?.run() }
            )
        }

        field.type == "LIST" -> {
            val list = (field.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            SesameExpandRow(
                title = field.name ?: "",
                summary = list.joinToString(","),
                expandable = true,
                onClick = { openEditor() }
            )
            if (expanded) {
                var text by remember { mutableStateOf(list.joinToString("\n")) }
                SesameInlineEditor(
                    value = text,
                    // 输入即生效：一行一个值，空行忽略
                    onValueChange = { input ->
                        text = input
                        field.setObjectValue(
                            input.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        )
                        onFieldChanged?.invoke()
                    },
                    singleLine = false,
                    minLines = 3
                )
            }
        }

        field.type == "CHOICE" -> {
            val cmf = field as? ChoiceModelField
            val choiceArray = cmf?.expandKey ?: emptyArray()
            val current = field.value as? Int ?: 0
            SesameExpandRow(
                title = field.name ?: "",
                summary = choiceArray.getOrNull(current),
                expandable = true,
                onClick = { openEditor() }
            )
            if (expanded) {
                var sel by remember { mutableStateOf(current) }
                Column(Modifier.fillMaxWidth()) {
                    choiceArray.forEachIndexed { index, opt ->
                        // 点选即生效（不再需要保存按钮）
                        SesameRadioListRow(
                            title = opt,
                            selected = sel == index,
                            onClick = {
                                sel = index
                                field.setObjectValue(index)
                                onFieldChanged?.invoke()
                            }
                        )
                    }
                }
            }
        }

        else -> {
            SesameText(text = field.name ?: "")
        }
    }
}
