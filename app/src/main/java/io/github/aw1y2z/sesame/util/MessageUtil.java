package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.entity.AutoBlackTaskEntry;
import io.github.aw1y2z.sesame.entity.IdAndName;
import io.github.aw1y2z.sesame.model.task.antMember.AntMember;
import io.github.aw1y2z.sesame.util.idMap.AntDodoTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardDrawTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntSportsTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntStallTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.SesameAlchemyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class MessageUtil {
    private static final String TAG = MessageUtil.class.getSimpleName();
    private static final String UNKNOWN_TAG = "Unknown TAG";

    /**
     * 自动拉黑：模糊错误需连续命中的确认次数。
     *
     * <p>🔴 语义是「**连续 N 天**每天至少失败一次」，不是「累计失败 N 次」——
     * 计数由 {@link #MarkTaskBlackListConfirm} 保证**每天最多 +1**。
     */
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
     * 服务端繁忙 / 限流的文案关键字。
     * <p>⚠️ 实测「人气太旺啦，请稍后再试」同样带 `errorTip=1009`，但它是**限流**不是风控挑战
     * —— 若不在这里拦住，会被 {@link #isRiskControl} 按错误码误判成风控（详见该方法注释）。
     */
    private static final String[] SERVER_BUSY_KEYWORDS = {"服务器正在开小差", "人气太旺"};

    /**
     * 是否服务端繁忙：`resultCode=102` 或命中 {@link #SERVER_BUSY_KEYWORDS} 文案。
     * <p>这类错误是临时性的，既不该拉黑，也不该几秒内连续重试。
     * <p>对调用方的用途：命中后可在"本轮"内放弃后续同类调用（见 {@code AntFarm.listFarmTask} 的领奖）。
     */
    public static boolean isServerBusy(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("102".equals(jo.optString("resultCode", "").trim())) {
            return true;
        }
        for (String keyword : SERVER_BUSY_KEYWORDS) {
            if (anyFieldContains(jo, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务级"不支持/跳过"错误：服务端明确告知该任务类型/参数组合不受支持，属**预期内的跳过**，
     * 不是故障。典型是会员任务遍历上报 `TaskManageFacade.executeTask` 时，支付宝侧对
     * 未开放的任务类型回 `PARAM_ILLEGAL` + `Unsupported bizType and bizSubType`
     * （实测一轮刷 265 条，纯噪音）。
     * <p>⚠️ 只降噪、不改变调用方判定：{@code checkResultCode} 仍返回 false，该跳过的照跳。
     */
    private static final int BIZ_UNSUPPORTED_LOG_LIMIT = 3;
    /** tag → 已打印过的"业务不支持"次数（只增不减，进程重启即清零） */
    private static final Map<String, int[]> BIZ_UNSUPPORTED_LOG_COUNT = new ConcurrentHashMap<>();

    /** 业务级"不支持"的 resultCode。400000040 = 「不支持rpc调用」：任务类接口收到不支持的 taskType 时返回 */
    private static final String[] BIZ_UNSUPPORTED_CODES = {"PARAM_ILLEGAL", "400000040"};

    /** 业务级"不支持"的文案关键字 */
    private static final String[] BIZ_UNSUPPORTED_KEYWORDS = {
            "Unsupported bizType", "bizType and bizSubType", "不支持rpc调用"};

    /**
     * 是否业务级"不支持/跳过"：`resultCode=PARAM_ILLEGAL` 或命中"Unsupported bizType"文案。
     * <p>这类错误重复且无信息量，调用方**不需要**据此拉黑或熔断，只是本次任务不支持。
     */
    public static boolean isBizUnsupported(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        for (String code : BIZ_UNSUPPORTED_CODES) {
            if (code.equals(jo.optString("resultCode", "").trim())) {
                return true;
            }
        }
        for (String keyword : BIZ_UNSUPPORTED_KEYWORDS) {
            if (anyFieldContains(jo, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务级"不支持"是否还应该打印日志：同一 tag 前 {@link #BIZ_UNSUPPORTED_LOG_LIMIT} 次打印，
     * 之后静默（最后一次提示"已静默"，免得看日志的人以为这类错误消失了）。
     */
    private static boolean shouldLogBizUnsupported(String tag) {
        String key = StringUtil.isEmpty(tag) ? UNKNOWN_TAG : tag;
        int printed;
        synchronized (MessageUtil.class) {
            int[] state = BIZ_UNSUPPORTED_LOG_COUNT.computeIfAbsent(key, k -> new int[]{0});
            if (state[0] >= BIZ_UNSUPPORTED_LOG_LIMIT) {
                return false;
            }
            state[0]++;
            printed = state[0];
        }
        if (printed == BIZ_UNSUPPORTED_LOG_LIMIT) {
            Log.record(key + "：本模块「业务不支持(PARAM_ILLEGAL)」已累计" + printed
                    + "次，后续同类不再打印（任务照常跳过，不影响执行）");
        }
        return true;
    }

    /** 风控挑战：错误码所在的字段（各接口字段不统一） */
    private static final String[] RISK_CONTROL_CODE_FIELDS = {"errorTip", "error", "errorCode", "errorNo"};

    /** 风控挑战：服务端文案关键字 */
    private static final String[] RISK_CONTROL_KEYWORDS = {"为了保障您的操作安全", "请进行验证后继续"};

    /**
     * 风控挑战日志降噪：同一模块累计打印满该次数后，后续风控错误不再打印。
     */
    private static final int RISK_CONTROL_LOG_LIMIT = 2;
    /** tag → 已打印过的风控次数（只增不减，进程重启即清零） */
    private static final Map<String, int[]> RISK_CONTROL_LOG_COUNT = new ConcurrentHashMap<>();

    /**
     * 是否命中风控挑战（要求人工安全验证）。
     * <p>服务端原话「为了保障您的操作安全，请进行验证后继续。」，错误码 <b>1009</b>。
     * <p>这类错误**模块无法自动完成** —— 自动过滑块在支付宝 12.12.x 上不可用（启动日志会打印
     * 「目标应用版本[12.12.16.8000]高于[10.6.58.99999]不支持自动过滑块验证」）。
     * <p>识别它的意义在于让调用方**按接口熔断**：命中后当日不再调用该接口。
     * 09-20 的实测教训是运动宝箱接口被风控后仍每 21 秒重试一次、连续空转 101 分钟共 284 次失败。
     */
    public static boolean isRiskControl(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        // ⚠️ 先排除「限流/繁忙」：它们**也**带 errorTip=1009，但不是风控挑战。
        // 09-20 实测会员 signPageTaskList 返回
        // {"error":1009,"errorMessage":"人气太旺啦，请稍后再试","errorTip":"1009"}，
        // 若只按错误码判定就会被当成风控 —— 调用方会据此把接口熔断一整天（误伤）。
        if (isServerBusy(jo)) {
            return false;
        }
        for (String field : RISK_CONTROL_CODE_FIELDS) {
            if ("1009".equals(jo.optString(field, "").trim())) {
                return true;
            }
        }
        for (String keyword : RISK_CONTROL_KEYWORDS) {
            if (anyFieldContains(jo, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 风控挑战（重载：直接吃原始响应字符串，供 RPC 桥在错误路径统一判定）。
     * <p>解析失败按「非风控」处理，避免把网络异常/畸形响应误判成风控。
     */
    public static boolean isRiskControl(String responseStr) {
        if (StringUtil.isEmpty(responseStr)) {
            return false;
        }
        try {
            return isRiskControl(new JSONObject(responseStr));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 是否应该对本次失败做重试（SmartRetryStrategy 分流）：网络/临时类可重试，
     * 风控(保护)类不可重试（自动过滑块不可用，重试只会加重风控）。
     */
    public static boolean shouldRetryAfter(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        return isRetryable(jo) && !isRiskControl(jo);
    }

    /**
     * 风控错误是否还应该打印日志：同一 tag 前 {@link #RISK_CONTROL_LOG_LIMIT} 次打印，
     * 之后静默（最后一次打印时提示「已按接口熔断」，免得看日志的人以为风控消失了）。
     */
    private static boolean shouldLogRiskControl(String tag) {
        String key = StringUtil.isEmpty(tag) ? UNKNOWN_TAG : tag;
        int printed;
        synchronized (MessageUtil.class) {
            int[] state = RISK_CONTROL_LOG_COUNT.computeIfAbsent(key, k -> new int[]{0});
            if (state[0] >= RISK_CONTROL_LOG_LIMIT) {
                return false;
            }
            state[0]++;
            printed = state[0];
        }
        if (printed == RISK_CONTROL_LOG_LIMIT) {
            Log.i(key, "风控🚫本模块风控验证已累计" + printed + "次，后续风控日志不再打印（接口已按当日熔断）");
        }
        return true;
    }

    /**
     * 幂等类「此前已生效」应答：服务端明确表示该动作已经做过（已领取 / 已完结 / 无法重复领取）。
     * <p>这类应答是重复调用撞上既有结果的正常态，不是故障：既不写异常日志，也不落模块分类文件，
     * 否则每轮补做都会刷一条 {@code xxx error:任务已领取} 到「其他记录」。
     */
    public static boolean isAlreadyDone(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        for (String field : FAIL_TEXT_FIELDS) {
            String text = jo.optString(field, "");
            if (text.isEmpty()) {
                continue;
            }
            if (text.contains("已领取") || text.contains("已完结")
                    || text.contains("无法重复领取") || text.contains("重复领取")
                    || text.contains("已经领取") || text.contains("已领完")) {
                return true;
            }
        }
        return false;
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
     * 打印失败应答。三类**日志降噪**（只少写几行日志，不拦截、不退避、不跳过）：
     * <ol>
     *   <li>服务端繁忙 102：同一 tag 累计满 {@link #SERVER_BUSY_LOG_LIMIT} 次后静默；</li>
     *   <li>风控（1009 / 保护文案）：同一 tag 累计满 {@link #RISK_CONTROL_LOG_LIMIT} 次后静默；</li>
     *   <li>业务级「不支持」：同一 tag 累计满 {@link #BIZ_UNSUPPORTED_LOG_LIMIT} 次后静默（本地定制）。</li>
     * </ol>
     * <p>本地定制：失败应答交由 {@link #recordFailure} 按模块 tag 归档到分类文件（上游 v1.2.8 引入）。
     * <p>另：**400000040「不支持rpc调用」**只写异常日志、不记模块错误——它不代表任务做不了
     * （调用方会转另一种实现方案）。
     * <p>原始响应属异常，统一进异常日志（不进运行日志），运行日志只留过程类。
     */
    public static void printErrorMessage(String tag, JSONObject jo, String errorMessageField) {
        try {
            String memo = jo.getString(errorMessageField);
            // 已领取/已完结这类幂等应答不是故障：不打印、不落分类文件
            if (isAlreadyDone(jo)) {
                return;
            }
            if (isUnsupportedRpc(jo)) {
                Log.error(tag + ", " + jo.toString());
                return;
            }
            if (isServerBusy(jo)) {
                if (!shouldLogServerBusy(tag)) {
                    return;
                }
                // 102 高频错误：只打一行 JSON（去掉文案行，避免每次都刷两条）
                Log.error(tag + ", " + jo.toString());
                return;
            }
            if (isRiskControl(jo) && !shouldLogRiskControl(tag)) {
                return;
            }
            // 业务级"不支持"(如 Unsupported bizType)：预期内的跳过，重复且无信息量 → 降噪
            if (isBizUnsupported(jo) && !shouldLogBizUnsupported(tag)) {
                return;
            }
            // 失败应答按模块 tag 归档到分类文件（上游 v1.2.8）；归档器内部会同时写一份运行日志。
            recordFailure(tag, memo);
            // 上游：原始响应属异常，统一进异常日志（不进运行日志），运行日志只留过程类。
            // 本地原先删了这行——但 recordFailure 只写分类文件，不写异常日志，两者不重复。
            Log.error(memo + ", " + jo.toString());
        } catch (Throwable t) {
            Log.err(TAG, "printErrorMessage err:", t);
        }
    }

    /**
     * 失败应答属「结果」，按模块 tag 落到对应分类文件（与 {@code Log.forest/farm/...} 一样会同时写一份运行日志）。
     * <p>未登记的 tag 落到「其他记录」：宁可走兜底分类，也不要让结果停在没有模块归属的运行日志里。
     */
    private static void recordFailure(String tag, String memo) {
        String s = tag + " error:" + memo;
        switch (StringUtil.isEmpty(tag) ? UNKNOWN_TAG : tag) {
            case "AntForestV2":
            case "AntDodo":
            case "ProtectEcology":
            case "WhackMole":
                Log.forest(s);
                break;
            case "AntFarm":
            case "AntStall":
            case "AntOrchard":
                Log.farm(s);
                break;
            case "金豆夺宝":
                Log.goldenBeans(s);
                break;
            default:
                Log.other(s);
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
                    // 服务端繁忙（102）已有 printErrorMessage 统一降噪，这里跳过避免重复刷行
                    if (!isServerBusy(jo)) {
                        Log.i(tag, jo.toString());
                    }
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
     * 网络类临时故障的 `error` 码：**码在各接口间并不统一** —— 庄园/会员回 48、海洋回 46，
     * 文案都是「当前网络不可用，请稍后重试」。用码兜底、用文案主判。
     */
    private static final String[] NETWORK_TRANSIENT_CODES = {"46", "48"};

    /**
     * 网络类临时故障的文案特征。服务端已明说「请稍后重试」，属临时故障，
     * **绝不能判成「任务做不了」**。
     * <p>用例（2026-10-23 实测）：08:48~08:55 手机网络抖动，庄园 33 次 `error:48`、
     * 海洋 `error:46`、会员 `error:48`。由于 {@link #isRetryable} 不认这两个码，
     * 庄园每个任务都被判 {@code Outcome.UNABLE} → 落「今日不再尝试」标记 →
     * 当天后续每轮都是「今日已试[…]＃跳过」，**11 个饲料任务整天废掉**（约 990g）。
     * 网络恢复后也没机会补做，这是"任务突然变少、饲料不够"的真凶。
     */
    private static final String[] NETWORK_TRANSIENT_KEYWORDS = {
            "网络不可用", "网络异常", "网络超时", "请稍后重试"
    };

    /**
     * 是否为**网络类临时故障**（网络不可用/超时，服务端已明说「请稍后重试」）。
     * <p>单独暴露给调用方，是因为它的处置比普通可重试错误更激进：
     * 网络整段不通时，逐个任务去打只会白等超时（庄园一轮能白耗 7 分钟），
     * 调用方可以选择**本轮直接中止**、下一轮再试（见 AntFarm#farmTaskNetworkDown）。
     * <p>⚠️ 码不统一：庄园/会员回 48、海洋回 46，文案都是「当前网络不可用，请稍后重试」。
     */
    public static boolean isNetworkTransient(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        String errCode = jo.optString("error", "").trim();
        if (errCode.isEmpty()) {
            errCode = jo.optString("errorTip", "").trim();
        }
        for (String c : NETWORK_TRANSIENT_CODES) {
            if (c.equals(errCode)) {
                return true;
            }
        }
        for (String keyword : NETWORK_TRANSIENT_KEYWORDS) {
            if (anyFieldContains(jo, keyword)) {
                return true;
            }
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
        // 网络类临时故障优先判定：其错误码不在下面 code 字段里（走的是 error 字段），
        // 漏判的代价是整批任务当天作废，比误判重试严重得多。
        if (isNetworkTransient(jo)) {
            return true;
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
     * 拉黑目标表：listTitle → {ModelFieldsType, 列表中文名}。
     * <p>决定"拉黑写进哪个模块的哪个字段、日志里怎么称呼这个列表"。
     * <p>**新增任务列表只需在这里加一行**：判据绝大多数走默认
     * （desc 字段命中"不支持rpc调用"＝立即拉黑，其它字段命中＝连续确认），
     * 只有少数列表有额外错误码/文案判据时才需要在 {@link #checkResultCodeAndMarkTaskBlackList} 里加分支。
     */
    private static final Map<String, String[]> BLACKLIST_LIST_TARGETS = new LinkedHashMap<>();

    static {
        BLACKLIST_LIST_TARGETS.put("AntForestVitalityTaskList", new String[]{"AntForestV2", "蚂蚁森林活力值任务"});
        BLACKLIST_LIST_TARGETS.put("AntForestHuntTaskList", new String[]{"AntForestV2", "蚂蚁森林抽抽乐任务"});
        BLACKLIST_LIST_TARGETS.put("MonopolyTaskList", new String[]{"AntForestV2", "新版保护地任务"});
        BLACKLIST_LIST_TARGETS.put("AntFarmDoFarmTaskList", new String[]{"AntFarm", "庄园饲料任务"});
        BLACKLIST_LIST_TARGETS.put("AntFarmDrawMachineTaskList", new String[]{"AntFarm", "庄园装扮抽抽乐任务"});
        BLACKLIST_LIST_TARGETS.put("AntKitchenTaskList", new String[]{"AntFarm", "小鸡大厨任务"});
        BLACKLIST_LIST_TARGETS.put("AntDodoTaskList", new String[]{"AntDodo", "神奇物种任务"});
        BLACKLIST_LIST_TARGETS.put("AntOceanAntiepTaskList", new String[]{"AntOcean", "神奇海洋普通任务"});
        BLACKLIST_LIST_TARGETS.put("AntOceanFishBlackList", new String[]{"AntOcean", "神奇海洋去摸鱼任务"});
        BLACKLIST_LIST_TARGETS.put("AntOrchardTaskList", new String[]{"AntOrchard", "农场肥料任务"});
        BLACKLIST_LIST_TARGETS.put("AntOrchardDrawTaskList", new String[]{"AntOrchard", "抽抽乐任务"});
        // 上游新增：网商银行福利金任务
        // （本地的 OrchardChouChouLeTaskList 已废弃、类已删除，不再登记）
        BLACKLIST_LIST_TARGETS.put("WelfareFundTaskList", new String[]{"AntMember", "福利金任务"});
        BLACKLIST_LIST_TARGETS.put("GoldTicketTaskList", new String[]{"AntMember", "黄金票任务"});
        BLACKLIST_LIST_TARGETS.put("GoldenBeansTaskList", new String[]{"goldenbeans", "金豆夺宝任务"});
        BLACKLIST_LIST_TARGETS.put("AntStallTaskList", new String[]{"AntStall", "新村任务"});
        BLACKLIST_LIST_TARGETS.put("AntSportsTaskList", new String[]{"AntSports", "运动任务"});
        BLACKLIST_LIST_TARGETS.put("AntMemberTaskList", new String[]{"AntMember", "会员任务"});
        BLACKLIST_LIST_TARGETS.put("AntMemberAdTaskList", new String[]{"AntMember", "会员广告任务"});
        BLACKLIST_LIST_TARGETS.put("MemberCreditSesameTaskList", new String[]{"AntMember", "会员芝麻信用任务芝麻粒"});
        BLACKLIST_LIST_TARGETS.put("SesameAlchemyTaskList", new String[]{"AntMember", "芝麻炼金任务"});
    }

    /**
     * 黑名单界面的**一级分组**名（模块）：键是字段码。
     * <p>层级照抄「扩展功能」页：一级 = 模块（蚂蚁森林 / 神奇海洋…），
     * 它下面那张卡片里的**每一行 = 该模块下的一个黑名单列表**（活力值黑名单 / 抽抽乐黑名单…），
     * 点进去才是那个列表里的具体任务。
     */
    private static final Map<String, String> BLACKLIST_GROUP_NAMES = new LinkedHashMap<>();

    /** 一级分组下的**行标题**：该黑名单列表的显示名。模块名已在分组标题上，这里不再重复。 */
    private static final Map<String, String> BLACKLIST_LIST_NAMES = new LinkedHashMap<>();

    /**
     * 一级分组的固定展示顺序（未列出的分组排在最后）。
     * <p>不按名称排序：中文按码点排出来的顺序对用户没有意义。
     */
    private static final List<String> BLACKLIST_GROUP_ORDER = Arrays.asList(
            "蚂蚁森林", "神奇海洋", "芭芭农场", "庄园", "金豆",
            "神奇物种", "新村", "运动", "会员");

    static {
        // 蚂蚁森林（AntForestV2）：活力值 / 抽抽乐 / 保护地
        BLACKLIST_GROUP_NAMES.put("AntForestVitalityTaskList", "蚂蚁森林");
        BLACKLIST_LIST_NAMES.put("AntForestVitalityTaskList", "活力值黑名单");
        BLACKLIST_GROUP_NAMES.put("AntForestHuntTaskList", "蚂蚁森林");
        BLACKLIST_LIST_NAMES.put("AntForestHuntTaskList", "抽抽乐黑名单");
        BLACKLIST_GROUP_NAMES.put("MonopolyTaskList", "蚂蚁森林");
        BLACKLIST_LIST_NAMES.put("MonopolyTaskList", "新版保护地黑名单");
        // 神奇海洋（AntOcean）
        BLACKLIST_GROUP_NAMES.put("AntOceanAntiepTaskList", "神奇海洋");
        BLACKLIST_LIST_NAMES.put("AntOceanAntiepTaskList", "普通任务黑名单");
        BLACKLIST_GROUP_NAMES.put("AntOceanFishBlackList", "神奇海洋");
        BLACKLIST_LIST_NAMES.put("AntOceanFishBlackList", "去摸鱼黑名单");
        // 芭芭农场（AntOrchard）：肥料 + 金豆夺宝专场（专场入口在农场内，同属 AntOrchard）
        BLACKLIST_GROUP_NAMES.put("AntOrchardTaskList", "芭芭农场");
        BLACKLIST_LIST_NAMES.put("AntOrchardTaskList", "农场肥料黑名单");
        BLACKLIST_GROUP_NAMES.put("AntOrchardDrawTaskList", "芭芭农场");
        BLACKLIST_LIST_NAMES.put("AntOrchardDrawTaskList", "抽抽乐黑名单");
        // 庄园（AntFarm）
        BLACKLIST_GROUP_NAMES.put("AntFarmDoFarmTaskList", "庄园");
        BLACKLIST_LIST_NAMES.put("AntFarmDoFarmTaskList", "饲料黑名单");
        BLACKLIST_GROUP_NAMES.put("AntFarmDrawMachineTaskList", "庄园");
        BLACKLIST_LIST_NAMES.put("AntFarmDrawMachineTaskList", "装扮抽抽乐黑名单");
        // 金豆（独立小程序 goldenbeans）
        BLACKLIST_GROUP_NAMES.put("GoldenBeansTaskList", "金豆");
        BLACKLIST_LIST_NAMES.put("GoldenBeansTaskList", "夺宝黑名单");
        // 其余模块各自成一组（当前设备上暂无记录，先备好，免得将来退化成"字段码当标题"）
        BLACKLIST_GROUP_NAMES.put("AntDodoTaskList", "神奇物种");
        BLACKLIST_LIST_NAMES.put("AntDodoTaskList", "物种任务黑名单");
        BLACKLIST_GROUP_NAMES.put("AntStallTaskList", "新村");
        BLACKLIST_LIST_NAMES.put("AntStallTaskList", "新村任务黑名单");
        BLACKLIST_GROUP_NAMES.put("AntSportsTaskList", "运动");
        BLACKLIST_LIST_NAMES.put("AntSportsTaskList", "运动任务黑名单");
        BLACKLIST_GROUP_NAMES.put("AntMemberTaskList", "会员");
        BLACKLIST_LIST_NAMES.put("AntMemberTaskList", "会员任务黑名单");
        BLACKLIST_GROUP_NAMES.put("MemberCreditSesameTaskList", "会员");
        BLACKLIST_LIST_NAMES.put("MemberCreditSesameTaskList", "芝麻信用黑名单");
        BLACKLIST_GROUP_NAMES.put("SesameAlchemyTaskList", "会员");
        BLACKLIST_LIST_NAMES.put("SesameAlchemyTaskList", "芝麻炼金黑名单");
    }

    /**
     * **已废弃的黑名单列表**：字段/开关/写入点都随上游改版一起删掉了，但设备上可能残留历史数据。
     *
     * <p>为什么单独列一张表、而不是给它们补个中文名挂在界面上：
     * <ul>
     *   <li>它们**没有对应的字段**了 —— {@code findTaskListField} 恒返回 null，
     *       明细页按 fieldCode 过滤也点不进去（一级页会把它和同名列表并成一行，
     *       点进去只显示另一个列表的任务，**行数与明细对不上**）；</li>
     *   <li>残留记录只剩"观察期"垃圾（{@code blackDay=0}），没有任何状态语义可展示。</li>
     * </ul>
     * 所以正确做法是**在界面上直接不显示**（本表用于 {@code listAutoBlackEntries} 跳过），
     * 并由 {@link #sweepObsoleteBlackLists()} 在跨天时把纯垃圾清掉。
     *
     * <p><b>来源（2026-10-05 查实）</b>：本地把「阿肥寻宝记」合并进了「金豆夺宝专场」
     * （{@code AntOrchardDrawTaskList}），旧字段/开关整体移除；但上游旧版曾把**中文任务名**
     * 当键写进这个列表，历史数据就留在了 {@code AutoBlackList.json} 里，
     * 界面上就冒出一组英文的「OrchardChouChouLeTaskList」。
     */
    private static final Set<String> OBSOLETE_BLACKLIST_LISTS = new LinkedHashSet<>();

    static {
        // 阿肥寻宝记/农场抽抽乐 → 已并入「芭芭农场 · 金豆夺宝专场（抽抽乐）」
        OBSOLETE_BLACKLIST_LISTS.add("OrchardChouChouLeTaskList");
    }

    /**
     * 清理**已废弃列表的历史残留**（幂等，每次跨天跑一次）。
     *
     * <p>只清「模块自动拉的、仍停在观察期（{@code blackDay=0}、{@code retry=0}）」那种纯垃圾；
     * 已被正式拉黑或已进入解禁重试周期的记录**一律保留** —— 它们有状态语义，
     * 顺手删掉等于偷偷把任务放出来，那是改行为，不是清理。
     *
     * <p>顺带把**无法解析**的记录（{@code parse} 返回 null）也清掉：它们永远不会被消费，
     * 却会让黑名单页多出一条读不出状态的行。
     */
    public static void sweepObsoleteBlackLists() {
        try {
            AutoBlackListMap.ensureLoaded();
            if (AutoBlackListMap.getMap().isEmpty()) {
                return;
            }
            boolean changed = false;
            for (String key : new ArrayList<>(AutoBlackListMap.keys())) {
                AutoBlackRecord record = AutoBlackRecord.parse(AutoBlackListMap.get(key));
                String[] parts = key.split("\\|", 3);
                String fieldCode = parts.length > 1 ? parts[1] : "";
                // ① 解析不出来的记录：留着只会在界面上多一条无状态的行
                if (record == null) {
                    AutoBlackListMap.remove(key);
                    changed = true;
                    Log.record("黑名单治理🧹清理无法解析的拉黑记录[" + key + "]");
                    continue;
                }
                // ② 废弃列表的观察期残留：列表本身已不存在，这条记录永远不会再被消费
                if (OBSOLETE_BLACKLIST_LISTS.contains(fieldCode)
                        && record.blackDay == 0L && record.retry == 0) {
                    AutoBlackListMap.remove(key);
                    changed = true;
                    Log.record("黑名单治理🧹清理废弃列表残留[" + parts[parts.length - 1] + "]原列表["
                            + fieldCode + "]已废弃");
                }
            }
            if (changed) {
                AutoBlackListMap.save();
            }
        } catch (Throwable t) {
            Log.err(TAG, "sweepObsoleteBlackLists err:", t);
        }
    }

    /**
     * 字段码 → 该字段的「任务键 → 中文名」映射文件。
     *
     * <p>给 {@link #resolveTaskName} 当**兜底数据源**：正规路径是拿字段的候选项列表
     * （{@code SelectModelField.getExpandValue()}），但那要求 {@code ConfigV2} 的字段表已经灌好 ——
     * 而字段表要等 {@code ConfigPreload.prepare()}，黑名单页从主界面直接进来时可能是空的。
     * 那种情况下，键本身就是英文稳定键的列表（金豆、金豆夺宝专场）会整片显示英文。
     *
     * <p>⚠️ 同上，**废弃列表也必须登记**：否则键退化成英文（见 {@link #OBSOLETE_BLACKLIST_LISTS}）。
     *
     * <h3>🔴 为什么要连 load 一起登记（2026-10-05）</h3>
     * 这些 idMap 的 {@code getMap()} 只是**内存只读视图**，磁盘内容要靠各模块初始化时显式
     * {@code load()} 才进来。黑名单页可以从主界面直接进 —— 那时模块还没跑过任何任务，
     * 映射全是空的，于是**明明磁盘上的 {@code GoldenBeansTask.json} 有「眼力拼图通过1关」，
     * 界面照样显示 {@code GOLDENBEAN_GAME_ZH_YLPT}**。只登记 getter 修不了这个洞，
     * 必须在取值前先把 load 兜住，所以这里存的是「加载器 + 取值器」一对。
     */
    private static final Map<String, Supplier<Map<String, String>>> BLACKLIST_NAME_MAPS = new LinkedHashMap<>();

    /** 同上，字段码 → 该映射的**加载器**（在 {@link #resolveTaskName} 取值前调用，幂等） */
    private static final Map<String, Runnable> BLACKLIST_NAME_LOADERS = new LinkedHashMap<>();

    /** 登记一个「字段码 → 中文名映射」：加载器与取值器必须成对登记 */
    private static void registerNameMap(String fieldCode, Runnable loader,
                                        Supplier<Map<String, String>> getter) {
        BLACKLIST_NAME_LOADERS.put(fieldCode, loader);
        BLACKLIST_NAME_MAPS.put(fieldCode, getter);
    }

    static {
        registerNameMap("AntForestVitalityTaskList",
                () -> AntForestVitalityTaskListMap.load(), AntForestVitalityTaskListMap::getMap);
        registerNameMap("AntForestHuntTaskList",
                () -> AntForestHuntTaskListMap.load(), AntForestHuntTaskListMap::getMap);
        registerNameMap("MonopolyTaskList",
                () -> MonopolyTaskListMap.load(), MonopolyTaskListMap::getMap);
        registerNameMap("AntOceanAntiepTaskList",
                () -> AntOceanAntiepTaskListMap.load(), AntOceanAntiepTaskListMap::getMap);
        registerNameMap("AntOceanFishBlackList",
                () -> AntOceanFishBlackListMap.load(), AntOceanFishBlackListMap::getMap);
        registerNameMap("AntOrchardTaskList",
                () -> AntOrchardTaskListMap.load(), AntOrchardTaskListMap::getMap);
        registerNameMap("AntOrchardDrawTaskList",
                () -> AntOrchardDrawTaskListMap.load(), AntOrchardDrawTaskListMap::getMap);
        registerNameMap("AntFarmDoFarmTaskList",
                () -> AntFarmDoFarmTaskListMap.load(), AntFarmDoFarmTaskListMap::getMap);
        registerNameMap("AntFarmDrawMachineTaskList",
                () -> AntFarmDrawMachineTaskListMap.load(), AntFarmDrawMachineTaskListMap::getMap);
        registerNameMap("GoldenBeansTaskList",
                () -> GoldenBeansTaskListMap.load(), GoldenBeansTaskListMap::getMap);
        registerNameMap("AntDodoTaskList",
                () -> AntDodoTaskListMap.load(), AntDodoTaskListMap::getMap);
        registerNameMap("AntStallTaskList",
                () -> AntStallTaskListMap.load(), AntStallTaskListMap::getMap);
        registerNameMap("AntSportsTaskList",
                () -> AntSportsTaskListMap.load(), AntSportsTaskListMap::getMap);
        registerNameMap("AntMemberTaskList",
                () -> AntMemberTaskListMap.load(), AntMemberTaskListMap::getMap);
        registerNameMap("MemberCreditSesameTaskList",
                () -> MemberCreditSesameTaskListMap.load(), MemberCreditSesameTaskListMap::getMap);
        registerNameMap("SesameAlchemyTaskList",
                () -> SesameAlchemyTaskListMap.load(), SesameAlchemyTaskListMap::getMap);
    }

    /**
     * 各模块的**预置黑名单登记表**（键 {@code module|listTitle}）：这是"默认项"的**单一真相**——
     * 模块 init 不再自带一份，而是取 {@link #presetBlackList} 后交给 {@link #syncTaskBlackList}。
     * <p>为什么必须登记在这里：配置页在**独立进程**里跑，只能读到落盘/代码里的东西；
     * 预置项若留在各模块 init 的局部变量里，配置页就无法把它标成"默认"。
     */
    private static final Map<String, Set<String>> PRESET_BLACKLIST = new LinkedHashMap<>();

    static {
        PRESET_BLACKLIST.put("AntFarm|AntFarmDoFarmTaskList", setOf(
                "线上支付", "用花呗完成一笔支付"));
        // 2026-10-12：「消耗饲料换机会」不再预置拉黑。它只是消耗饲料换抽奖机会，
        // 属可自动化的兑换类（走 AntFarm 的 EXCHANGE 分支，见 doFarmDrawTask），
        // 上游原先按「需消耗资源」一刀切拉黑，导致用户手动取消后每日 init 又被补回。
        PRESET_BLACKLIST.put("AntFarm|AntFarmDrawMachineTaskList", setOf(
                // 上游 55579ce8 修正了这条「从未生效的预置项」：服务端实际下发的标题是空格版，
                // 逗号版从来匹配不上。本地 2026-10-12 已把「消耗饲料换机会」移出预置（见上方注释），
                // 故此处只保留修正后的标题。
                "伸出援手 点亮希望"));
        PRESET_BLACKLIST.put("AntOrchard|AntOrchardTaskList", setOf(
                "完成1笔旧衣回收", "完成1单手机数码回收"));
        // 芝麻粒：仍预置拉黑的只剩真实交易/履约类（下单/租赁/订酒店/回收/雇佣/付钱/查车）
        PRESET_BLACKLIST.put("AntMember|MemberCreditSesameTaskList", setOf(
                "用额度免押金下单", "去租赁下单", "芝麻租赁下单得芝麻粒", "去飞猪订酒店",
                "0.1元起租会员攒粒", "9.9元抢租3天大疆", "1分起囤神券茶咖美食", "完成旧衣回收得现金",
                "去雇佣芝麻大表鸽", "送你10.6元支付红包", "一键查询爱车估值"));
        PRESET_BLACKLIST.put("goldenbeans|GoldenBeansTaskList", setOf(
                "GOLDEN_BEAN_TASK_XIANSHANGZHIFU", "GOLDEN_BEAN_TASK_YUEBAO", "TEST_PUSH_SUBSCRIBE"));
        // 跳外部 App 的"真行为"任务：模块无法完成，且提交时服务端直接回 1009 风控
        // （1009 会连累全部模块，只读查询也被拦），故直接预置拉黑
        PRESET_BLACKLIST.put("AntMember|GoldTicketTaskList", setOf(
                "为自己攒笔钱", "去体验余额宝自动攒", "设置股票快捷入口"));
    }

    private static Set<String> setOf(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    /**
     * 取该列表的预置黑名单（**可变副本**）：模块 init 可在此基础上追加自己的项，再交给
     * {@link #syncTaskBlackList}。未登记的列表返回空集。
     */
    public static Set<String> presetBlackList(String module, String listTitle) {
        Set<String> preset = PRESET_BLACKLIST.get(module + "|" + listTitle);
        return preset == null ? new LinkedHashSet<>() : new LinkedHashSet<>(preset);
    }

    /** 该条目是否来自模块预置黑名单（配置页据此标注"默认"）。 */
    public static boolean isPresetBlackListItem(String module, String listTitle, String taskTitle) {
        Set<String> preset = PRESET_BLACKLIST.get(module + "|" + listTitle);
        return preset != null && preset.contains(taskTitle);
    }

    /**
     * 按列表字段名反查拉黑目标 {模块名, 列表中文名}；未登记的列表返回 null。
     * <p>供模块外的通用逻辑（如 {@code TaskAttemptPolicy}）在判定"该任务做不了"时按规则记账。
     */
    public static String[] autoBlackListTarget(String listField) {
        return BLACKLIST_LIST_TARGETS.get(listField);
    }

    /**
     * 各任务列表的白名单：键为 listTitle（字段名，与 {@link #BLACKLIST_LIST_TARGETS} 一致），
     * 值为不应被「自动拉黑」机制添加的任务标题集合。
     * <p>只用于拦截自动拉黑入口（{@link #MarkTaskBlackList}），<b>绝不删除用户手动加入黑名单的条目</b>。
     * 由各模块的 {@link #syncTaskBlackList} 在初始化时登记。
     */
    private static final Map<String, Set<String>> TASK_WHITE_LIST = new HashMap<>();

    /** 400000040「不支持rpc调用」；它不等于任务做不了，见另一种实现方案。 */
    public static final String CODE_UNSUPPORTED_RPC = "400000040";

    /** 失败响应是否命中"接口不支持调用"（按错误码，不按文案）。 */
    public static boolean isUnsupportedRpc(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        return CODE_UNSUPPORTED_RPC.equals(jo.optString("code", "").trim())
                || CODE_UNSUPPORTED_RPC.equals(jo.optString("errorCode", "").trim());
    }

    /**
     * 延迟拉黑缓冲：非 null 时，本线程的自动拉黑先入缓冲，由调用方在**任务列表核对之后**决定落盘/丢弃
     * （列表确认已完成则不该拉黑）。供 {@link io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy} 使用。
     */
    private static final ThreadLocal<List<Runnable>> DEFER_BLACKLIST = new ThreadLocal<>();

    /** 开始捕获本线程的自动拉黑（缓冲已存在时不覆盖，避免嵌套时丢失外层）。 */
    public static void beginDeferBlackList() {
        if (DEFER_BLACKLIST.get() == null) {
            DEFER_BLACKLIST.set(new ArrayList<>());
        }
    }

    /** 结束捕获：{@code commit=true} 落盘缓冲中的拉黑，否则丢弃；无论如何都清空本线程缓冲。 */
    public static void endDeferBlackList(boolean commit) {
        List<Runnable> pending = DEFER_BLACKLIST.get();
        DEFER_BLACKLIST.remove();
        if (pending == null || !commit) {
            return;
        }
        for (Runnable action : pending) {
            try {
                action.run();
            } catch (Throwable t) {
                Log.err(TAG, "endDeferBlackList err:", t);
            }
        }
    }

    /** 处于延迟模式则把动作入缓冲并返回 true；否则返回 false（调用方应立即执行）。 */
    private static boolean deferBlackList(Runnable action) {
        List<Runnable> pending = DEFER_BLACKLIST.get();
        if (pending == null) {
            return false;
        }
        pending.add(action);
        return true;
    }

    public static void checkResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle, JSONObject jo) {
        // 开启延迟拉黑时先入缓冲：只有任务列表未确认完成时才真正落盘
        if (deferBlackList(() -> doCheckResultCodeAndMarkTaskBlackList(listTitle, taskTitle, null, jo))) {
            return;
        }
        doCheckResultCodeAndMarkTaskBlackList(listTitle, taskTitle, null, jo);
    }

    /**
     * 同上，额外带一个**展示名**（该任务当时的中文标题），只影响界面显示 ——
     * 语义见 {@link #MarkTaskBlackList} 的五参重载。
     */
    public static void checkResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle,
                                                          String displayName, JSONObject jo) {
        // 本地定制：带展示名的重载同样要经过延迟拉黑缓冲；
        // 缓冲内容统一落到下面那个 4 参 do* 实现（展示名只影响界面，不改判定）。
        if (deferBlackList(() -> doCheckResultCodeAndMarkTaskBlackList(listTitle, taskTitle, displayName, jo))) {
            return;
        }
        doCheckResultCodeAndMarkTaskBlackList(listTitle, taskTitle, displayName, jo);
    }

    private static void doCheckResultCodeAndMarkTaskBlackList(String listTitle, String taskTitle,
                                                              String displayName, JSONObject jo) {
        try {
            if (jo == null) {
                Log.i(listTitle, "JSON对象为空");
                return;
            }
            // 可重试错误（限流、远端异常、网络抖动）与服务端繁忙（102 / "服务器正在开小差"）一律不拉黑，
            // 对全部任务列生效。注意：isRetryable 不含 102，而 102 属临时故障（见 isServerBusy），
            // 若只判 isRetryable 会让 102 漏过守卫被误拉黑，故此处与调用方一致用 isRetryable || isServerBusy
            if (isRetryable(jo) || isServerBusy(jo)) {
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

            String[] target = BLACKLIST_LIST_TARGETS.get(listTitle);
            if (target == null) {
                // 未登记的列表不做拉黑（原先 switch 无匹配分支时也是什么都不做）
                Log.i(listTitle, "未登记拉黑目标，跳过");
                return;
            }

            // 判据：desc 命中"不支持rpc调用"＝立即拉黑；其它字段命中＝只作为"连续命中确认"的依据
            // （字段不统一，放宽判定范围必须更保守，避免一次误判就把任务停掉 3 天）
            // 例外：400000040（同一文案的规范化错误码）**不等于任务做不了**——另一种实现方案（doFarmTask）
            // 能做成，且响应可能撒谎（回 102 但已生效）。
            // 据它立即拉黑会把能做的任务永久拉黑 ⇒ 降级为"连续确认"，给另一种实现方案与列表核对留出机会
            boolean unsupportedRpc = isUnsupportedRpc(jo);
            boolean canAddBlackList = strongHit && !unsupportedRpc;
            boolean needConfirm = (weakHit && !strongHit) || unsupportedRpc;
            // 是否走「永久拉黑」（一次即黑、永不自动解禁）：仅用于**确定性失败**
            // —— 交易/支付类，以及服务端固定拒绝（见 SesameAlchemyTaskList 分支）。
            boolean permanentAdd = false;
            // 少数列表有自己的额外判据（服务端错误码、特有文案），其余列表走上面的默认判据
            switch (listTitle) {
                // 运动任务：错误码/文案指明任务 id 非法时可直接拉黑
                case "AntSportsTaskList":
                    if (jo.has("errorCode") && jo.optString("errorCode").contains("TASK_ID_INVALID")) {
                        // {"ariverRpcTraceId":"...","errorCode":"TASK_ID_INVALID","errorMsg":"海豚任务id非法","retryable":false,"success":false}
                        canAddBlackList = true;
                    }
                    if (jo.has("errorMsg") && jo.optString("errorMsg").contains("海豚活动触发不可重试错误")) {
                        canAddBlackList = true;
                    }
                    needConfirm = weakHit;
                    break;

                // 农场肥料任务：文案模糊（可能只是活动当天未配置）也算待确认
                case "AntOrchardTaskList":
                    needConfirm = weakHit || anyFieldContains(jo, "任务全局配置不存在");
                    break;

                // 会员芝麻信用任务：另有三种模糊文案同样走连续确认
                case "MemberCreditSesameTaskList":
                    needConfirm = weakHit
                            || anyFieldContains(jo, "不是有效的入参")
                            || anyFieldContains(jo, "存在进行中的生活记录")
                            || anyFieldContains(jo, "生活记录模板不存在");
                    break;

                // 芝麻炼金任务：「参数[promiseActivityExtCheck]不是有效的入参」是服务端对
                // 「需真实行为校验」类任务（zm_* / 玩XX游戏）的**确定性固定拒绝**。
                // 与"临时故障"性质不同：无论试多少次都不可能成功，且服务端还会换任务名变体
                // （实测：「玩寻道大千」→「玩寻道大千砍伐10次」、「玩青云诀之伏魔」→「…完成任务1次」），
                // 而拉黑是**按任务名精确匹配** ⇒ 走"3 天确认 + 满 3 天解禁"的循环永远追不上，
                // 旧名刚拉黑、新变体又要重刷 3 天，天天刷错。
                // 🔴 2026-10-17（老板确认 A 方案）：改为**一次即永久拉黑**，不进解禁重试生命周期。
                // 仍可人工恢复：在黑名单页取消勾选后任务会重新尝试，成功则不再被拉黑。
                // 其余模糊错误（非固定拒绝）保留连续确认，避免一次误判就把任务永久停掉。
                case "SesameAlchemyTaskList":
                    if (anyFieldContains(jo, "不是有效的入参")
                            || anyFieldContains(jo, "promiseActivityExtCheck")) {
                        canAddBlackList = true;
                        permanentAdd = true;
                    } else {
                        needConfirm = weakHit;
                    }
                    break;

                // 会员广告任务：失败响应无统一文案，也无另一种实现方案可重放，一律按连续确认记账
                case "AntMemberAdTaskList":
                    needConfirm = true;
                    break;

                // 福利金任务：事件规则任务被 10000005「不允许完成事件规则任务」拒绝，文案里没有
                // 「不支持rpc调用」关键字，须按错误码单独接入连续确认，不能靠 strongHit
                case "WelfareFundTaskList":
                    needConfirm = weakHit
                            || "10000005".equals(jo.optString("errorCode", "").trim())
                            || anyFieldContains(jo, "不允许完成事件规则任务");
                    break;

                // 金豆夺宝任务：错误码/文案（code 或 resultCode 或 errorCode + desc/resultDesc/memo）
                // 明确不可恢复时可直接拉黑（可重试错误已在入口统一拦截）
                case "GoldenBeansTaskList": {
                    String code = jo.optString("code", "").trim();
                    if (code.isEmpty()) {
                        code = jo.optString("resultCode", "").trim();
                    }
                    if (code.isEmpty()) {
                        code = jo.optString("errorCode", "").trim();
                    }
                    String message = jo.optString("desc", "");
                    if (message.isEmpty()) {
                        message = jo.optString("resultDesc", "");
                    }
                    if (message.isEmpty()) {
                        message = jo.optString("memo", "");
                    }
                    // 任务Id非法、入参非法等不可恢复错误码可直接拉黑；400000040「不支持rpc调用」不在此列
                    // （金豆已有另一种实现方案 doFarmTask + 列表核对，见 GoldenBeansTasks），降级为连续确认
                    boolean invalid = code.contains("20020012")
                            || code.contains("TASK_ID_INVALID")
                            || code.contains("ILLEGAL_ARGUMENT");
                    if (invalid) {
                        canAddBlackList = true;
                    }
                    needConfirm = needConfirm || message.contains("任务全局配置不存在");
                    break;
                }

                default:
                    // 其余列表（森林活力值/抽抽乐、庄园饲料/装扮抽抽乐、物种、海洋普通/摸鱼、新村、会员）
                    // 都只用默认判据，无需分支
                    break;
            }

            if (canAddBlackList) {
                if (permanentAdd) {
                    // 确定性失败（交易/支付类、服务端固定拒绝）：一次即永久，
                    // 不进"满 3 天解禁重试"的生命周期（否则要绕 2 轮、约 9 天）
                    MarkTaskBlackListPermanent(target[0], listTitle, target[1], taskTitle);
                } else {
                    MarkTaskBlackList(target[0], listTitle, target[1], taskTitle, displayName);
                }
            } else if (needConfirm) {
                MarkTaskBlackListConfirm(target[0], listTitle, target[1], taskTitle, displayName);
            }
        } catch (Throwable t) {
            Log.err(TAG, "checkSuccess err:", t);
        }
    }

    public static void MarkTaskBlackList(String ModelFieldsType, String listTitle, String TaskListName, String taskTitle) {
        // 延迟拉黑：处于捕获模式时先入缓冲，由调用方在列表核对后决定落盘/丢弃
        if (deferBlackList(() -> doMarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, null, false))) {
            return;
        }
        doMarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, null, false);
    }

    /**
     * 记一条自动拉黑（带展示名版本）。
     *
     * @param displayName 该任务**当时的中文标题**，只供界面显示：黑名单页会优先用它，
     *                    免得再去查"任务键 → 中文名"的映射 —— 有些列表按稳定键拉黑，
     *                    键本身就是英文（如 {@code GOLDENBEAN_BATCH_DRAW_GAME_ZH_KXXXL}），
     *                    映射里又没有，界面上就只能显示英文键。
     *                    传 {@code null} 时与旧版行为完全一致。
     */
    public static void MarkTaskBlackList(String ModelFieldsType, String listTitle, String TaskListName,
                                         String taskTitle, String displayName) {
        // 本地定制：带展示名的重载同样要经过延迟拉黑缓冲；
        // 展示名只影响界面显示，落盘时机与 4 参版完全一致。
        if (deferBlackList(() -> doMarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, displayName, false))) {
            return;
        }
        doMarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, displayName, false);
    }

    /**
     * 【底线】**确定性失败**的任务：立即永久拉黑、永不自动解禁。适用两类：
     * <ol>
     *   <li><b>交易/支付类</b>：只能靠真实交易完成，伪申报会被服务端判风险操作（1009 风控）；</li>
     *   <li><b>服务端固定拒绝</b>（{@code promiseActivityExtCheck 不是有效的入参} 等）：
     *       无论试多少次都不可能成功，且服务端会换任务名变体、拉黑按名精确匹配
     *       ⇒ 走"3 天确认 + 满 3 天解禁"永远追不上（2026-10-17 起，芝麻炼金走这条）。</li>
     * </ol>
     *
     * <p>必须一次拦死：不能写 {@code blackDay=今天}——那会进入"满 {@link #BLACKLIST_RETRY_DAYS} 天自动解禁重试"的
     * 生命周期（要循环 {@link #BLACKLIST_MAX_RETRY} 次、约 9 天才变永久），期间任务会反复回到待办；
     * 这里直接写 {@link AutoBlackRecord#PERMANENT}，{@link #sweepExpiredBlackList} 永不解禁。
     *
     * <p>**不走延迟拉黑缓冲**：缓冲的用途是"等任务列表核对后再决定是否拉黑"，
     * 而这两类都没有可核对的成功路径（绝不允许被伪申报做成）。
     */
    public static void MarkTaskBlackListPermanent(String ModelFieldsType, String listTitle, String TaskListName,
                                                  String taskTitle) {
        doMarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, null, true);
    }

    private static void doMarkTaskBlackList(String ModelFieldsType, String listTitle, String TaskListName,
                                            String taskTitle, String displayName, boolean permanent) {
        ConfigV2 config = ConfigV2.INSTANCE;
        ModelFields TaskModelFields = config.getModelFieldsMap().get(ModelFieldsType);
        if (TaskModelFields == null) {
            // BLACKLIST_LIST_TARGETS 的第一项必须是模型类名，写错时这里要能看到原因而不是 NPE
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle + "#未找到模块[" + ModelFieldsType + "]");
            return;
        }
        SelectModelField TaskSelectModelField = (SelectModelField) TaskModelFields.get(listTitle);
        if (TaskSelectModelField == null) {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
            return;
        }
        // 白名单拦截：白名单任务只由"用户手动"决定是否拉黑，自动拉黑机制不插手
        Set<String> white = TASK_WHITE_LIST.get(listTitle);
        if (white != null && white.contains(taskTitle)) {
            Log.record("[" + TaskListName + "]任务[" + taskTitle + "]在白名单中，跳过自动拉黑");
            return;
        }
        if (!TaskSelectModelField.contains(taskTitle)) {
            TaskSelectModelField.add(taskTitle, 0); // 数组类型忽略count，传0
        }
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.record((permanent ? "自动拉黑🔒永久" : "自动拉黑🏴") + "在[" + TaskListName + "]中添加[" + taskTitle
                    + "]黑名单:" + TaskSelectModelField.getValue()
                    + (permanent ? "#确定性失败(交易/支付或服务端固定拒绝)，永不自动解禁" : ""));
            // 记录拉黑日期，供"超期自动解禁重试"使用（只记自动项，用户手动加的不会被解禁）
            if (permanent) {
                // 交易/支付类：一次即永久，绝不进入"解禁重试"生命周期
                recordAutoBlackPermanent(ModelFieldsType, listTitle, taskTitle);
            } else {
                // 本地定制：把展示名一起记下（黑名单页优先显示它，避免只显示英文键）
                recordAutoBlack(ModelFieldsType, listTitle, taskTitle, displayName);
            }
        } else {
            Log.record("添加" + TaskListName + "黑名单失败：" + taskTitle);
        }
    }

    /**
     * 自动拉黑（需连续命中确认）：用于错误文案模糊、可能只是临时状态的任务。
     * <p>连续命中 {@link #BLACKLIST_CONFIRM_HITS} 次才真正拉黑；未达标时只记录命中并打日志，
     * 避免一次性的临时故障（活动当天未配置等）被永久跳过。
     *
     * <h3>🔴 计数口径（2026-10-05 修）</h3>
     * 命中次数指的是「**连续多少天**失败」，因此**同一天最多只记 1 次**。
     * 曾经没有这道闸门：同一轮里同一任务被调用 3 次（实测同一秒内连调、间隔 151ms），
     * {@code hits} 就从 0 累到 3 → **当场真拉黑**，等于把「连续 3 天」的设计意图架空成
     * 「同一轮连续失败 3 次」，与「避免一次性临时故障被永久跳过」的初衷完全相反。
     */
    public static void MarkTaskBlackListConfirm(String ModelFieldsType, String listTitle, String TaskListName, String taskTitle) {
        MarkTaskBlackListConfirm(ModelFieldsType, listTitle, TaskListName, taskTitle, null);
    }

    /** 需连续确认的自动拉黑（带展示名，语义同 {@link #MarkTaskBlackList} 的五参重载） */
    public static void MarkTaskBlackListConfirm(String ModelFieldsType, String listTitle, String TaskListName,
                                                String taskTitle, String displayName) {
        try {
            String key = autoBlackKey(ModelFieldsType, listTitle, taskTitle);
            // 必须先确保已从磁盘载入：AutoBlackListMap 是懒加载的，新进程里不调 ensureLoaded
            // 会读不到历史记录，命中次数永远从 0 起算（表现为始终"第1/3次命中"，永远拉不上）
            AutoBlackListMap.ensureLoaded();
            AutoBlackRecord record = AutoBlackRecord.parse(AutoBlackListMap.get(key));
            long today = todayIndex();
            if (record == null || today - record.lastDay > BLACKLIST_CONFIRM_WINDOW_DAYS) {
                record = new AutoBlackRecord();
            }
            // 观察期就把展示名记下来：真拉黑时不必再依赖"任务键 → 中文名"的映射
            if ((record.title == null || record.title.isEmpty()) && displayName != null && !displayName.isEmpty()) {
                record.title = displayName;
            }
            // 🔴 同日去重：今天已经记过一次就直接返回，**不再累加**。
            // 没有它，同一轮里的多次失败会在毫秒内凑满 BLACKLIST_CONFIRM_HITS → 秒拉黑。
            // 这里**仍然要落盘一次**：上面可能刚补了 title（老记录只有 4 段），
            // 不落盘这次补的名字就丢了，界面又会显示英文键。
            if (record.hits > 0 && record.lastDay == today) {
                AutoBlackListMap.put(key, record.format());
                AutoBlackListMap.save();
                return;
            }
            if (record.hits + 1 < BLACKLIST_CONFIRM_HITS) {
                record.hits = record.hits + 1;
                record.lastDay = today;
                record.blackDay = 0L;
                AutoBlackListMap.put(key, record.format());
                AutoBlackListMap.save();
                Log.record("自动拉黑🕵️[" + TaskListName + "][" + taskTitle + "]可疑错误第"
                        + record.hits + "/" + BLACKLIST_CONFIRM_HITS + "天命中，暂不拉黑");
                return;
            }
            MarkTaskBlackList(ModelFieldsType, listTitle, TaskListName, taskTitle, displayName);
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
                        Log.record("黑名单治理🧹清理过期观察记录[" + key.split("\\|", 3)[2] + "]");
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
                "三国大冒险过1关征战", "到店支付得50g能量"});
        RELEASED_DEFAULTS.put("AntForestV2|AntForestHuntTaskList", new String[]{
                "【限时】玩游戏得2次机会", "去乐园开宝箱得机会"});
        RELEASED_DEFAULTS.put("AntFarm|AntFarmDrawMachineTaskList", new String[]{
                "【限时】玩游戏得新机会", "【限时】玩游戏得3次机会", "限时玩游戏得新机会",
                "【限时】开宝箱得2次机会", "【限时】开宝箱得3次机会",
                // 2026-10-12：从预置黑名单释放（存量设备上那条靠本次 sweep 摘掉）
                "消耗饲料换机会"});
        RELEASED_DEFAULTS.put("AntOrchard|AntOrchardTaskList", new String[]{
                "逛助农好货得肥料", "钓鱼1次", "逛一逛闪购外卖", "逛好物最高得1500肥料"});
        // 芝麻粒游戏/浏览/签到类：服务端对 taskFeedback 不校验是否真参与过，未报名任务一发即 success
        // ⇒ 原先"预置拉黑"的这些条目全部释放，交给任务循环自动完成；
        // 保留预置的只剩真实交易/履约类（下单/租赁/订酒店/回收/雇佣/付钱/查车）
        RELEASED_DEFAULTS.put("AntMember|MemberCreditSesameTaskList", new String[]{
                "去玩小游戏", "去玩这城有良田", "去玩三国冰河时代", "去玩青云诀之伏魔", "去玩龙迹之城",
                "去玩智商乐消消", "去玩挪了个箭", "去玩斗破苍穹", "去玩灵画师", "去玩浪漫餐厅",
                "去玩烈焰觉醒", "去玩时光杂货店", "玩小游戏30秒", "玩任意游戏30秒", "玩30秒三国冰河时代",
                "玩任意1个游戏", "添加桌面小组件", "坚持签到领奖励", "坚持逛裹酱领福利", "坚持看直播领福利",
                "坚持种水果", "每日施肥领水果", "逛淘宝签到", "头条刷热点领现金", "618去淘金币赢20亿",
                "去点淘逛一逛", "去淘金币逛一逛", "逛逛淘金币", "来淘金币赢20亿", "去逛一逛消消乐"});
        RELEASED_DEFAULTS.put("AntFarm|AntFarmDoFarmTaskList", new String[]{
                "到店付款"});
        RELEASED_DEFAULTS.put("AntSports|AntSportsTaskList", new String[]{
                "下载登录AI健康管家"});
        RELEASED_DEFAULTS.put("goldenbeans|GoldenBeansTaskList", new String[]{
                "GOLDEN_BEAN_TASK_XIANXIAZHIFU"});
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

    /** {@link #autoBlackState} 取值：未被自动拉黑追踪（用户手动加的，或当前不在黑名单里） */
    public static final int AUTO_BLACK_NONE = 0;
    /** {@link #autoBlackState} 取值：自动拉黑中（会随"满 N 天解禁重试"变化） */
    public static final int AUTO_BLACK_TRACKED = 1;
    /** {@link #autoBlackState} 取值：自动拉黑且永不自动解禁（交易/支付类） */
    public static final int AUTO_BLACK_PERMANENT = 2;

    /**
     * 黑名单条目的来源：用户手动加的返回 {@link #AUTO_BLACK_NONE}；只有自动机制登记过**且当前仍在黑名单里**
     * 才算"自动"（曾记录但已解禁待重试的不算）。
     * <p>供配置页给候选项加"（自动）"标注，让用户能分辨自己加的还是系统加的。
     */
    public static int autoBlackState(String module, String listTitle, String taskTitle) {
        try {
            AutoBlackListMap.ensureLoaded();
            AutoBlackRecord record = AutoBlackRecord.parse(
                    AutoBlackListMap.get(autoBlackKey(module, listTitle, taskTitle)));
            if (record == null) {
                return AUTO_BLACK_NONE;
            }
            if (record.blackDay == AutoBlackRecord.PERMANENT) {
                return AUTO_BLACK_PERMANENT;
            }
            // blackDay==0：观察期 / 已解禁待重试，当前并不在黑名单里，不标注
            return record.blackDay != 0L ? AUTO_BLACK_TRACKED : AUTO_BLACK_NONE;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return AUTO_BLACK_NONE;
        }
    }

    /** {@link #blackListOrigin} 取值：用户手动加的（配置页不标注） */
    public static final int ORIGIN_MANUAL = 0;
    /** {@link #blackListOrigin} 取值：模块预置（默认）项 */
    public static final int ORIGIN_PRESET = 1;
    /** {@link #blackListOrigin} 取值：自动拉黑（会随"满 N 天解禁重试"变化） */
    public static final int ORIGIN_AUTO = 2;
    /** {@link #blackListOrigin} 取值：自动拉黑且永不自动解禁（交易/支付类） */
    public static final int ORIGIN_AUTO_PERMANENT = 3;

    /**
     * 黑名单条目的来源（供配置页标注）：自动·永久 > 自动 > 预置（默认）> 用户手动。
     * <p>"默认"读的是 {@link #PRESET_BLACKLIST} 登记表，所以**配置页在独立进程里也能判**。
     */
    public static int blackListOrigin(String module, String listTitle, String taskTitle) {
        switch (autoBlackState(module, listTitle, taskTitle)) {
            case AUTO_BLACK_PERMANENT:
                return ORIGIN_AUTO_PERMANENT;
            case AUTO_BLACK_TRACKED:
                return ORIGIN_AUTO;
            default:
                return isPresetBlackListItem(module, listTitle, taskTitle) ? ORIGIN_PRESET : ORIGIN_MANUAL;
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

    /**
     * 各模块"黑白名单初始化"的收尾：把预置黑名单补进列表、登记预置白名单，并保存配置。
     * <p>原先 9 个模块 × 13 个任务列表块各自抄了一遍「遍历 blackList → add(已存在则跳过) ／
     * 遍历 whiteList → remove ／ ConfigV2.save ＋ 成功/失败日志」，逻辑完全相同、只有中文名不同。
     * <p>日志文案由 displayName 拼出，与原先逐字一致：成功 `黑白名单🈲<名>自动设置: <列表>`，
     * 失败 `<名>黑白名单设置失败`。
     *
     * <p><b>白名单语义变更</b>：白名单只用于「阻止自动拉黑机制去添加该任务」，
     * 不再从列表中移除任何条目——用户手动加入黑名单的任务会被保留，不会被白名单悄悄删掉。
     *
     * @param displayName 列表中文名（如 "森林活力值任务"），仅用于日志
     * @param listTitle   列表字段名（如 "AntForestHuntTaskList"），用于登记白名单、与自动拉黑入口对应
     * @param blackList   预置拉黑项（键＝任务标题）
     * @param whiteList   预置白名单项（仅阻止自动拉黑添加，不删除用户条目）
     * @param field       目标 SelectModelField（可用性由调用方先判空；为 null 时直接返回）
     */
    public static void syncTaskBlackList(String displayName, String listTitle, Set<String> blackList,
                                         Set<String> whiteList, SelectModelField field) {
        if (field == null) {
            return;
        }
        // 登记白名单：仅用于阻止自动拉黑添加，绝不删除用户手动加入黑名单的条目
        if (listTitle != null && whiteList != null && !whiteList.isEmpty()) {
            TASK_WHITE_LIST.put(listTitle, new HashSet<>(whiteList));
        }
        Set<String> currentValues = field.getValue();
        boolean added = false;
        if (currentValues != null) {
            for (String task : blackList) {
                // 白名单优先：若预置黑名单与白名单冲突，以白名单为准，不自动添加
                if (whiteList != null && whiteList.contains(task)) {
                    continue;
                }
                if (!currentValues.contains(task)) {
                    field.add(task, 0);
                    added = true;
                }
            }
            // 注意：不再遍历 whiteList 调用 remove。白名单只挡"自动拉黑去加它"，
            // 用户手动加入黑名单的条目（无论是否为白名单任务）一律保留。
        }
        if (!added) {
            // 预置项均已在列表中：不重复落盘、不重复刷日志（每轮会被多次调用）
            return;
        }
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.record("黑白名单🈲" + displayName + "自动设置: " + field.getValue());
        } else {
            Log.record(displayName + "黑白名单设置失败");
        }
    }

    private static String autoBlackKey(String module, String listTitle, String taskTitle) {
        return module + "|" + listTitle + "|" + taskTitle;
    }

    /** 当前天序号，用于按天比较 */
    private static long todayIndex() {
        return System.currentTimeMillis() / 86400000L;
    }

    private static void recordAutoBlack(String module, String listTitle, String taskTitle, String displayName) {
        try {
            String key = autoBlackKey(module, listTitle, taskTitle);
            // 同上：先确保载入，否则读不到旧的 retry，会把"解禁重试次数"反复重置为 0
            AutoBlackListMap.ensureLoaded();
            AutoBlackRecord old = AutoBlackRecord.parse(AutoBlackListMap.get(key));
            AutoBlackRecord record = new AutoBlackRecord();
            record.hits = 0;
            record.lastDay = todayIndex();
            record.retry = old == null ? 0 : old.retry;
            // 展示名：本次没给就沿用记录里已有的（反复拉黑不该把已记下的中文名抹掉）
            record.title = displayName != null && !displayName.isEmpty()
                    ? displayName
                    : (old == null ? null : old.title);
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

    // ===================== 任务黑名单：界面展示 & 手动解禁 =====================
    // 下面这些方法**只读既有数据、或做用户显式要求的手动清理**，不参与"是否拉黑"的判定链路，
    // 因此改它们不会影响任务的实际行为（判定逻辑见 MarkTaskBlackList / MarkTaskBlackListConfirm）。

    /** 自动拉黑满多少天自动解禁（界面显示用） */
    public static int getBlacklistRetryDays() {
        return BLACKLIST_RETRY_DAYS;
    }

    /** 模糊错误需要连续命中多少次才拉黑（界面显示用） */
    public static int getBlacklistConfirmHits() {
        return BLACKLIST_CONFIRM_HITS;
    }

    /** 解禁重试几次后永久拉黑（界面显示用） */
    public static int getBlacklistMaxRetry() {
        return BLACKLIST_MAX_RETRY;
    }

    /** 当前天序号；界面用它算"还剩几天解禁" */
    public static long currentDayIndex() {
        return todayIndex();
    }

    /**
     * 天序号 → {@code yyyy-MM-dd}，供界面显示「已拉黑 2026-10-01」这类文案。
     *
     * <p>天序号的口径是 {@code System.currentTimeMillis() / 86400000}（UTC 取整），
     * 东八区下它对应的本地时间是当天 08:00，所以按本地时区格式化**不会差一天**。
     * {@code <= 0}（观察期/非法值）返回空串，调用方据此省略日期段。
     */
    public static String formatBlackDay(long dayIndex) {
        if (dayIndex <= 0) {
            return "";
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date(dayIndex * 86400000L));
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return "";
        }
    }

    /**
     * 列出模块自动拉黑的记录（含仍在"观察期"、尚未真正拉黑的条目），按来源 → 任务名排序。
     *
     * <p>数据源只有 {@link AutoBlackListMap}——**用户自己在设置页手动加进「黑名单列表」的项不在其中**，
     * 所以本清单回答的是"模块自己把哪些任务停掉了"，与各模块的黑名单列表是互补关系。
     *
     * <p>任务中文名取自该字段自己的候选项（{@link SelectModelField#getExpandValue()}，与设置页同源），
     * 服务端已不再下发、候选项里找不到的，回落显示稳定键本身，不臆造。
     */
    /**
     * 载入「黑名单列表」相关的「任务键 → 中文名」映射。
     *
     * <p>这些映射由各模块跑任务时从服务端学到的标题写进来（模块主目录下的 {@code *Task.json}）。
     * 黑名单页要靠它们把 {@code CHOUCHOULE_GAME_ZJD3000} 这类稳定键显示成中文，
     * 而原本统一负责载入的 {@code ConfigPreload.prepare()} **只在设置页被调用** ——
     * 从主界面直接进黑名单时映射是空的，任务名就会整片回落成英文键。
     *
     * <p>这里刻意只读映射文件、**不碰配置**：{@code prepare()} 会连带 {@code ConfigV2.load()}
     * 把配置文件重灌一遍，万一用户刚在别的页面改过还没落盘就会被覆盖。
     */
    private static void loadBlacklistNameMaps() {
        AntForestVitalityTaskListMap.load();
        AntForestHuntTaskListMap.load();
        MonopolyTaskListMap.load();
        AntOceanAntiepTaskListMap.load();
        AntOceanFishBlackListMap.load();
        AntOrchardTaskListMap.load();
        AntOrchardDrawTaskListMap.load();
        AntFarmDoFarmTaskListMap.load();
        AntFarmDrawMachineTaskListMap.load();
        GoldenBeansTaskListMap.load();
        AntDodoTaskListMap.load();
        AntStallTaskListMap.load();
        AntSportsTaskListMap.load();
        AntMemberTaskListMap.load();
        MemberCreditSesameTaskListMap.load();
    }

    /**
     * 兜底：从字段自己的界面标题里取列表名。
     *
     * <p>各模块给黑名单字段起的标题都是「活力值 | 黑名单列表」「新村任务 | 黑名单列表」这种形式，
     * 取竖线左边再补「黑名单」，正好就是界面要的行标题。
     *
     * <p>有它就不必指望 {@link #BLACKLIST_LIST_NAMES} 把每个列表都登记全 ——
     * 上游将来新增一个黑名单列表时，界面也不会退化成只显示一个「黑名单」。
     */
    private static String resolveListNameFromField(SelectModelField field) {
        if (field == null) {
            return "";
        }
        String title = field.getName();
        if (title == null || title.isEmpty()) {
            return "";
        }
        int bar = title.indexOf('|');
        String left = (bar >= 0 ? title.substring(0, bar) : title).trim();
        if (left.isEmpty()) {
            return "";
        }
        return left.endsWith("黑名单") ? left : left + "黑名单";
    }

    /** 黑名单列表在界面上的先后顺序 = 它在 {@link #BLACKLIST_LIST_TARGETS} 里的声明顺序 */
    private static int blacklistListOrder(String fieldCode) {
        int index = 0;
        for (String code : BLACKLIST_LIST_TARGETS.keySet()) {
            if (code.equals(fieldCode)) {
                return index;
            }
            index++;
        }
        return Integer.MAX_VALUE;
    }

    /** 一级分组的顺序 = {@link #BLACKLIST_GROUP_ORDER} 的登记顺序（中文按码点排没有意义） */
    private static int blacklistGroupOrder(String groupName) {
        int index = BLACKLIST_GROUP_ORDER.indexOf(groupName);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * 状态档位：**已拉黑(0) → 观察中(1) → 已永久拉黑(2)**。
     *
     * <p>正在被拦的排最前（它有明确的解禁倒计时、最需要被看到）；观察期次之（还没真被拦，
     * 但连续命中在累积，是"趋势预警"）；永久拉黑的已经不会自己恢复，放最后，不占视线。
     */
    private static int blacklistStateRank(AutoBlackTaskEntry entry) {
        if (entry.isPermanent()) {
            return 2;
        }
        return entry.isWatching() ? 1 : 0;
    }

    /**
     * 同一档内的"紧急度"，**升序**排列 —— 越小越该先看。
     *
     * <ul>
     *   <li>已拉黑：剩余解禁天数（快解禁的在前，因为它们最可能马上回到任务队列）；</li>
     *   <li>观察中：命中次数的相反数（命中越多越接近被拉黑，排越前）。</li>
     * </ul>
     */
    private static int blacklistUrgency(AutoBlackTaskEntry entry, long today) {
        if (entry.isWatching()) {
            return -entry.hits;
        }
        long remain = (long) BLACKLIST_RETRY_DAYS - (today - entry.blackDay);
        // 永久拉黑（blackDay = -1）算出来是很大的负数，钳到 0 即可（它们同档、下面会按名称排）
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remain));
    }

    public static List<AutoBlackTaskEntry> listAutoBlackEntries() {
        List<AutoBlackTaskEntry> entries = new ArrayList<>();
        try {
            AutoBlackListMap.load();
            loadBlacklistNameMaps();
            for (String key : new ArrayList<>(AutoBlackListMap.keys())) {
                AutoBlackRecord record = AutoBlackRecord.parse(AutoBlackListMap.get(key));
                if (record == null) {
                    continue;
                }
                String[] parts = key.split("\\|", 3);
                String module = parts.length > 0 ? parts[0] : "";
                String fieldCode = parts.length > 1 ? parts[1] : "";
                String taskValue = parts.length > 2 ? parts[2] : "";
                // 已废弃的列表：字段都没了，界面既没法翻中文名、明细页也点不进去。
                // 与其硬凑个名字挂上去（会跟同名列表并成一行、可点进去的却是另一个列表），
                // 不如**索性不显示** —— 它们的记录由 sweepObsoleteBlackLists() 跨天清掉。
                if (OBSOLETE_BLACKLIST_LISTS.contains(fieldCode)) {
                    continue;
                }
                String[] target = BLACKLIST_LIST_TARGETS.get(fieldCode);
                String sourceName = target == null || target.length < 2 ? fieldCode : target[1];
                String groupName = BLACKLIST_GROUP_NAMES.getOrDefault(fieldCode, sourceName);
                SelectModelField field = findTaskListField(module, fieldCode);
                // 行标题（列表名）三级取值：登记表 → 字段自带标题 → 来源名。
                // 中间那层是兜底，保证"没登记过的新列表"也不会显示成光秃秃的「黑名单」。
                String listName = BLACKLIST_LIST_NAMES.get(fieldCode);
                if (listName == null || listName.isEmpty()) {
                    listName = resolveListNameFromField(field);
                }
                if (listName.isEmpty()) {
                    listName = sourceName;
                }
                // ⚠️ 拿不到字段表时是"未知"（null），**不能当成"不在列表里"** ——
                // 否则界面会给一条刚拉黑的任务标上「已不在黑名单列表」，看起来像已经放出来了。
                Boolean inList = field == null ? null : Boolean.TRUE.equals(field.contains(taskValue));
                // 名字优先级：记录里存的「拉黑当时的中文标题」 > 字段候选/名字映射 > 稳定键本身。
                // 第一优先是为了**不依赖名字映射** —— 有些列表是按英文稳定键拉黑的，
                // 那个键在映射里可能永远学不到，界面就只能显示英文。
                //
                // 🔴 但这里要防一手「英文占位标题」：早期调用方把 taskId 当 title 传了下来
                // （金豆的 displayName 会回落成 taskId、抽抽乐领奖路径干脆不传），于是记录里
                // 存的是英文键本身。它**恒等于 taskValue**，却因为排在最高优先级而把
                // 「字段名字映射」那级整个挡住 —— 明明 GoldenBeansTask.json 里有中文名，
                // 界面照样显示 GOLDENBEAN_GAME_ZH_YLPT（2026-10-05 实测 9 条）。
                // 判据取「等于稳定键」而不是「是不是纯 ASCII」：中文标题恰好是英文的情况极罕见，
                // 而 taskValue 一定是稳定键，这样判定最不容易误伤。
                String recordTitle = record.title == null ? "" : record.title.trim();
                String taskName = !recordTitle.isEmpty() && !recordTitle.equals(taskValue)
                        ? recordTitle
                        : resolveTaskName(field, fieldCode, taskValue);
                entries.add(new AutoBlackTaskEntry(key, module, fieldCode, taskValue, sourceName,
                        groupName, listName, taskName, record.hits, record.blackDay,
                        record.retry, inList));
            }
            // 排序（界面直接照这个顺序渲染）：
            //   ① 一级分组：固定表（蚂蚁森林 → 神奇海洋 → 芭芭农场 → 庄园 → 金豆 → …）
            //   ② 同组内：按黑名单列表的**声明顺序**（与设置页里的先后一致，位置稳定）
            //   ③ 同列表内：**已拉黑 → 观察中 → 永久拉黑**，每档内"最紧急的在前"
            //      （已拉黑按剩余解禁天数升序、观察中按命中次数降序）
            //   ④ 同级再按任务名**拼音**（中文按码点排出来的顺序对用户毫无意义）
            long today = todayIndex();
            Collator pinyin = Collator.getInstance(Locale.CHINA);
            entries.sort(Comparator
                    .comparingInt((AutoBlackTaskEntry e) -> blacklistGroupOrder(e.groupName))
                    .thenComparing((AutoBlackTaskEntry e) -> e.groupName)
                    .thenComparingInt((AutoBlackTaskEntry e) -> blacklistListOrder(e.fieldCode))
                    .thenComparingInt(MessageUtil::blacklistStateRank)
                    .thenComparingInt((AutoBlackTaskEntry e) -> blacklistUrgency(e, today))
                    .thenComparing((AutoBlackTaskEntry e) -> e.taskName, pinyin));
        } catch (Throwable t) {
            Log.err(TAG, "listAutoBlackEntries err:", t);
        }
        return entries;
    }

    /**
     * 手动解禁一条：把它从对应模块的黑名单列表里移除，并清掉自动拉黑记录。
     *
     * <p>与"自动解禁"的区别：立刻执行，且**不累加解禁重试次数**——用户主动干预不该被记成一次失败重试，
     * 否则多按几次就把任务"刷"成永久拉黑。
     *
     * <p>注意：解禁只代表"允许再试"，不保证下次能成功；服务端已下线的任务会再次失败并被重新拉黑。
     */
    public static boolean releaseAutoBlack(String key) {
        try {
            String[] parts = key.split("\\|", 3);
            if (parts.length < 3) {
                return false;
            }
            String taskValue = parts[2];

            // ⚠️ 账号必须用 resolveCurrentUid()：界面进程刚启动时 currentUid 是空的，
            //   直接拿它去 save 会把改动写进「默认配置」而不是账号配置 ——
            //   现象就是「解禁点了、设置里还勾着、任务照样被跳过」。
            String userId = UserIdMap.resolveCurrentUid();
            if (userId == null || userId.isEmpty()) {
                Log.record("手动解禁⚠️[" + taskValue + "]拿不到当前账号，未改动配置");
                return false;
            }

            SelectModelField field = findTaskListField(parts[0], parts[1]);
            if (field == null) {
                // 字段表没进内存时**绝不能**当作解禁成功：那样只会删掉自动拉黑记录，
                // 黑名单里的键还留着，任务依旧被跳过 —— 用户会以为已经放开了。
                Log.record("手动解禁⚠️[" + taskValue + "]找不到黑名单字段(" + parts[0] + "|" + parts[1]
                        + ")，未改动配置");
                return false;
            }
            field.remove(taskValue);
            if (Boolean.TRUE.equals(field.contains(taskValue))) {
                // 键本来就不在这个字段里（或 remove 没生效）：也要如实报失败，别报"已移出"
                Log.record("手动解禁⚠️[" + taskValue + "]不在[" + parts[1] + "]里，未能移除");
                return false;
            }

            AutoBlackListMap.load();
            AutoBlackListMap.remove(key);
            AutoBlackListMap.save();
            if (!ConfigV2.save(userId, false)) {
                Log.record("手动解禁⚠️[" + taskValue + "]配置保存失败");
                return false;
            }
            Log.record("手动解禁🕊️[" + taskValue + "]已移出黑名单");
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "releaseAutoBlack err:", t);
            return false;
        }
    }

    /** 手动解禁全部自动拉黑项，返回处理条数 */
    public static int releaseAllAutoBlack() {
        int count = 0;
        for (AutoBlackTaskEntry entry : listAutoBlackEntries()) {
            if (releaseAutoBlack(entry.key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 任务稳定键 → 中文展示名；取不到就回落成键本身。
     * <p>依次走两条路，任一命中即可：
     * <ol>
     *   <li>字段自带的候选项列表（{@code getExpandValue()}）—— 与设置页「黑名单列表」里显示的名字同源，
     *       还包含各实体类内置的兜底名；</li>
     *   <li>该字段对应的 {@code *Task.json} 映射（见 {@link #BLACKLIST_NAME_MAPS}）——
     *       **字段表还没灌进内存时兜底**：黑名单页从主界面直接进来时 {@code ConfigV2} 的字段表是空的，
     *       只靠第 1 条会让"按英文稳定键拉黑"的列表（金豆、金豆夺宝专场）整片显示英文键。</li>
     * </ol>
     */
    private static String resolveTaskName(SelectModelField field, String fieldCode, String taskValue) {
        if (taskValue.isEmpty()) {
            return taskValue;
        }
        // 1) 字段自带的候选项：与设置页「黑名单列表」显示的名字同源，还含各实体类内置的兜底名
        if (field != null) {
            try {
                List<? extends IdAndName> options = field.getExpandValue();
                if (options != null) {
                    for (IdAndName option : options) {
                        if (option != null && taskValue.equals(option.id) && option.name != null
                                && !option.name.isEmpty()) {
                            return option.name;
                        }
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }
        // 2) 兜底：直接查该字段的映射文件 —— 字段表没灌进内存时（从主界面直接进黑名单页）也能出中文
        //    ⚠️ 必须先 load：这些 idMap 的 getMap() 只是内存视图，磁盘内容要显式 load 才进来。
        //    漏了这一步就会出现"磁盘映射里明明有中文名、界面却显示英文键"（2026-10-05 实测 9 条）。
        Runnable loader = BLACKLIST_NAME_LOADERS.get(fieldCode);
        if (loader != null) {
            try {
                loader.run();
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }
        Supplier<Map<String, String>> supplier = BLACKLIST_NAME_MAPS.get(fieldCode);
        if (supplier != null) {
            try {
                Map<String, String> map = supplier.get();
                if (map != null) {
                    String name = map.get(taskValue);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }
        return taskValue;
    }

    /**
     * 交易/支付类专用：直接记 {@link AutoBlackRecord#PERMANENT}，永不自动解禁。
     * <p>{@code retry} 一并置为 {@link #BLACKLIST_MAX_RETRY}，即使日后被别的路径按常规拉黑也不会被解禁。
     */
    private static void recordAutoBlackPermanent(String module, String listTitle, String taskTitle) {
        try {
            String key = autoBlackKey(module, listTitle, taskTitle);
            AutoBlackListMap.ensureLoaded();
            AutoBlackRecord record = new AutoBlackRecord();
            record.hits = 0;
            record.lastDay = todayIndex();
            record.retry = BLACKLIST_MAX_RETRY;
            record.blackDay = AutoBlackRecord.PERMANENT;
            AutoBlackListMap.put(key, record.format());
            AutoBlackListMap.save();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 自动拉黑记录：命中次数 / 最后命中天 / 拉黑天 / 解禁重试次数 / 展示名（可选）。
     * <p>{@code blackDay}：{@code >0} 已拉黑；{@code 0} 未拉黑（观察中或已解禁待重试）；{@link #PERMANENT} 永久拉黑。
     * <p>{@code title} 是**拉黑当时的中文标题**，只服务界面显示（黑名单页优先用它）。
     * 追加在末尾且**可缺省**：老记录（4 段）照旧可解析，新记录是 5 段。
     *
     * <h3>🔴 hits 的口径与钳制（2026-10-05 引入，2026-10-11 修正上界）</h3>
     * {@code hits} 表示「**连续多少天**失败」，由 {@link #MarkTaskBlackListConfirm} 保证每天最多 +1。
     * 但在此之前没有这道闸门，历史记录里存在**同一天累加到 2 甚至 3** 的脏数据 ——
     * 它们会让「连续 3 天」提前一天达成（旧值 2 明天再失败一次就拉黑）。
     * 故读回时把 {@code hits} 钳到 {@code [0, BLACKLIST_CONFIRM_HITS - 1]}（= [0, 2]）：
     * 把脏数据（≥3）压回 2，同时**保留正常累计**（1、2 必须原样保留）。
     * <p>⚠️🔴 **2026-10-11 修正**：原实现钳到 {@code [0, 1]}，与判定
     * {@code if (record.hits + 1 < BLACKLIST_CONFIRM_HITS)} **直接冲突** ——
     * hits 恒 ≤ 1 ⇒ 该条件**恒成立** ⇒ 永远走"暂不拉黑"分支 ⇒
     * **整条「连续确认」路径彻底失效**（实测：抽抽乐任务反复停在"第2/3天"、
     * {@code AutoBlackList.json} 里 hits=2 堆积却永无 hits=3）。上界改为
     * {@code BLACKLIST_CONFIRM_HITS - 1} 即修复。
     * （"同一天重复命中"另有专门闸门拦 —— 见 {@link #MarkTaskBlackListConfirm} 里的同日去重，
     * 不依赖这道钳制；两者叠加才导致钳到 1 之后再也涨不上去。）
     */
    private static final class AutoBlackRecord {
        private static final long PERMANENT = -1L;
        private int hits;
        private long lastDay;
        private long blackDay;
        private int retry;
        private String title;

        private static AutoBlackRecord parse(String str) {
            if (str == null || str.isEmpty()) {
                return null;
            }
            // 最多切 5 段：第 5 段收"剩余全部"，标题里万一带了分号也不会被切碎
            String[] parts = str.split(";", 5);
            if (parts.length < 3) {
                return null;
            }
            try {
                AutoBlackRecord record = new AutoBlackRecord();
                // 观察期计数上界 = BLACKLIST_CONFIRM_HITS - 1（见类注释「hits 的口径与钳制」）。
                // ⚠️ 上界**不能是 1**：判定是 `hits + 1 < BLACKLIST_CONFIRM_HITS`，
                // 钳到 1 会让该条件恒成立 → 永远"暂不拉黑" → 连续确认路径彻底失效（2026-10-11 修复）。
                // ⚠️ 只钳观察期（blackDay == 0）；已拉黑/永久拉黑的记录 hits 不再被消费，原样保留。
                int rawHits = Integer.parseInt(parts[0].trim());
                record.lastDay = Long.parseLong(parts[1].trim());
                record.blackDay = Long.parseLong(parts[2].trim());
                record.hits = record.blackDay == 0L
                        ? Math.max(0, Math.min(BLACKLIST_CONFIRM_HITS - 1, rawHits))
                        : rawHits;
                record.retry = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 0;
                record.title = parts.length > 4 ? parts[4].trim() : null;
                return record;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private String format() {
            String base = hits + ";" + lastDay + ";" + blackDay + ";" + retry;
            // 没有标题时仍写 4 段，与旧版格式完全一致（旧版本读回来也不会出错）
            return title == null || title.isEmpty() ? base : base + ";" + title;
        }
    }

}
