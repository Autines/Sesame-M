package io.github.aw1y2z.sesame.model.task.antGame;

import org.json.JSONObject;

/**
 * 游戏类任务的**适配器接口**。
 *
 * <h3>为什么需要它</h3>
 * 每个模块的任务列表里都混着"游戏任务"（要用户真的去玩某个小程序才给奖励）。
 * 这类任务过去只有两条出路：要么某个游戏恰好有专门实现，要么**被自动拉黑跳过**。
 * 结果就是"任务永远做不了、还占着黑名单"。
 *
 * <h3>设计</h3>
 * 参照同类实现（森 {@code task/gameTasks/IGameTask}）做成"一个游戏一个适配器 + 注册表匹配"：
 * <ul>
 *   <li>{@link #canHandle} 只依据**服务端下发的结构化字段**（taskType 前缀、bizInfo 内容等）判断，
 *       不用任务标题硬编码 —— 标题会随活动改，结构化字段不会；</li>
 *   <li>{@link #execute} 负责把这个任务推进到"可领奖/已完成"；返回 {@code true} 只代表
 *       "本次动作发出去了"，**不代表任务一定完成**（最终由服务端判定，调用方要重新拉列表确认）。</li>
 * </ul>
 *
 * <p>匹配失败或执行失败都**不应导致拉黑**：游戏任务本身成功率就低，
 * 拉黑它等于"以后连试都不试了"，与做这个框架的目的相反。
 */
public interface IGameTask {

    /** 展示名（日志用），如「生存33天（金豆夺宝专场游戏任务）」 */
    String getGameName();

    /**
     * 能否处理该任务。
     *
     * @param task     服务端返回的原始任务对象（含 {@code taskBaseInfo.bizInfo}）
     * @param taskType 稳定任务键（来自 iepTaskTracer）
     * @param title    任务标题（易变，仅用于日志，**不要**拿它做匹配依据）
     * @param desc     任务描述（易变，同上）
     */
    boolean canHandle(JSONObject task, String taskType, String title, String desc);

    /**
     * 执行该游戏任务。
     *
     * @return {@code true} = 已发出推进请求；{@code false} = 缺必要信息或调用异常
     */
    boolean execute(JSONObject task, String taskType);
}
