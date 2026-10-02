package io.github.aw1y2z.sesame.data.task;

import io.github.aw1y2z.sesame.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子任务「磁盘存档 → 内存排程」的还原器。
 * <p>
 * {@link ModelTask.ChildModelTask} 里的执行体是 Lambda，无法序列化，所以磁盘上只存
 * {@link ChildTaskArchive}（id/group/时刻）。重启后要把它变回一个能跑的子任务，
 * 就必须有一张「group → 可执行体」的对照表，即本类。
 * <p>
 * <b>安全约定</b>：只有登记在册的 group 才会被还原。未登记的 group 一律跳过 ——
 * 因为无法确认它的执行体是否还成立（对应参数可能已失效、模块可能被关掉），
 * 盲目执行比重启后重新排程更危险。
 *
 * @see ChildTaskArchive
 * @see ChildTaskPersistence
 */
public class ChildTaskRegistry {

    /**
     * 一个可还原的排程定义。
     *
     * @param group   分组标识（与 {@link ModelTask.ChildModelTask#getGroup()} 一致）
     * @param builder 由存档重建执行体；入参是子任务 id（一般形如 {@code "FA|<farmId>"}，
     *                需要具体参数的从里面切出来）
     */
    public interface RestoreHandler {
        Runnable build(String childTaskId, long execTime);
    }

    private final Map<String, RestoreHandler> handlers = new HashMap<>();

    private static ChildTaskRegistry instance;

    private ChildTaskRegistry() {
    }

    public static synchronized ChildTaskRegistry get() {
        if (instance == null) {
            instance = new ChildTaskRegistry();
        }
        return instance;
    }

    public ChildTaskRegistry register(String group, RestoreHandler handler) {
        handlers.put(group, handler);
        return this;
    }

    public boolean supports(String group) {
        return handlers.containsKey(group);
    }

    /**
     * 把一批存档还原成子任务，塞回对应的模型任务。
     * <p>
     * 刻意做成「返回可建数量 + 由调用方决定何时 add」：{@link ModelTask#addChildTask} 会
     * 立刻起线程/定时器，在恢复阶段批量调用会把执行器瞬间打满。
     *
     * @return 实际能还原（group 已登记）的存档条数
     */
    public List<ModelTask.ChildModelTask> build(List<ChildTaskArchive> archives, ModelTask owner) {
        List<ModelTask.ChildModelTask> out = new ArrayList<>();
        if (archives == null || owner == null) {
            return out;
        }
        // 同一 group 只保留「时刻最新」的一条：
        // 像「定时睡觉」这类排程的 id 里带时刻（AS|<时刻>），每次 run 重算都会产生新 id，
        // 于是存档里可能躺着同语义的多条（昨天的睡觉 + 今天的睡觉）。
        // 全量恢复会造成重复执行/互相取消，这里按 group 去重只留最新。
        java.util.Map<String, ChildTaskArchive> latestByGroup = new java.util.HashMap<>();
        for (ChildTaskArchive a : archives) {
            if (a == null || StringUtil.isEmpty(a.getGroup())) {
                continue;
            }
            if (!handlers.containsKey(a.getGroup())) {
                continue;
            }
            ChildTaskArchive exist = latestByGroup.get(a.getGroup());
            if (exist == null || a.getExecTime() > exist.getExecTime()) {
                latestByGroup.put(a.getGroup(), a);
            }
        }
        for (ChildTaskArchive a : latestByGroup.values()) {
            try {
                Runnable r = handlers.get(a.getGroup()).build(a.getId(), a.getExecTime());
                if (r != null) {
                    out.add(new ModelTask.ChildModelTask(a.getId(), a.getGroup(), r, a.getExecTime()));
                }
            } catch (Throwable t) {
                // 单条还原失败不能影响其它排程
                io.github.aw1y2z.sesame.util.Log.printStackTrace("ChildTaskRegistry", t);
            }
        }
        return out;
    }
}
