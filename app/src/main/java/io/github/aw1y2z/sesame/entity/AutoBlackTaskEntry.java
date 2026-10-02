package io.github.aw1y2z.sesame.entity;

/**
 * 任务黑名单条目（**只用于界面展示**，不参与任何"是否拉黑"的判定）。
 *
 * <p>数据来源是 {@code util/idMap/AutoBlackListMap}：模块跑任务失败时自己写进去的记录，
 * 键为 {@code 模块码|字段码|任务稳定键}，值为 {@code 命中次数;最后命中日;拉黑日;解禁重试次数}
 * （日 = Unix 天序号）。
 *
 * <p>两种状态：
 * <ul>
 *   <li><b>已拉黑</b>：{@code blackDay > 0}，该任务当前在模块黑名单列表里，满
 *       {@code retryDays} 天后会被自动解禁重试一次；</li>
 *   <li><b>观察中</b>：{@code blackDay == 0}，只是累计了"连续命中"次数、还没真拉黑
 *       （模糊错误要连续 {@code confirmHits} 次才拉黑）。</li>
 * </ul>
 *
 * <p>拉黑日等于 {@link #PERMANENT}（-1）表示解禁重试次数用尽、已永久拉黑，不再自动解禁。
 */
public class AutoBlackTaskEntry {

    /** 永久拉黑的标记值（与 MessageUtil 内部常量一致） */
    public static final long PERMANENT = -1L;

    /** 原始记录键，手动解禁时原样传回 */
    public final String key;
    /** 模块码，如 {@code AntOrchard} */
    public final String module;
    /** 字段码（黑名单列表所在字段），如 {@code AntOrchardDrawTaskList} */
    public final String fieldCode;
    /** 任务稳定键，如 {@code CHOUCHOULE_GAME_ZJD5000}（黑名单匹配就靠它） */
    public final String taskValue;
    /** 来源中文名，如「金豆夺宝专场任务」；取不到时回落为字段码 */
    public final String sourceName;
    /** 界面一级分组名（**模块**），如「蚂蚁森林」 */
    public final String groupName;
    /** 一级分组下的行标题：该黑名单列表的显示名，如「活力值黑名单」 */
    public final String listName;
    /** 任务中文名；解析不到时回落为 {@link #taskValue} */
    public final String taskName;
    /** 连续命中次数（观察期用） */
    public final int hits;
    /** 拉黑日（天序号；0=观察期，-1=永久） */
    public final long blackDay;
    /** 已解禁重试次数 */
    public final int retry;
    /**
     * 该任务当前是否真的还在模块黑名单列表里。
     * <p>{@code null} = **无法判断**（字段表还没灌进内存，拿不到那个字段）。
     * 界面在 {@code null} 时不要显示「已不在黑名单列表」，否则是误报 ——
     * 用户刚被拉黑，看着却像已经被放出来了。
     */
    public final Boolean inList;

    public AutoBlackTaskEntry(String key, String module, String fieldCode, String taskValue,
                              String sourceName, String groupName, String listName, String taskName,
                              int hits, long blackDay, int retry, Boolean inList) {
        this.key = key;
        this.module = module;
        this.fieldCode = fieldCode;
        this.taskValue = taskValue;
        this.sourceName = sourceName;
        this.groupName = groupName;
        this.listName = listName;
        this.taskName = taskName;
        this.hits = hits;
        this.blackDay = blackDay;
        this.retry = retry;
        this.inList = inList;
    }

    /** 是否已永久拉黑（不再自动解禁） */
    public boolean isPermanent() {
        return blackDay == PERMANENT;
    }

    /** 是否仍在"连续命中观察期"、尚未真正拉黑 */
    public boolean isWatching() {
        return blackDay == 0L;
    }

    /** 是否已拉黑（含永久） */
    public boolean isBlacked() {
        return blackDay > 0L || isPermanent();
    }
}
