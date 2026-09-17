package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.util.ArrayList;

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
            Log.i(TAG, "newJSONObject err:");
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    public static void printErrorMessage(String tag, JSONObject jo, String errorMessageField) {
        try {
            String errMsg = tag + " error:";
            Log.record(errMsg + jo.getString(errorMessageField));
            Log.i(jo.getString(errorMessageField), jo.toString());
        } catch (Throwable t) {
            Log.i(TAG, "printErrorMessage err:");
            Log.printStackTrace(TAG, t);
        }
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
            Log.i(TAG, "checkMemo err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "checkResultCode err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "checkResultCodeString err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "checkResultCodeInteger err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "checkSuccess err:");
            Log.printStackTrace(TAG, t);
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
            //标记是否加黑
            boolean canAddBlackList = false;

            //共性返回失败关键字
            if (jo.has("desc")) {
                String desc = jo.optString("desc");
                if (desc.contains("不支持rpc调用") || desc.contains("不支持RPC调用")) {
                    canAddBlackList = true;
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
                    if (jo.has("desc")) {
                        String desc = jo.optString("desc");
                        if (desc.contains("任务全局配置不存在")) {
                            // 文案模糊（可能只是活动当天未配置），需连续命中确认
                            MarkTaskBlackListConfirm("AntOrchard", listTitle, "农场肥料任务", taskTitle);
                        }
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
                    if (jo.has("resultView")) {
                        String resultView = jo.optString("resultView");
                        if (resultView.contains("不是有效的入参") || resultView.contains("存在进行中的生活记录")|| resultView.contains("生活记录模板不存在")) {
                            // 文案模糊（可能只是当天状态异常），需连续命中确认
                            MarkTaskBlackListConfirm("AntMember", listTitle, "会员芝麻信用任务芝麻粒", taskTitle);
                        }
                    }
                    break;

            }
        } catch (Throwable t) {
            Log.i(TAG, "checkSuccess err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "MarkTaskBlackListConfirm err:");
            Log.printStackTrace(TAG, t);
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
            Log.i(TAG, "sweepExpiredBlackList err:");
            Log.printStackTrace(TAG, t);
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
