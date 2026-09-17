package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.model.task.antMember.AntMember;
import io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class MessageUtil {
    private static final String TAG = MessageUtil.class.getSimpleName();
    private static final String UNKNOWN_TAG = "Unknown TAG";

    /** 自动拉黑：模糊错误需连续命中的确认次数 */
    private static final int BLACKLIST_CONFIRM_HITS = 3;
    /** 自动拉黑：命中计数的有效期（天），超过该窗口未再命中则重新计数 */
    private static final int BLACKLIST_CONFIRM_WINDOW_DAYS = 3;
    /** 自动拉黑：满该天数后自动解禁并重试一次 */
    private static final int BLACKLIST_RETRY_DAYS = 3;
    /** 自动拉黑：解禁重试的最大次数，超过后永久拉黑、不再自动解禁 */
    private static final int BLACKLIST_MAX_RETRY = 3;

    public static JSONObject newJSONObject(String str) {
        try {
            return new JSONObject(str);
        } catch (Throwable t) {
            Log.err(TAG, "newJSONObject err:", t);
        }
        return null;
    }

    /**
     * 服务端繁忙（102）日志降噪：同一模块（tag）累计打印满该次数后，后续 102 不再打印。
     * <p>这里**只降噪、不拦截**：请求照发——不做跳过、不做退避 sleep、也不写"当日停试"标记。
     */
    private static final int SERVER_BUSY_LOG_LIMIT = 3;
    /** tag → 已打印过的 102 次数（只增不减，进程重启即清零） */
    private static final Map<String, int[]> SERVER_BUSY_LOG_COUNT = new ConcurrentHashMap<>();

    /**
     * 是否服务端繁忙：`resultCode=102` 或文案为"服务器正在开小差"。
     * <p>这类错误是临时性的，既不该拉黑，也不该几秒内连续重试。
     */
    private static boolean isServerBusy(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("102".equals(jo.optString("resultCode", "").trim())) {
            return true;
        }
        return jo.optString("memo", "").contains("服务器正在开小差");
    }

    /** 在多个文案字段里找关键字（各接口字段不统一，不能只扫 desc） */
    private static boolean anyFieldContains(JSONObject jo, String keyword) {
        for (String field : FAIL_TEXT_FIELDS) {
            if (jo.optString(field, "").contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 打印失败应答。唯一的特殊处理是**服务端繁忙（102）的日志降噪**：同一 tag 累计打印满
     * {@link #SERVER_BUSY_LOG_LIMIT} 次后不再打印，避免限流期间刷屏。
     * <p>注意：这里不拦截、不退避、不跳过——请求该发照发，只是少写几行日志。
     */
    public static void printErrorMessage(String tag, JSONObject jo, String errorMessageField) {
        try {
            String memo = jo.getString(errorMessageField);
            if (isServerBusy(jo) && !shouldLogServerBusy(tag)) {
                return;
            }
            Log.record(tag + " error:" + memo);
            Log.i(memo, jo.toString());
        } catch (Throwable t) {
            Log.err(TAG, "printErrorMessage err:", t);
        }
    }

    /**
     * 服务端繁忙（102）是否还应该打印日志：同一 tag 前 {@link #SERVER_BUSY_LOG_LIMIT} 次打印，
     * 之后静默（并在最后一次打印时提示"已开始静默"，免得看日志的人以为 102 消失了）。
     */
    private static boolean shouldLogServerBusy(String tag) {
        String key = StringUtil.isEmpty(tag) ? UNKNOWN_TAG : tag;
        int printed;
        synchronized (MessageUtil.class) {
            int[] state = SERVER_BUSY_LOG_COUNT.computeIfAbsent(key, k -> new int[]{0});
            if (state[0] >= SERVER_BUSY_LOG_LIMIT) {
                return false;
            }
            state[0]++;
            printed = state[0];
        }
        if (printed == SERVER_BUSY_LOG_LIMIT) {
            Log.i(key, "服务端繁忙🌧️本模块 102 已累计" + printed + "次，后续 102 不再打印日志（请求照常发送）");
        }
        return true;
    }

    public static Boolean checkMemo(JSONObject jo) {
        return checkMemo(UNKNOWN_TAG, jo);
    }

    public static Boolean checkMemo(String tag, JSONObject jo) {
        try {
            if (!"SUCCESS".equals(jo.optString("memo"))) {
                if (jo.has("memo")) {
                    printErrorMessage(tag, jo, "memo");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "checkMemo err:", t);
        }
        return false;
    }

    public static Boolean checkResultCode(JSONObject jo) {
        return checkResultCode(UNKNOWN_TAG, jo);
    }

    public static Boolean checkResultCode(String tag, JSONObject jo) {
        try {
            /// 添加空值检查
            if (jo == null) {
                Log.i(tag, "JSON对象为空");
                return false;
            }

            if (jo.optBoolean("success") && jo.optString("desc").equals("处理成功")) {
                return true;
            }

            Object resultCode = jo.opt("resultCode");
            if (resultCode == null) {
                Log.i(tag, jo.toString());
                return false;
            }
            if (resultCode instanceof Integer) {
                return checkResultCodeInteger(tag, jo);
            } else if (resultCode instanceof String) {
                return checkResultCodeString(tag, jo);
            }
            Log.i(tag, jo.toString());
            return false;
        } catch (Throwable t) {
            Log.err(TAG, "checkResultCode err:", t);
        }
        return false;
    }

    public static Boolean checkResultCodeString(String tag, JSONObject jo) {
        try {
            String resultCode = jo.optString("resultCode");
            if (!resultCode.equalsIgnoreCase("SUCCESS") && !resultCode.equals("100")) {
                // CONFIG_NOT_EXIST 是正常响应（用户未配置权益），不打印错误日志
                if ("CONFIG_NOT_EXIST".equals(resultCode)) {
                    return false;
                }
                if (jo.has("resultDesc")) {
                    printErrorMessage(tag, jo, "resultDesc");
                } else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "checkResultCodeString err:", t);
        }
        return false;
    }

    public static Boolean checkResultCodeInteger(String tag, JSONObject jo) {
        try {
            int resultCode = jo.optInt("resultCode");
            if (resultCode != 200) {
                if (jo.has("resultMsg")) {
                    printErrorMessage(tag, jo, "resultMsg");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "checkResultCodeInteger err:", t);
        }
        return false;
    }

    public static Boolean checkSuccess(JSONObject jo) {
        return checkSuccess(UNKNOWN_TAG, jo);
    }

    public static Boolean checkSuccess(String tag, JSONObject jo) {
        try {
            if (!jo.optBoolean("success") && !jo.optBoolean("isSuccess")) {
                if (jo.has("errorMsg")) {
                    printErrorMessage(tag, jo, "errorMsg");
                } else if (jo.has("errorMessage")) {
                    printErrorMessage(tag, jo, "errorMessage");
                } else if (jo.has("desc")) {
                    printErrorMessage(tag, jo, "desc");
                } else if (jo.has("resultDesc")) {
                    printErrorMessage(tag, jo, "resultDesc");
                } else if (jo.has("resultView")) {
                    printErrorMessage(tag, jo, "resultView");
                } else {
                    Log.i(tag, jo.toString());
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "checkSuccess err:", t);
        }
        return false;
    }

    /**
     * 是否为"可重试错误"（限流、远端异常、网络抖动等）。
     * <p>这类错误只是临时故障，一律不拉黑，避免把任务永久跳过。
     */
    public static boolean isRetryable(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        String code = jo.optString("code", "").trim();
        if (code.isEmpty()) {
            code = jo.optString("resultCode", "").trim();
        }
        if (code.isEmpty()) {
            code = jo.optString("errorCode", "").trim();
        }
        return jo.optBoolean("retryable", false)
                || jo.optBoolean("retriable", false)
                || "3000".equals(code)
                || "REMOTE_INVOKE_EXCEPTION".equals(code);
    }

    /**
     * 拉黑判定要扫的文案字段：各接口字段不统一（desc / resultDesc / resultView / memo / errorMsg …），
     * 原先只扫 desc，会把文案放在其它字段的失败漏掉（表现为"该拉黑却没拉黑、每天白试一次"）。
     */
    private static final String[] FAIL_TEXT_FIELDS = {
            "desc", "resultDesc", "resultView", "memo", "errorMsg", "errorMessage", "resultMsg"
    };

    /**
     * 只有"文案含不支持rpc调用"一个判据的列表：listTitle → {ModelFieldsType, 列表中文名}。
     * <p>用于非 desc 字段命中时的"连续确认"通道（其余带额外判据的列表在各自分支里处理）。
     */
    private static final Map<String, String[]> BLACKLIST_LIST_TARGETS = new LinkedHashMap<>();

    static {
        BLACKLIST_LIST_TARGETS.put("AntForestVitalityTaskList", new String[]{"AntForestV2", "蚂蚁森林活力值任务"});
        BLACKLIST_LIST_TARGETS.put("AntForestHuntTaskList", new String[]{"AntForestV2", "蚂蚁森林抽抽乐任务"});
        BLACKLIST_LIST_TARGETS.put("AntFarmDoFarmTaskList", new String[]{"AntFarm", "庄园饲料任务"});
        BLACKLIST_LIST_TARGETS.put("AntFarmDrawMachineTaskList", new String[]{"AntFarm", "庄园装扮抽抽乐任务"});
        BLACKLIST_LIST_TARGETS.put("AntDodoTaskList", new String[]{"AntDodo", "神奇物种任务"});
        BLACKLIST_LIST_TARGETS.put("AntOceanAntiepTaskList", new String[]{"AntOcean", "神奇海洋普通任务"});
        BLACKLIST_LIST_TARGETS.put("AntOceanFishBlackList", new String[]{"AntOcean", "神奇海洋去摸鱼任务"});
        BLACKLIST_LIST_TARGETS.put("AntStallTaskList", new String[]{"AntStall", "新村任务"});
        BLACKLIST_LIST_TARGETS.put("AntMemberTaskList", new String[]{"AntMember", "会员任务"});
        // 注：GoldenBeansTaskList / AntOrchardTaskList / AntSportsTaskList / MemberCreditSesameTaskList
        // 有自己的额外判据，在下方分支里处理，不走这张表，避免重复动作
    }

    public static void checkResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle, JSONObject jo) {
        try {
            if (jo == null) {
                Log.i(listTitle, "JSON对象为空");
                return;
            }
            // 可重试错误（限流、远端异常、网络抖动）一律不拉黑，对全部任务列生效
            if (isRetryable(jo)) {
                return;
            }
            // 关键字判定：desc 命中沿用原有"立即拉黑"语义；其它字段命中走"连续确认"（字段不统一，
            // 放宽判定范围必须更保守，避免一次误判就把任务停掉 3 天）
            boolean strongHit = false;
            boolean weakHit = false;
            for (String field : FAIL_TEXT_FIELDS) {
                String text = jo.optString(field, "");
                if (text.isEmpty() || !(text.contains("不支持rpc调用") || text.contains("不支持RPC调用"))) {
                    continue;
                }
                if ("desc".equals(field)) {
                    strongHit = true;
                } else {
                    weakHit = true;
                }
            }

            //标记是否加黑（保持原有语义：只有 desc 命中才进入各列表的"立即拉黑"分支）
            boolean canAddBlackList = strongHit;

            // 非 desc 字段命中：原各分支只认 desc 会漏判（文案被放在 memo/resultDesc 等字段），
            // 这里统一走"连续命中确认"通道；带额外判据的列表由下方各自分支处理
            if (weakHit && !strongHit) {
                String[] weakTarget = BLACKLIST_LIST_TARGETS.get(listTitle);
                if (weakTarget != null) {
                    MarkTaskBlackListConfirm(weakTarget[0], listTitle, weakTarget[1], taskTitle);
                }
            }

            //这里根据对应任务返回异常的值精准设置拉黑条件
            switch (listTitle) {
                //蚂蚁森林活力值任务AntForestV2
                case "AntForestVitalityTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntForestV2", listTitle, "蚂蚁森林活力值任务", taskTitle);
                    }
                    break;

                //蚂蚁森林抽抽乐任务AntForestV2
                case "AntForestHuntTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntForestV2", listTitle, "蚂蚁森林抽抽乐任务", taskTitle);
                    }
                    break;

                //庄园饲料任务AntFarm
                case "AntFarmDoFarmTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园饲料任务", taskTitle);
                    }
                    break;

                //庄园装扮抽抽乐任务AntFarm
                case "AntFarmDrawMachineTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntFarm", listTitle, "庄园装扮抽抽乐任务", taskTitle);
                    }
                    break;

                //神奇物种任务AntDodo
                case "AntDodoTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntDodo", listTitle, "神奇物种任务", taskTitle);
                    }
                    break;

                //神奇海洋普通任务AntOcean
                case "AntOceanAntiepTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOcean", listTitle, "神奇海洋普通任务", taskTitle);
                    }
                    break;

                //神奇海洋去摸鱼任务AntOcean
                case "AntOceanFishBlackList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntOcean", listTitle, "神奇海洋去摸鱼任务", taskTitle);
                    }
                    break;

                //农场肥料任务AntOrchard
                case "AntOrchardTaskList":
                    if (strongHit) {
                        MarkTaskBlackList("AntOrchard", listTitle, "农场肥料任务", taskTitle);
                    } else if (weakHit || anyFieldContains(jo, "任务全局配置不存在")) {
                        // 关键字落在非 desc 字段 / 文案模糊（可能只是活动当天未配置）→ 连续命中确认
                        MarkTaskBlackListConfirm("AntOrchard", listTitle, "农场肥料任务", taskTitle);
                    }
                    break;

                //金豆夺宝任务goldenbeans
                case "GoldenBeansTaskList": {
                    // 错误码依次取 code / resultCode / errorCode（不同接口字段不统一）
                    String code = jo.optString("code", "").trim();
                    if (code.isEmpty()) {
                        code = jo.optString("resultCode", "").trim();
                    }
                    if (code.isEmpty()) {
                        code = jo.optString("errorCode", "").trim();
                    }
                    // 错误文案依次取 desc / resultDesc / memo
                    String message = jo.optString("desc", "");
                    if (message.isEmpty()) {
                        message = jo.optString("resultDesc", "");
                    }
                    if (message.isEmpty()) {
                        message = jo.optString("memo", "");
                    }
                    // 确定性不可恢复错误（可重试错误已在入口统一拦截）：
                    // 1) 任务Id非法、入参非法等不可恢复错误码；
                    // 2) 服务端明确不支持 rpc 调用。
                    boolean unsupported = code.contains("400000040");
                    boolean invalid = code.contains("20020012")
                            || code.contains("TASK_ID_INVALID")
                            || code.contains("ILLEGAL_ARGUMENT")
                            || message.contains("不支持rpc调用")
                            || message.contains("不支持RPC调用");
                    if (unsupported || invalid) {
                        canAddBlackList = true;
                    } else if (message.contains("任务全局配置不存在")) {
                        // 文案模糊（可能只是活动当天未配置），需连续命中确认
                        MarkTaskBlackListConfirm("goldenbeans", listTitle, "金豆夺宝任务", taskTitle);
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("goldenbeans", listTitle, "金豆夺宝任务", taskTitle);
                    }
                    break;
                }

                //新村任务AntStall
                case "AntStallTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntStall", listTitle, "新村任务", taskTitle);
                    }
                    break;

                //运动任务AntSports
                case "AntSportsTaskList":
                    if (jo.has("errorCode")) {
                        String errorCode = jo.optString("errorCode");
                        // {"ariverRpcTraceId":"21a4804717677001946607240e1734","errorCode":"TASK_ID_INVALID","errorMsg":"海豚任务id非法","retryable":false,"success":false}
                        if (errorCode.contains("TASK_ID_INVALID")) {
                            canAddBlackList = true;
                        }

                    }
                    if (jo.has("errorMsg")) {
                        String errorMsg = jo.optString("errorMsg");
                        if (errorMsg.contains("海豚活动触发不可重试错误")) {
                            canAddBlackList = true;
                        }
                    }
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntSports", listTitle, "运动任务", taskTitle);
                    } else if (weakHit) {
                        // 关键字落在非 desc 字段 → 连续命中确认
                        MarkTaskBlackListConfirm("AntSports", listTitle, "运动任务", taskTitle);
                    }
                    break;

                //会员任务AntMember
                case "AntMemberTaskList":
                    if (canAddBlackList) {
                        MarkTaskBlackList("AntMember", listTitle, "会员任务", taskTitle);

                    }
                    break;

                //会员芝麻信用任务芝麻粒AntMember
                case "MemberCreditSesameTaskList":
                    if (strongHit) {
                        MarkTaskBlackList("AntMember", listTitle, "会员芝麻信用任务芝麻粒", taskTitle);
                    } else if (weakHit
                            || anyFieldContains(jo, "不是有效的入参")
                            || anyFieldContains(jo, "存在进行中的生活记录")
                            || anyFieldContains(jo, "生活记录模板不存在")) {
                        // 关键字落在非 desc 字段 / 文案模糊（可能只是当天状态异常）→ 连续命中确认
                        MarkTaskBlackListConfirm("AntMember", listTitle, "会员芝麻信用任务芝麻粒", taskTitle);
                    }
                    break;

            }
        } catch (Throwable t) {
            Log.err(TAG, "checkSuccess err:", t);
        }
    }

    public static void MarkTaskBlackList(String ModelFieldsType, String listTitle, String TaskListName, String taskTitle) {
        ConfigV2 config = ConfigV2.INSTANCE;
        ModelFields TaskModelFields = config.getModelFieldsMap().get(ModelFieldsType);
        SelectModelField TaskSelectModelField = (SelectModelField) TaskModelFields.get(listTitle);
        if (TaskSelectModelField == null) {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
            return;
        }
        if (!TaskSelectModelField.contains(taskTitle)) {
            TaskSelectModelField.add(taskTitle, 0); // 数组类型忽略count，传0
        }
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.record("自动拉黑🏴在[" + TaskListName + "]中添加[" + taskTitle + "]黑名单:" + TaskSelectModelField.getValue());
            // 记录拉黑日期，供"超期自动解禁重试"使用（只记自动项，用户手动加的不会被解禁）
            recordAutoBlack(ModelFieldsType, listTitle, taskTitle);
        } else {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
        }
    }

    /**
     * 自动拉黑（需连续命中确认）：用于错误文案模糊、可能只是临时状态的任务。
     * <p>连续命中 {@link #BLACKLIST_CONFIRM_HITS} 次才真正拉黑；未达标时只记录命中并打日志，
     * 避免一次性的临时故障（活动当天未配置等）被永久跳过。
     */
    public static void MarkTaskBlackListConfirm(String ModelFieldsType, String listTitle, String TaskListName, String taskTitle) {
        try {
            String key = autoBlackKey(ModelFieldsType, listTitle, taskTitle);
            AutoBlackRecord record = AutoBlackRecord.parse(AutoBlackListMap.get(key));
            long today = todayIndex();
            if (record == null || today - record.lastDay > BLACKLIST_CONFIRM_WINDOW_DAYS) {
                record = new AutoBlackRecord();
            }
            if (record.hits + 1 < BLACKLIST_CONFIRM_HITS) {
                record.hits = record.hits + 1;
                record.lastDay = today;
                record.blackDay = 0L;
                AutoBlackListMap.put(key, record.format());
                AutoBlackListMap.save();
                Log.record("自动拉黑🕵️[" + TaskListName + "][" + taskTitle + "]可疑错误第"
                        + record.hits + "/" + BLACKLIST_CONFIRM_HITS + "次命中，暂不拉黑");
                return;
            }
            MarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle);
        } catch (Throwable t) {
            Log.err(TAG, "MarkTaskBlackListConfirm err:", t);
        }
    }

    /**
     * 解禁超期的"自动拉黑"任务：自动拉黑满 {@link #BLACKLIST_RETRY_DAYS} 天后移出黑名单、重新尝试一次；
     * 若再次失败会重新拉黑并重新计时。
     * <p>只处理模块自动加入的项，用户手动加入的黑名单不受影响。
     */
    public static void sweepExpiredBlackList() {
        try {
            AutoBlackListMap.load();
            if (AutoBlackListMap.getMap().isEmpty()) {
                return;
            }
            long today = todayIndex();
            boolean changed = false;
            for (String key : new ArrayList<>(AutoBlackListMap.keys())) {
                AutoBlackRecord record = AutoBlackRecord.parse(AutoBlackListMap.get(key));
                if (record == null) {
                    AutoBlackListMap.remove(key);
                    changed = true;
                    continue;
                }
                if (record.blackDay == AutoBlackRecord.PERMANENT) {
                    // 已永久拉黑（解禁重试次数用尽），不再自动解禁
                    continue;
                }
                if (record.blackDay == 0L) {
                    // 未拉黑：仅"观察期"记录会在窗口过期后丢弃（解禁后的记录保留重试次数）
                    if (record.retry == 0 && today - record.lastDay > BLACKLIST_CONFIRM_WINDOW_DAYS) {
                        AutoBlackListMap.remove(key);
                        changed = true;
                    }
                    continue;
                }
                if (today - record.blackDay < BLACKLIST_RETRY_DAYS) {
                    continue;
                }
                String[] parts = key.split("\\|", 3);
                if (parts.length < 3) {
                    AutoBlackListMap.remove(key);
                    changed = true;
                    continue;
                }
                String taskTitle = parts[2];
                SelectModelField field = findTaskListField(parts[0], parts[1]);
                if (field != null) {
                    field.remove(taskTitle);
                    record.hits = 0;
                    record.lastDay = today;
                    record.blackDay = 0L;
                    record.retry = record.retry + 1;
                    // 保留重试次数，供"再失败满 BLACKLIST_MAX_RETRY 次则永久拉黑"判断
                    AutoBlackListMap.put(key, record.format());
                    Log.record("自动解禁🕊️[" + taskTitle + "]自动拉黑已满" + BLACKLIST_RETRY_DAYS + "天，移出黑名单重试(第"
                            + record.retry + "/" + BLACKLIST_MAX_RETRY + "次)");
                } else {
                    AutoBlackListMap.remove(key);
                }
                changed = true;
            }
            AutoBlackListMap.save();
            if (changed) {
                ConfigV2.save(UserIdMap.getCurrentUid(), false);
            }
        } catch (Throwable t) {
            Log.err(TAG, "sweepExpiredBlackList err:", t);
        }
    }

    /**
     * 原先被"预置拉黑"的技术性不可自动化项：不再由各模块每日 init 预置，
     * 改由自动拉黑机制自行判定（首跑尝试 → 失败即拉黑 → 满 N 天解禁复核 → 重试满 N 次永久拉黑）。
     * <p>键为 {@code module|listTitle}，值为需要释放的条目（与写入黑名单时的键一致）。
     * <p>注意：这些条目同时必须从各模块 init 的默认黑名单里删除，否则会被每日补回。
     */
    private static final Map<String, String[]> RELEASED_DEFAULTS = new LinkedHashMap<>();

    static {
        RELEASED_DEFAULTS.put("AntOcean|AntOceanAntiepTaskList", new String[]{
                "随机任务：玩一玩得拼图"});
        RELEASED_DEFAULTS.put("AntOcean|AntOceanFishBlackList", new String[]{
                "玩一玩向僵尸开炮"});
        RELEASED_DEFAULTS.put("AntForestV2|AntForestVitalityTaskList", new String[]{
                "三国大冒险过1关征战"});
        RELEASED_DEFAULTS.put("AntForestV2|AntForestHuntTaskList", new String[]{
                "【限时】玩游戏得2次机会", "去乐园开宝箱得机会"});
        RELEASED_DEFAULTS.put("AntFarm|AntFarmDrawMachineTaskList", new String[]{
                "【限时】玩游戏得新机会", "【限时】玩游戏得3次机会", "限时玩游戏得新机会",
                "【限时】开宝箱得2次机会", "【限时】开宝箱得3次机会"});
        RELEASED_DEFAULTS.put("AntOrchard|AntOrchardTaskList", new String[]{
                "逛助农好货得肥料", "钓鱼1次", "逛一逛闪购外卖", "逛好物最高得1500肥料"});
        RELEASED_DEFAULTS.put("AntMember|MemberCreditSesameTaskList", new String[]{
                "去玩小游戏"});
    }

    /**
     * 释放原先预置拉黑的技术性不可自动化项（幂等，可重复执行）。
     * <p>只移除**尚未被自动拉黑接管**的条目；一旦已被接管，就交给"解禁 / 永久拉黑"生命周期，不再干预。
     */
    public static void sweepReleasedDefaults() {
        try {
            boolean changed = false;
            for (Map.Entry<String, String[]> entry : RELEASED_DEFAULTS.entrySet()) {
                String[] keys = entry.getKey().split("\\|", 2);
                if (keys.length < 2) {
                    continue;
                }
                SelectModelField field = findTaskListField(keys[0], keys[1]);
                if (field == null || field.getValue() == null) {
                    continue;
                }
                for (String task : entry.getValue()) {
                    if (field.getValue().contains(task) && !isAutoBlackTracked(keys[0], keys[1], task)) {
                        field.remove(task);
                        changed = true;
                        Log.record("黑名单治理🧹[" + task + "]不再预置拉黑，改由自动拉黑机制判定");
                    }
                }
            }
            if (changed) {
                ConfigV2.save(UserIdMap.getCurrentUid(), false);
            }
        } catch (Throwable t) {
            Log.err(TAG, "sweepReleasedDefaults err:", t);
        }
    }

    /**
     * 该任务是否已在自动拉黑记录中（观察期 / 已拉黑 / 已解禁待重试 / 永久拉黑）。
     * <p>供各模块把"技术性不可自动化"的默认项作为**一次性种子**写入：
     * 只在该任务从未进入过自动拉黑生命周期时写一次，之后完全交给
     * "满 N 天自动解禁 / 重试满 N 次永久拉黑" 接管，不再由每日 init 反复补回。
     */
    public static boolean isAutoBlackTracked(String module, String listTitle, String taskTitle) {
        try {
            AutoBlackListMap.ensureLoaded();
            return AutoBlackListMap.get(autoBlackKey(module, listTitle, taskTitle)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static SelectModelField findTaskListField(String module, String listTitle) {
        try {
            ModelFields modelFields = ConfigV2.INSTANCE.getModelFieldsMap().get(module);
            return modelFields == null ? null : (SelectModelField) modelFields.get(listTitle);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String autoBlackKey(String module, String listTitle, String taskTitle) {
        return module + "|" + listTitle + "|" + taskTitle;
    }

    /** 当前天序号，用于按天比较 */
    private static long todayIndex() {
        return System.currentTimeMillis() / 86400000L;
    }

    private static void recordAutoBlack(String module, String listTitle, String taskTitle) {
        try {
            String key = autoBlackKey(module, listTitle, taskTitle);
            AutoBlackRecord old = AutoBlackRecord.parse(AutoBlackListMap.get(key));
            AutoBlackRecord record = new AutoBlackRecord();
            record.hits = 0;
            record.lastDay = todayIndex();
            record.retry = old == null ? 0 : old.retry;
            if (record.retry >= BLACKLIST_MAX_RETRY) {
                // 解禁重试满 BLACKLIST_MAX_RETRY 次仍失败：永久拉黑，不再自动解禁
                record.blackDay = AutoBlackRecord.PERMANENT;
                Log.record("自动拉黑🔒[" + taskTitle + "]解禁重试" + record.retry + "次仍失败，永久拉黑");
            } else {
                record.blackDay = todayIndex();
            }
            AutoBlackListMap.put(key, record.format());
            AutoBlackListMap.save();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 自动拉黑记录：命中次数 / 最后命中天 / 拉黑天 / 解禁重试次数。
     * <p>{@code blackDay}：{@code >0} 已拉黑；{@code 0} 未拉黑（观察中或已解禁待重试）；{@link #PERMANENT} 永久拉黑。
     */
    private static final class AutoBlackRecord {
        private static final long PERMANENT = -1L;
        private int hits;
        private long lastDay;
        private long blackDay;
        private int retry;

        private static AutoBlackRecord parse(String str) {
            if (str == null || str.isEmpty()) {
                return null;
            }
            String[] parts = str.split(";");
            if (parts.length < 3) {
                return null;
            }
            try {
                AutoBlackRecord record = new AutoBlackRecord();
                record.hits = Integer.parseInt(parts[0].trim());
                record.lastDay = Long.parseLong(parts[1].trim());
                record.blackDay = Long.parseLong(parts[2].trim());
                record.retry = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 0;
                return record;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String format() {
            return hits + ";" + lastDay + ";" + blackDay + ";" + retry;
        }
    }

}
