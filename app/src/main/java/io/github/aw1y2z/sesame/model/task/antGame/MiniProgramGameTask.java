package io.github.aw1y2z.sesame.model.task.antGame;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/**
 * 「小程序游戏任务」的通用适配器：凡是服务端标为**游戏**的任务，都走它。
 *
 * <h3>它治的是什么</h3>
 * 各模块的任务清单里都混着"要你玩某个小程序游戏才给奖励"的任务。这类任务的
 * {@code finishTask} 会被服务端拒收，所以在只有"提交完成 / 拉黑"两条路的老实现里
 * 只能被永久跳过（表现为"任务做不了、还占着黑名单"）。
 *
 * <h3>怎么认出一个"游戏任务"（判据来自 2026-10-01 K40 抓包，全量 76 个任务逐一核对）</h3>
 * <pre>
 * taskBaseInfo.prodPlayParam.taskCategorization.categorizationSecondLevel == "Game"
 * </pre>
 * 抓包里**所有**真游戏任务都满足这条、且都带 {@code categorizationParamModel.game_id}：
 * <pre>
 * CHOUCHOULE_GAME_SC33T_ZH1     生存33天电台招募10次   Game / IAP  game_id=2021005194673847
 * SSBHC_LLRW_NORMAL             玩一玩时尚百货城       Game / IAP  game_id=2021006139626077
 * FOREST_ACTIVITY_DRAW_MHXCZ_*  玩梦幻消除战完成1个订单 Game / IAP  game_id=2060170000380982
 * WDFX_LLRW_ACTIVITY            玩一玩我的防线         Game / IAP  game_id=2060170000396045
 * </pre>
 *
 * <h3>⚠️ 为什么不能只按 taskType 前缀匹配（踩过的坑）</h3>
 * 同族的攒豆阶梯任务 {@code CHOUCHOULE_GAME_ZJD1500 / ZJD3000 / ZJD5000 / ZJD8000}
 * **同样以 {@code CHOUCHOULE_GAME_} 开头，但它们不是游戏**：
 * <pre>
 * CHOUCHOULE_GAME_ZJD5000  继续攒豆得3次机会   Function_Related / Ectype_Function  game_id=无
 * CHOUCHOULE_GAME_ZJD1500  【限时】攒金豆得2次机会  Function_Related / Ectype_Function  game_id=无
 * </pre>
 * 而且它们**也有小程序地址**（"去逛金豆夺宝"那个 appId），所以"能抠出 appId"同样挡不住，
 * 实测会被误判成游戏、每天白发 4 个无效游戏上报（增加风控暴露面）。
 * 只有 {@code categorizationSecondLevel == "Game"} 能把两者干净地分开。
 *
 * <h3>⚠️ 诚实说明：上报能否让任务完成，尚未验证</h3>
 * 上报用的接口与参数来自同类实现的反编译结果（森 {@code AntOrchardGameStayTask}），结构确定；
 * 但"该任务能否被这套上报推完成"还没有真机实证（任务标题是「生存33天电台招募10次」，
 * 语义上可能要求真的完成游戏内行为）。因此调用方在 {@link #execute} 之后**不拉黑**该任务，
 * 下一轮还会再试（且每天只试一次），不会因一次失败就永久放弃。
 */
public class MiniProgramGameTask implements IGameTask {

    public static final MiniProgramGameTask INSTANCE = new MiniProgramGameTask();

    private static final String TAG = "MiniProgramGameTask";

    /** 游戏任务的分类标记：prodPlayParam.taskCategorization.categorizationSecondLevel */
    private static final String GAME_CATEGORY = "Game";

    /** 兜底：从 targetUrl 抠小程序 appId（{@code alipays://platformapi/startapp?appId=2021...&...}） */
    private static final Pattern APP_ID_PATTERN = Pattern.compile("appId=([0-9]+)");

    /** 一次上报的游戏时长（秒）。官方按"玩了多久"推进进度，取偏保守值 */
    private static final int PLAY_SECONDS = 30;

    private MiniProgramGameTask() {
    }

    @Override
    public String getGameName() {
        return "小程序游戏任务";
    }

    @Override
    public boolean canHandle(JSONObject task, String taskType, String title, String desc) {
        // 只认「服务端说是游戏」+「拿得到游戏 appId」这两条；不看 taskType、更不看标题
        return isGameTask(task) && !resolveAppId(task).isEmpty();
    }

    @Override
    public boolean execute(JSONObject task, String taskType) {
        String appId = resolveAppId(task);
        if (appId.isEmpty()) {
            return false;
        }
        try {
            // ① 先告知游戏侧"要处理这个 appId 的任务"；失败不阻断后续——它只是打招呼，
            //    某些 appId 服务端本来就不需要这一步
            JSONObject notice = new JSONObject(GameTaskRpcCall.noticeGame(appId));
            boolean noticeOk = MessageUtil.checkSuccess(TAG, notice);
            // ② 上报游戏内动作与时长：这两步是让服务端推进任务进度的主体
            GameTaskRpcCall.submitUserAction(appId, "loading_completed");
            GameTaskRpcCall.submitUserPlayDuration(appId, PLAY_SECONDS);
            // ③ 补一条事件上报，覆盖按事件计数的场景
            GameTaskRpcCall.submitEvent(appId, "loading_completed", PLAY_SECONDS * 1000L, "");
            // 日志不加模块前缀：调用方（如金豆夺宝专场）自己的行里已经带了上下文，
            // 本适配器是通用的，写死某个模块名字以后被别处复用就会误导
            Log.farm("🎮游戏任务已上报[" + getGameName() + "]#appId=" + appId
                    + (noticeOk ? "" : "#noticeGame未成功"));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "execute err:", t);
            return false;
        }
    }

    /**
     * 该任务是否被服务端标为游戏任务。
     * <p>判据：{@code taskBaseInfo.prodPlayParam.taskCategorization.categorizationSecondLevel == "Game"}。
     */
    public static boolean isGameTask(JSONObject task) {
        try {
            String second = parseTaskCategorization(task).optString("categorizationSecondLevel", "");
            return GAME_CATEGORY.equals(second);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 解析小程序 appId：**优先取 {@code prodPlayParam.taskCategorization.categorizationParamModel.game_id}**
     * （服务端自己标定的游戏 id，最权威），取不到再从 {@code bizInfo.targetUrl} 里正则抠。
     * <p>两者都取不到返回空串——此时 {@link #canHandle} 会拒绝处理该任务。
     */
    public static String resolveAppId(JSONObject task) {
        try {
            JSONObject categorization = parseTaskCategorization(task);
            JSONObject paramModel = categorization.optJSONObject("categorizationParamModel");
            if (paramModel != null) {
                String gameId = paramModel.optString("game_id", "").trim();
                if (!gameId.isEmpty()) {
                    return gameId;
                }
            }
            Matcher matcher = APP_ID_PATTERN.matcher(readBizField(task, "targetUrl"));
            return matcher.find() ? matcher.group(1) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 读 {@code taskBaseInfo.prodPlayParam.taskCategorization}；取不到返回空对象。
     * <p>prodPlayParam 在报文里是**转义过的 JSON 字符串**，但也兼容直接给对象的情况
     * （服务端换包装时不至于解析失败）。
     */
    private static JSONObject parseTaskCategorization(JSONObject task) {
        JSONObject tbi = optObject(task, "taskBaseInfo");
        JSONObject param = optObject(tbi, "prodPlayParam");
        JSONObject categorization = param == null ? null : param.optJSONObject("taskCategorization");
        return categorization == null ? new JSONObject() : categorization;
    }

    /** 读 {@code taskBaseInfo.bizInfo} 里的字段；bizInfo 同样是"JSON 字符串或对象"两种形态 */
    private static String readBizField(JSONObject task, String key) {
        JSONObject biz = optObject(optObject(task, "taskBaseInfo"), "bizInfo");
        return biz == null ? "" : biz.optString(key, "");
    }

    /** 取一个"可能是 JSONObject、也可能是 JSON 字符串"的字段 */
    private static JSONObject optObject(JSONObject parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.opt(key);
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof String) {
            try {
                return new JSONObject((String) value);
            } catch (Throwable ignore) {
                return null;
            }
        }
        return null;
    }
}
