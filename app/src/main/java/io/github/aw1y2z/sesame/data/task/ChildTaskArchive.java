package io.github.aw1y2z.sesame.data.task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 子任务在磁盘上的存档。
 * <p>
 * 为什么要落盘：庄园里有一批「到点才做」的动作（蹲点投喂、定时睡觉/起床、偷菜排行赛…），
 * 它们都靠 {@link ModelTask.ChildModelTask} 挂在内存时间线上。而
 * {@code childTaskMap} 是纯内存 {@code Map}，宿主进程被系统杀掉、支付宝被收进后台回收、
 * 或者用户手动清后台之后，排程就没了 —— 下一次 {@code startAllTask} 只能从头全量重扫，
 * 既慢又会把当天的时间窗白白丢掉。
 * <p>
 * 所以把「什么时候做什么」这件事落到磁盘，重启后按 {@link #group} + {@link #id} 重建
 * 内存排程。
 * <p>
 * ⚠️ <b>刻意不存 {@code Runnable}</b>：{@link ModelTask.ChildModelTask} 持的是
 * Lambda 引用，不可序列化。真正的做法是只存「三元组 + 时刻」，重启时由
 * {@link ChildTaskRegistry} 按 group 反查得到可执行体 —— 这也是这套存档结构存在的意义。
 *
 * @see ChildTaskPersistence
 * @see ChildTaskRegistry
 */
@Getter
// ⚠️ 忽略磁盘上多余的字段：以后给存档加字段时，旧版本文件里的未知 key 不该让整个解析失败
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChildTaskArchive {

    /** 子任务 id（同 group 内唯一）。 */
    private final String id;

    /** 子任务分组，决定用哪个执行体重建。 */
    private final String group;

    /** 归属的模型任务名（AntFarm / AntOrchard …），跨账号/跨模块时用于校验。 */
    private final String modelName;

    /** 归属账号 uid —— 防止切号后把上一账号的排程捡起来跑。 */
    private final String userId;

    /** 计划执行时刻（毫秒）。 */
    private final long execTime;

    /** 入盘时刻（毫秒），用于「存档太旧就丢弃」。 */
    private final long savedTime;

    /**
     * Jackson 反序列化专用入口。
     * <p>
     * ⚠️ <b>必须显式标注</b>：本类字段全是 {@code final} 且只有一个带参构造器，Lombok 的
     * {@code @Getter} 只生成 getter、<b>不生成无参构造器</b>，于是 Jackson 找不到默认构造器
     * 会抛 {@code handleMissingInstantiator}（实测表现为「存档解析失败」，且因为被 catch
     * 吞掉，只在 error 日志里留一行，极难发现 —— 它会让跨模块合并写盘静默失效）。
     * <p>
     * 顺带注意：混淆后 r8 可能把 Jackson 的 {@code @JsonCreator} 处理打乱，所以这里同时
     * 保留字段名标注，双保险。
     */
    @JsonCreator
    public ChildTaskArchive(
            @JsonProperty("id") String id,
            @JsonProperty("group") String group,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("userId") String userId,
            @JsonProperty("execTime") long execTime,
            @JsonProperty("savedTime") long savedTime) {
        this.id = id;
        this.group = group;
        this.modelName = modelName;
        this.userId = userId;
        this.execTime = execTime;
        this.savedTime = savedTime;
    }

    /** 从内存子任务拍一张存档。 */
    public static ChildTaskArchive of(ModelTask.ChildModelTask childTask, String modelName, String userId) {
        long exec = childTask.getExecTime() == null ? 0L : childTask.getExecTime();
        return new ChildTaskArchive(
                childTask.getId(),
                childTask.getGroup(),
                modelName,
                userId,
                exec,
                System.currentTimeMillis()
        );
    }

    /**
     * 唯一键：同一 id 在不同存档里可能是「已被重新排期」的两份，
     * 还原时以 {@code execTime} 大者为准（和
     * {@link ModelTask#addChildTask} 里「同 id 覆盖旧任务」的语义保持一致）。
     */
    public String key() {
        return group + "|" + id;
    }
}
