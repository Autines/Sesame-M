package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.aw1y2z.sesame.ui.theme.SesameDetailScaffold
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
import top.yukonga.miuix.kmp.basic.TextField

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
     * 统一落盘入口（二级/三级/四级同款实现）：
     * 先用 hasFieldChanges() 短路「无字段级改动」的情况，确认有改动后走 force=true，
     * 避免 ConfigV2.save() 内部再重复做一次全量序列化比较。
     */
    fun save() {
        if (!ConfigV2.hasFieldChanges()) return
        if (ConfigV2.save(userId, true)) {
            ToastUtil.show(this, "保存成功！")
            sendRestartIfNeeded()
        }
    }

    private fun sendRestartIfNeeded() {
        if (!StringUtil.isEmpty(userId)) {
            try {
                val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                intent.putExtra("userId", userId)
                sendBroadcast(intent)
            } catch (th: Throwable) {
                Log.printStackTrace(th)
            }
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
                    try {
                        val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                        intent.putExtra("userId", userId)
                        context.sendBroadcast(intent)
                    } catch (th: Throwable) {
                        Log.printStackTrace(th)
                    }
                }
                Model.initAllModel()
                ConfigPreload.prepare(userId)
                notifyModuleReload(context, userId)
                ToastUtil.show(context, "已导入当前账号配置")
                activity.recreate()
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
            ToastUtil.show(context, "导入失败！")
        }
    }

    // ============ 二级:分组目录 ============
    SesameDetailScaffold(
        title = "配置设置",
        onBack = { activity.saveAndFinish() },
        onImport = { importLauncher.launch("*/*") },
        onExport = { exportBackupLauncher.launch(fullBackupFileName()) },
        onClear = { showDeleteDialog = true }
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ============ 配置分组目录 ============
            SesameSectionTitle(text = "配置分组")
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
            }
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
                SesameConfirmDialog(
                    title = "警告",
                    text = "确认删除该配置？",
                    destructive = true,
                    onConfirm = {
                        showDeleteDialog = false
                        if (ConfigPreload.getConfigFile(userId).let { FileUtil.deleteFile(it) }) {
                            ToastUtil.show(context, "配置删除成功")
                        }
                        activity.finish()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }
        }
    }
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
 * 配置字段编辑项（原地展开编辑）。
 *
 * 值为「只写内存」：任何变更只调用 setObjectValue() 落在 ConfigV2 单例上，不触发磁盘写入。
 * 统一落盘由所在页面的退出流程负责（MiuixGroupFieldsActivity.saveAndFinish() / onBackPressed()），
 * 避免每次拨开关、每次提交输入都做一次「全量序列化 + 写盘 + 备份检查」。
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
            var checked by remember { mutableStateOf(field.value as? Boolean ?: false) }
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
