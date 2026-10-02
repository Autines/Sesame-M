package io.github.aw1y2z.sesame.model.task.antGame;

import io.github.aw1y2z.sesame.hook.ApplicationHook;

/**
 * 「模拟玩小程序游戏」用的**支付宝侧**接口集合。
 *
 * <h3>与 {@link GameTask} 的分工</h3>
 * <ul>
 *   <li>{@link GameTask}：直连**第三方游戏服**（{@code gamesapi2.aslk2018.com}）HTTP 上报，
 *       需要预先知道每个游戏的 gid / action / channel / version；</li>
 *   <li>本类：调**支付宝自己的游戏中心 / 事件上报**接口，让服务端认定"用户确实玩过这个小程序"，
 *       只需要一个 appId（可从任务的 bizInfo.targetUrl 里解析出来）。</li>
 * </ul>
 *
 * <p>参数结构全部取自同类实现的反编译结果（森 {@code AntOrchardGameStayTask} /
 * {@code AntOrchardMultiStageTask}），不是推断出来的：
 * <pre>
 * submitUserAction         {actionCode, gameId, paladinxVersion:"2.1.95", source:"gameFramework"}
 * submitUserPlayDurationAction {gameAppId, playTime(秒), source, statisticTag:""}
 * submitEvent              {appId, eventAttrMap, eventId, idempotentNo:"platform_<ts>_<rand>_<rand>", source}
 * noticeGame               {appId, requestType:"NORMAL", sceneCode:"ORCHARD", source, version}
 * triggerTbTask            {requestType:"NORMAL", sceneCode:"ORCHARD", source, taskId, taskPlantType, version}
 * </pre>
 */
public class GameTaskRpcCall {

    /** 农场/庄园场景的版本号，与官方报文一致 */
    private static final String ORCHARD_VERSION = "20260721.01";

    /** 农场场景来源标识（官方报文里固定就是这个串） */
    public static final String SOURCE_ORCHARD = "ch_appcenter__chsub_9patch";

    /** 上报游戏时长时的来源标识（农场场景） */
    public static final String SOURCE_PLAY_DURATION = "bbnc_mc_xbioen04";

    /** 游戏框架版本号，submitUserAction 要求带上 */
    private static final String PALADINX_VERSION = "2.1.95";

    private GameTaskRpcCall() {
    }

    /**
     * 告知游戏侧：本账号要在该小程序里处理任务（{@code com.alipay.antorchard.noticeGame}）。
     * <p>通常是游戏任务三步流程的第一步：先"进入游戏"，再上报行为，最后由服务端判定完成。
     */
    public static String noticeGame(String appId) {
        String args = "[{\"appId\":\"" + safe(appId) + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\""
                + ",\"source\":\"" + SOURCE_ORCHARD + "\",\"version\":\"" + ORCHARD_VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antorchard.noticeGame", args);
    }

    /**
     * 上报一次游戏内动作（{@code com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction}）。
     *
     * @param actionCode 动作编码，如 {@code loading_completed}（加载完成）
     */
    public static String submitUserAction(String appId, String actionCode) {
        String args = "[{\"actionCode\":\"" + safe(actionCode) + "\",\"gameId\":\"" + safe(appId)
                + "\",\"paladinxVersion\":\"" + PALADINX_VERSION + "\",\"source\":\"gameFramework\"}]";
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction", args);
    }

    /**
     * 上报游戏时长（{@code com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction}）。
     * <p>游戏任务的进度通常按"玩了多久"计，所以这一步是让服务端推进任务的关键。
     *
     * @param seconds 游戏时长（秒）
     */
    public static String submitUserPlayDuration(String appId, int seconds) {
        String args = "[{\"gameAppId\":\"" + safe(appId) + "\",\"playTime\":" + seconds
                + ",\"source\":\"" + SOURCE_PLAY_DURATION + "\",\"statisticTag\":\"\"}]";
        return ApplicationHook.requestString(
                "com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction", args);
    }

    /**
     * 上报游戏事件（{@code com.alipay.gameevent.biz.rpc.submitEvent}）。
     * <p>{@code idempotentNo} 是幂等号，同一号只计一次，所以按官方格式
     * {@code platform_<时间戳>_<随机>_<随机>} 现场生成。
     *
     * @param eventId 事件标识，如 {@code loading_completed}
     * @param source  传空串时按官方惯例按 eventId 推断
     */
    public static String submitEvent(String appId, String eventId, long elapsedTime, String source) {
        String resolvedSource = source;
        if (resolvedSource == null || resolvedSource.isEmpty()) {
            resolvedSource = "loading_completed".equals(eventId) ? "my.reportLoadingCompleted" : "my.reportGamePlay";
        }
        String idempotentNo = "platform_" + System.currentTimeMillis() + "_"
                + io.github.aw1y2z.sesame.util.RandomUtil.getRandom(6) + "_"
                + io.github.aw1y2z.sesame.util.RandomUtil.getRandom(4);
        String args = "[{\"appId\":\"" + safe(appId) + "\",\"eventAttrMap\":{\"elapsedTime\":" + elapsedTime
                + "},\"eventId\":\"" + safe(eventId) + "\",\"idempotentNo\":\"" + idempotentNo
                + "\",\"source\":\"" + resolvedSource + "\"}]";
        return ApplicationHook.requestString("com.alipay.gameevent.biz.rpc.submitEvent", args);
    }

    /**
     * 触发任务（{@code com.alipay.antfarm.triggerTbTask}）。
     *
     * <p>⚠️ 本方法**当前没有任何调用点**，故意留着：同类实现只在
     * {@code actionType=MULTI_STAGE && taskPlantType=ANTIEP} 的任务上用它，
     * 而金豆夺宝专场的游戏任务 bizInfo 里既没有 actionType 也没有 taskPlantType
     * （实测 {@code taskMode=NORMAL / taskProdPlayType=OTHER}），条件对不上。
     * 等将来抓到确实需要它的报文再接线，不凭猜测调用。
     */
    public static String triggerTbTask(String taskId, String taskPlantType) {
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"" + SOURCE_ORCHARD
                + "\",\"taskId\":\"" + safe(taskId) + "\",\"taskPlantType\":\"" + safe(taskPlantType)
                + "\",\"version\":\"" + ORCHARD_VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.triggerTbTask", args);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
