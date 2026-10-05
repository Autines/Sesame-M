package io.github.aw1y2z.sesame.data.task;

import static io.github.aw1y2z.sesame.model.normal.base.BaseModel.taskRpcRequest;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import lombok.Getter;
import io.github.aw1y2z.sesame.data.Model;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.ModelType;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ModelTask extends Model {

    private static final Map<ModelTask, Thread> MAIN_TASK_MAP = new ConcurrentHashMap<>();

    /** 本模型的代际编号：stopTask 递增，旧代在下一个检查点作废退出，无需 join 阻塞调用方 */
    private volatile long generation = 0;

    /** 执行槽（只限本实例；重载后是新对象，跨实例靠代际令牌在检查点退出）：抢不到即放弃，不用实例锁 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static final int MODEL_COUNT = Math.max(getModelArray().length, 1);

    /**
     * 主任务线程池：每个模型占一条线程（core = 模型数），上限 2 倍模型数，饱和后回退到调用线程执行。
     * <p>SynchronousQueue 不排队，故不存在「撤销已提交但尚未开始的那一轮」这种操作。
     */
    private static final ThreadPoolExecutor MAIN_THREAD_POOL = new ThreadPoolExecutor(MODEL_COUNT, MODEL_COUNT * 2, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    private final Map<String, ChildModelTask> childTaskMap = new ConcurrentHashMap<>();

    private ChildTaskExecutor childTaskExecutor;

    @Getter
    private final Runnable mainRunnable = new Runnable() {

        private final ModelTask task = ModelTask.this;

        @Override
        public void run() {
            // 执行槽已由 startTask 抢住；此处只取快照，stopTask 递增后本代作废
            long myGen = task.generation;
            long startTime = System.currentTimeMillis();
            MAIN_TASK_MAP.put(task, Thread.currentThread());
            // 绑定本代令牌：本线程的 sleep / RPC 入口据此感知作废
            RunGeneration prevGen = RunGeneration.bind(myGen, () -> task.generation);
            // 静音计数分层：记下上层残留，本轮只统计自己这段
            int droppedPrev = Log.takeDroppedStaleLogCount();
            // 与执行槽配对：只有真正拿到槽的线程才计入 runningCount；同时驱动状态栏显示当前在跑的模块
            NotificationUtil.trackTaskStart(task.getName());
            ModelGroup group = task.getGroup();
            String prevModule = Log.beginModule(group == null ? null : group.getCode());
            Log.record("执行开始-" + task.getName());
            Log.startModuleLogCount();
            // 标记本线程所属模块：Log 写入时会自动加 [模块名] 前缀。
            // 各模块跑在各自的线程池线程上，ThreadLocal 天然隔离，并发交错也不会串味。
            Log.setCurrentModule(task.getName());
            boolean threw = false;
            try {
                task.run();
            } catch (TaskCancelledException e) {
                Log.record(task.getName() + "⏹代际已作废，本轮提前结束");
            } catch (Exception e) {
                threw = true;
                Status.noteTaskError(task.getName());
                Log.printStackTrace(e);
            } finally {
                // P0 停滞护栏：单轮执行过久记为"卡顿"，连续超时则当日暂停
                long cost = System.currentTimeMillis() - startTime;
                if (threw) {
                    // 异常 streak 已在 catch 中累加
                } else {
                    Status.resetTaskError(task.getName());
                }
                if (cost > Status.STALL_SOFT_TIMEOUT_MS) {
                    Status.noteTaskSlow(task.getName());
                } else {
                    Status.resetTaskSlow(task.getName());
                }
                // 作废期间被静音的错误日志条数一并报告，避免真故障无声丢失
                int dropped = Log.takeDroppedStaleLogCount();
                if (dropped > 0) {
                    Log.record(task.getName() + "⏹代际已作废，其间静音错误日志 " + dropped + " 条");
                }
                Log.restoreDroppedStaleLogCount(droppedPrev);
                // 还原而非清空：isSync 会在同一线程嵌套起跑，清掉会抹掉外层令牌
                RunGeneration.restore(prevGen);
                // 清空本线程模块上下文，避免线程池复用把上一轮的 [模块名] 前缀串到后续日志
                Log.setCurrentModule(null);
                // 本轮模块未产生任何动作时，在运行日志中给出提示
                if (Log.stopModuleLogCount() == 0) {
                    Log.record(task.getName() + "✅本轮无操作");
                }
                Log.record("执行结束-" + task.getName());
                Log.endModule(prevModule);
                // 身份化移除：只删本线程登记的那条，避免旧代收尾误删新一代条目
                MAIN_TASK_MAP.remove(task, Thread.currentThread());
                task.running.set(false);
                NotificationUtil.trackTaskEnd(task.getName());
            }
        }

    };

    public ModelTask() {
    }

    @Override
    public final void prepare() {
        childTaskExecutor = newTimedTaskExecutor();
    }

    public String getId() {
        return toString();
    }

    public ModelType getType() {
        return ModelType.TASK;
    }

    public abstract String getName();

    public abstract ModelFields getFields();

    public abstract Boolean check();

    public Boolean isSync() {
        return false;
    }

    public abstract void run();

    public Boolean hasChildTask(String childId) {
        return childTaskMap.containsKey(childId);
    }

    public ChildModelTask getChildTask(String childId) {
        return childTaskMap.get(childId);
    }

    public Boolean addChildTask(ChildModelTask childTask) {
        String childId = childTask.getId();
        childTask.modelTask = this;
        // 提交放锁外：池满时 CallerRunsPolicy 会让提交线程就地跑完整段子任务，放进 compute 会卡住整桶
        if (!childTaskExecutor.addChildTask(childTask)) {
            return false;
        }
        ChildModelTask oldTask = childTaskMap.put(childId, childTask);
        if (oldTask != null) {
            oldTask.cancel();
        }
        // 排程入盘：宿主进程被杀/支付宝被回收后，重启能按存档恢复，而不必整轮重扫。
        // 失败不抛 —— 排程本身已在内存里生效，落盘只是增强。
        scheduleArchive();
        return true;
    }

    public void removeChildTask(String childId) {
        ChildModelTask childTask = childTaskMap.remove(childId);
        if (childTask != null) {
            // 通知执行器放锁外，别在 CHM 桶锁内调用可能阻塞的代码
            childTaskExecutor.removeChildTask(childTask);
        }
        // 已完成的排程同步从磁盘摘掉，否则重启会把「已经做完的事」再做一遍
        scheduleArchive();
    }

    /**
     * 把当前内存里的「可持久化」子任务整份写盘。
     * <p>
     * 整份覆盖而不是逐条增量：排程量很小（个位数到几十），全量重写逻辑最不容易出错。
     * 触发点在 add/remove 两处 —— 也就是排程集合真正发生变化的时刻，
     * 不会在 {@link #run()} 的循环里反复写盘。
     */
    private void scheduleArchive() {
        String userId = UserIdMap.getCurrentUid();
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        try {
            List<ChildTaskArchive> archives = new ArrayList<>();
            for (ChildModelTask t : childTaskMap.values()) {
                if (t == null || t.getIsCancel()) {
                    continue;
                }
                // 只存「可还原」的分组：没在 ChildTaskRegistry 登记的分组，存档里躺着的条目
                // 永远不会被执行（还原器会跳过它们），存下来只是让文件越来越臃肿。
                if (!ChildTaskRegistry.get().supports(t.getGroup())) {
                    continue;
                }
                // 只有「到点才执行」的排程值得存；execTime<=0 的即时子任务恢复出来也没意义
                Long exec = t.getExecTime();
                if (exec == null || exec <= 0 || exec <= System.currentTimeMillis()) {
                    continue;
                }
                archives.add(ChildTaskArchive.of(t, getName(), userId));
            }
            if (archives.isEmpty()) {
                // 没有可存档的排程时也要把本模块旧的清掉，否则会一直留着已经作废的排程
                ChildTaskPersistence.saveForModel(userId, getName(), archives);
                return;
            }
            int total = ChildTaskPersistence.saveForModel(userId, getName(), archives);
            if (!archives.isEmpty()) {
                // 用 Log.i 而非 Log.record：record 受「查看运行日志」开关控制，开关关着就查不到，
                // 而这类「排程已落盘」是排障时最需要看到的信息。用 Log.i 无条件进 runtime 日志。
                Log.i(getName() + " 排程已存档💾[本模块 " + archives.size() + " / 共 " + total + " 项]");
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /**
     * 从磁盘恢复本模型的排程。由每个模块在 {@code run()} 开头调用。
     * <p>
     * 只会恢复 {@link ChildTaskRegistry} 登记过的 group（见其注释），
     * 且 {@link ChildTaskPersistence#load} 已过滤掉过期/错过的条目。
     *
     * @return 实际恢复的条数
     */
    public int restoreArchivedChildTasks() {
        String userId = UserIdMap.getCurrentUid();
        if (StringUtil.isEmpty(userId)) {
            return 0;
        }
        try {
            List<ChildTaskArchive> all = ChildTaskPersistence.load(userId);
            if (all.isEmpty()) {
                return 0;
            }
            // 只认本模型的存档，避免 A 模块把 B 模块的排程捡走
            List<ChildTaskArchive> mine = new ArrayList<>();
            for (ChildTaskArchive a : all) {
                if (Objects.equals(getName(), a.getModelName())) {
                    mine.add(a);
                }
            }
            if (mine.isEmpty()) {
                return 0;
            }
            List<ChildModelTask> rebuilt = ChildTaskRegistry.get().build(mine, this);
            int ok = 0;
            int skipDuplicated = 0;
            for (ChildModelTask t : rebuilt) {
                // 内存里已有一条同 id 的（这轮 run 已经排过了）→ 跳过，不覆盖更新的排期
                if (hasChildTask(t.getId())) {
                    skipDuplicated++;
                    continue;
                }
                if (addChildTaskQuiet(t)) {
                    ok++;
                }
            }
            // 未登记的 group 在 build 里就被丢了，这里补一句计数便于排障
            int unsupported = 0;
            for (ChildTaskArchive a : mine) {
                if (!ChildTaskRegistry.get().supports(a.getGroup())) {
                    unsupported++;
                }
            }
            if (unsupported > 0) {
                Log.i(getName() + " 存档里有 " + unsupported + " 项分组未登记，已跳过（不会执行）");
            }
            if (ok > 0) {
                Log.i(getName() + " 恢复存档排程♻️[" + ok + "/" + mine.size() + " 项]");
            }
            else if (!mine.isEmpty()) {
                // 全部被跳过说明本轮已重排过相同 id 的排程，属于正常情况，值得记一笔避免误判成故障
                Log.i(getName() + " 存档排程无需恢复[本轮已排 " + mine.size() + " 项]");
            }
            return ok;
        } catch (Throwable t) {
            Log.printStackTrace(t);
            return 0;
        }
    }

    /**
     * 恢复专用：入内存排程但<b>不再</b>触发一次写盘。
     * <p>
     * {@link #addChildTask} 末尾会 {@code scheduleArchive()}，而恢复的场景是
     * 「先把一批排程读回来」，每加一条就整份重写一次纯属浪费；而且恢复中途若被打断，
     * 写出去的还是旧集合。这里直接操作内存与执行器，绕开写盘。
     */
    private boolean addChildTaskQuiet(ChildModelTask childTask) {
        String childId = childTask.getId();
        childTask.modelTask = this;
        if (hasChildTask(childId)) {
            return false;
        }
        if (!childTaskExecutor.addChildTask(childTask)) {
            return false;
        }
        ChildModelTask old = childTaskMap.put(childId, childTask);
        if (old != null) {
            old.cancel();
        }
        return true;
    }

    public Integer countChildTask() {
        return childTaskMap.size();
    }

    public Boolean startTask() {
        return startTask(false);
    }

    /**
     * 启动一轮。不加 {@code synchronized}：互斥由 {@code running} 执行槽（CAS）保证，被挡住的只是执行
     * 线程自己，不像实例锁那样把整轮时间压给 {@link #stopTask()} 的调用方（宿主 onDestroy 在主线程）。
     * <p>抢槽在本方法内完成，故返回值如实反映「是否真的开始跑」：抢不到返回 false。
     */
    public Boolean startTask(Boolean force) {
        if (force) {
            stopTask();
        }
        try {
            if (!isEnable() || !check()) {
                return false;
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            Log.record(getName() + "⏭本实例上一代未退出，本轮跳过");
            return false;
        }
        // P0 全局风控暂停：命中一次风控后所有任务静默约 GLOBAL_RISK_PAUSE_MINUTES 分钟，避免空转重打被拦接口
        if (Status.isGlobalRiskControlActive()) {
            running.set(false);
            long remainMin = Status.getGlobalRiskControlRemainMs() / 60_000L;
            Log.record(getName() + "⏸全局风控暂停中（约剩 " + remainMin + " 分钟），本轮跳过");
            return false;
        }
        // P0 停滞护栏：单任务连续异常/超时达阈值，当日不再启动，防止死循环卡住线程池
        if (Status.hasFlagToday("stall::" + getName())) {
            running.set(false);
            Log.record(getName() + "⏭当日停滞护栏已触发，本轮跳过");
            return false;
        }
        try {
            if (isSync()) {
                mainRunnable.run();
            } else {
                try {
                    MAIN_THREAD_POOL.execute(mainRunnable);
                } catch (Throwable t) {
                    // 没能提交就要归还槽，否则该模型再也起不来
                    running.set(false);
                    throw t;
                }
            }
            return true;
        } catch (Exception e) {
            Log.printStackTrace(e);
            return false;
        }
    }

    public synchronized void stopTask() {
        // 递增代际：旧代在下一个检查点自行退出，无需 join 阻塞调用方。
        // 令牌只覆盖模型主线程；子任务线程未绑定，其取消仍依赖 cancel() 与 clearAllChildTask()
        generation++;
        for (ChildModelTask childModelTask : childTaskMap.values()) {
            try {
                childModelTask.cancel();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
        if (childTaskExecutor != null) {
            childTaskExecutor.clearAllChildTask();
        }
        childTaskMap.clear();
        // 身份化移除：只删自己登记的那条线程，避免误删新一代条目
        Thread running = MAIN_TASK_MAP.get(this);
        if (running != null) {
            MAIN_TASK_MAP.remove(this, running);
        }
    }

    public static void startAllTask() {
        startAllTask(false);
    }

    public static void startAllTask(Boolean force) {
        // P0 全局风控暂停：整轮静默，不再逐个启动任何任务
        if (Status.isGlobalRiskControlActive()) {
            long remainMin = Status.getGlobalRiskControlRemainMs() / 60_000L;
            Log.record("⏸全局风控暂停中（约剩 " + remainMin + " 分钟），本轮全部任务跳过");
            return;
        }
        // 整轮开始：清零本轮收取能量。单分组执行（startGroupTask）不算整轮，故不在此清零
        NotificationUtil.startRound();
        //自动触发备份配置文件
        if (!Status.hasFlagToday("Config::backup")) {
            FileUtil.backupConfigV2WithRolling(UserIdMap.getCurrentUid());
            Status.flagToday("Config::backup");
        }
        //执行BaseModel中自定义执行请求
        taskRpcRequest();
        for (Model model : getModelArray()) {
            // 已停止就不再启动后续模型，否则 MAIN_TASK 会把整轮跑完
            if (RunGeneration.isStale()) {
                Log.record("⏹已停止，本轮不再启动后续任务");
                return;
            }
            if (model != null) {
                if (ModelType.TASK == model.getType()) {
                    if (((ModelTask) model).startTask(force)) {
                        // 带代际检查的睡眠：停止时在下一个边界结束整轮（未绑定时同 Thread.sleep）
                        try {
                            TimeUtil.sleep(750);
                        } catch (TaskCancelledException e) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * 只执行指定分组下的任务（{@code groupCode} 取 {@link ModelGroup#getCode()}）。
     * <p>配置页的"执行"按钮必须通过广播交给<strong>注入进程</strong>跑：模块 App 自己的进程没有
     * libxposed 类（{@code ApplicationHook}/{@code hook.Toast}/{@code NotificationUtil} 一碰就是
     * NoClassDefFoundError），而且那是个 UI 进程，把任务循环压在主线程上会直接卡死界面。
     *
     * @return 实际触发的任务数
     */
    public static int startGroupTask(String groupCode) {
        List<Model> targets = new ArrayList<>();
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType()) {
                continue;
            }
            ModelGroup group = model.getGroup();
            if (group == null || !groupCode.equals(group.getCode())) {
                continue;
            }
            targets.add(model);
        }
        if (targets.isEmpty()) {
            return 0;
        }
        // 手动执行分组也算新一轮：先清零本轮收取能量，再起跑
        NotificationUtil.startRound();
        int count = 0;
        for (Model model : targets) {
            if (((ModelTask) model).startTask(false)) {
                count++;
            }
        }
        return count;
    }

    public static void stopAllTask() {
        for (Model model : getModelArray()) {
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }
    }

    private ChildTaskExecutor newTimedTaskExecutor() {
        ChildTaskExecutor childTaskExecutor;
        Integer timedTaskModel = BaseModel.getTimedTaskModel().getValue();
        if (timedTaskModel == BaseModel.TimedTaskModel.SYSTEM) {
            childTaskExecutor = new SystemChildTaskExecutor();
        } else if (timedTaskModel == BaseModel.TimedTaskModel.PROGRAM) {
            childTaskExecutor = new ProgramChildTaskExecutor();
        } else {
            throw new RuntimeException("not found childTaskExecutor");
        }
        return childTaskExecutor;
    }

    public static class ChildModelTask implements Runnable {

        @Getter
        private ModelTask modelTask;

        @Getter
        private final String id;

        @Getter
        private final String group;

        private final Runnable runnable;

        @Getter
        private final Long execTime;

        private CancelTask cancelTask;

        @Getter
        private Boolean isCancel = false;

        public ChildModelTask() {
            this(null, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id) {
            this(id, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id, String group) {
            this(id, group, () -> {
            }, 0L);
        }

        protected ChildModelTask(String id, long execTime) {
            this(id, null, null, execTime);
        }

        /*protected ChildModelTask(String id, String group, Long time) {
            this(id, group, null, time);
        }*/

        public ChildModelTask(String id, Runnable runnable) {
            this(id, null, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable) {
            this(id, group, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable, Long execTime) {
            if (StringUtil.isEmpty(id)) {
                id = toString();
            }
            if (StringUtil.isEmpty(group)) {
                group = "DEFAULT";
            }
            if (runnable == null) {
                runnable = setRunnable();
            }
            this.id = id;
            this.group = group;
            this.runnable = runnable;
            this.execTime = execTime;
        }

        public Runnable setRunnable() {
            return null;
        }

        public final void run() {
            // 子任务线程继承所属模块：这些流程日志也带模块 tag（嵌套时恢复上层）
            ModelTask owner = modelTask;
            ModelGroup group = owner == null ? null : owner.getGroup();
            String prevModule = Log.beginModule(group == null ? null : group.getCode());
            try {
                runnable.run();
            } finally {
                Log.endModule(prevModule);
            }
        }

        protected void setCancelTask(CancelTask cancelTask) {
            this.cancelTask = cancelTask;
        }

        public final void cancel() {
            if (cancelTask != null) {
                try {
                    cancelTask.cancel();
                    isCancel = true;
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }

    }

    public interface CancelTask {

        void cancel();

    }

}
