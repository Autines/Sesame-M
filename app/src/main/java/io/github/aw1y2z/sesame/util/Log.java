package io.github.aw1y2z.sesame.util;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.clean.NeverCleanStrategy;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Log {

    static {
        XLog.init(LogLevel.ALL);
    }

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }

    };

    public static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        }

    };

    public static final ThreadLocal<SimpleDateFormat> OTHER_DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
        }

    };

    /**
     * 当前线程正在执行的模块名（由 ModelTask 在 run() 前置位、finally 清除）。
     *
     * 用 InheritableThreadLocal 而非普通 ThreadLocal：模块主逻辑跑在 mainRunnable 线程上
     * （此处已 set 模块名），而 GameTask.report() 等用 {@code new Thread()} 起的异步子线程里仍要
     * 打 {@code Log.other("游戏进度📈"...)}，裸线程不继承 ThreadLocal 会让这些日志丢掉 [模块名] 前缀、
     * 在查看端归不到子模块（整页 fallback 成「其他记录」）。改成可继承后，模块执行期间新建的
     * 子线程自动带上模块名，无需在每个调用点手工补前缀。
     *
     * 各模块线程池（MAIN_THREAD_POOL / 子任务执行器）的线程仍由各自逻辑「显式 set + finally 还原」
     * 管理，隔离性不依赖 TL 不继承，故不受影响；Inheritable 只影响「模块线程上新建的线程」这一路径。
     *
     * 写入日志时会作为前缀拼在正文上，格式：`[庄园] 肥料领取...`。
     * 查看端据此把同一分组的多个模块拆开过滤。
     */
    private static final ThreadLocal<String> CURRENT_MODULE = new InheritableThreadLocal<>();

    /**
     * 标记当前线程所属模块；传入 null 或空串表示清除。
     * 由 ModelTask.mainRunnable 在模块执行前后调用。
     */
    public static void setCurrentModule(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            CURRENT_MODULE.remove();
        } else {
            CURRENT_MODULE.set(moduleName);
        }
    }

    /** 取当前线程所属模块名；不在任何模块上下文中时返回 null */
    public static String getCurrentModule() {
        return CURRENT_MODULE.get();
    }

    /**
     * 给日志正文加上当前模块前缀。
     * 不在模块上下文中（如界面进程的配置加载日志）则原样返回。
     */
    private static String withModulePrefix(String s) {
        String module = CURRENT_MODULE.get();
        if (module == null || module.isEmpty()) {
            return s;
        }
        return "[" + module + "] " + s;
    }

    /**
     * runtime 日志文件的共用打印机。
     * <p>5 个 tag（RUNTIME / FOREST / GOLDENBEANS / FARM / OTHER）共写同一个 runtime 文件，
     * 共用一组缓冲与后台 worker，避免多个 printer 同时写同一文件带来的行交错与线程浪费。
     */
    private static final Printer RUNTIME_FILE_PRINTER = new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
            .fileNameGenerator(new CustomDateFileNameGenerator("runtime"))
            .backupStrategy(new NeverBackupStrategy())
            .cleanStrategy(new NeverCleanStrategy())
            .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
            .build();

    /** 通用运行日志（不在模块上下文时使用），tag 为 RUNTIME */
    private static final Logger runtimeLogger = XLog.tag("RUNTIME").printers(RUNTIME_FILE_PRINTER).build();

    /**
     * 当前线程所属模块（{@code ModelGroup.getCode()}，如 FOREST/FARM/…）。
     * <p>只决定流程日志在运行日志里的 tag：查看器的 tag 过滤条据此按模块切分流程。
     * 模块结果不经过这里，它们只写各自的分类文件。
     */
    private static final ThreadLocal<String> MODULE_TAG = new ThreadLocal<>();

    /** 按 tag 缓存的运行日志 logger：同一个文件、同一组缓冲，只是 tag 不同 */
    private static final Map<String, Logger> RUNTIME_TAG_LOGGERS = new ConcurrentHashMap<>();

    private static Logger runtimeLoggerOf(String tag) {
        if (StringUtil.isEmpty(tag)) {
            return runtimeLogger;
        }
        Logger logger = RUNTIME_TAG_LOGGERS.get(tag);
        if (logger == null) {
            logger = XLog.tag(tag).printers(RUNTIME_FILE_PRINTER).build();
            RUNTIME_TAG_LOGGERS.put(tag, logger);
        }
        return logger;
    }

    /**
     * 进入模块上下文：本线程后续的流程日志带上该模块 tag。
     *
     * @return 上一个 tag（可能为 null），收尾时原样传回 {@link #endModule} 以支持嵌套
     */
    public static String beginModule(String moduleTag) {
        String prev = MODULE_TAG.get();
        if (StringUtil.isEmpty(moduleTag)) {
            MODULE_TAG.remove();
        } else {
            MODULE_TAG.set(moduleTag);
        }
        return prev;
    }

    /** 恢复模块上下文；{@code prev} 为 null 表示退出最外层（回落到 RUNTIME tag） */
    public static void endModule(String prev) {
        if (StringUtil.isEmpty(prev)) {
            MODULE_TAG.remove();
        } else {
            MODULE_TAG.set(prev);
        }
    }

    /**
     * 写运行日志：受「查看运行日志」开关控制，tag 取当前模块（无则 RUNTIME）。
     * <p>本地定制：正文同时带中文模块前缀（{@code [庄园] …}），供 {@code MiuixLogViewerActivity}
     * 按正文做分组收口；上游的 tag 机制（{@code FOREST/FARM/…}）另存一份供 tag 过滤。
     * 两者并存是刻意的 —— 丢掉任一都会让某一套查看方式失效。
     */
    private static void runtimeWrite(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLoggerOf(MODULE_TAG.get()).i(withModulePrefix(withUser(s)));
    }

    private static final Logger debugLogger = XLog.tag("DEBUG").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("debug"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger forestLogger = XLog.tag("FOREST").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("forest"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger goldenBeansLogger = XLog.tag("GOLDENBEANS").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("goldenbeans"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger farmLogger = XLog.tag("FARM").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("farm"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger otherLogger = XLog.tag("OTHER").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("other"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger errorLogger = XLog.tag("ERROR").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("error"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    /**
     * 当前账号简称（账号1、账号2…），由 {@code UserIdMap} 在 uid 变化时写入；未知时为 null。
     * <p>刻意缓存成普通字段，而不是每条日志回头去问 UserIdMap：本模块的 UI 进程里没有
     * libxposed API，而 UserIdMap 引用了 ApplicationHook（继承 XposedModule），在 UI 进程里
     * 触达它可能抛 NoClassDefFoundError 把界面搞崩。日志是全项目最高频的调用，
     * 不能背这个依赖。
     */
    private static volatile String accountLabel = null;

    public static void setAccountLabel(String label) {
        accountLabel = StringUtil.isEmpty(label) ? null : label;
    }

    /**
     * 统一日志写入口：在消息前加上账号简称（账号1、账号2…），便于多账号下区分日志来源。
     * <p>序号由 {@code UserIdMap} 首次出现时分配并持久化，与配置页显示的账号序号一致；
     * 日志里**不写 uid、也不写昵称**，避免日志被分享/导出时把账号信息带出去。
     * <p>查看器按「时间 tag: 正文」解析，前缀会落在正文里，不影响解析。
     */
    private static String withUser(String msg) {
        String label = accountLabel;
        return label == null ? msg : "[" + label + "]" + msg;
    }

    /**
     * 模块结果日志：只写各自的分类文件。
     * <p>运行日志不再双写一份——它只承载流程（模块线程的流程日志由 {@link #beginModule} 决定 tag），
     * 模块结果统一到分类页查看。
     * <p>⚠️ 本地定制：正文仍带「模块 → 账号」中文前缀（如 {@code [庄园] [账号1] 投喂小鸡…}），
     * 因为查看器 {@code MiuixLogViewerActivity} 按正文里第一个非账号方括号做**分组收口**；
     * 拼接顺序不可写成 {@code withUser(withModulePrefix(s))}，那样「账号1」会被误判成模块名，
     * 分组页（新村/农场/运动/会员）整页空白。
     */
    private static void writeModuleLog(String s, boolean toFile, Logger fileTarget) {
        if (!toFile) {
            return;
        }
        fileTarget.i(withModulePrefix(withUser(s)));
    }

    /** 本线程在「代际作废」期间被静音的错误日志条数 */
    private static final ThreadLocal<Integer> staleLogDropped = ThreadLocal.withInitial(() -> 0);

    /** 取出并清零本线程被静音的错误日志条数 */
    public static int takeDroppedStaleLogCount() {
        int dropped = staleLogDropped.get();
        staleLogDropped.set(0);
        return dropped;
    }

    /** 还原上层残留计数：嵌套执行时与 {@link RunGeneration#restore} 一样分层，数字不串 */
    public static void restoreDroppedStaleLogCount(int prev) {
        if (prev == 0) {
            staleLogDropped.remove();
        } else {
            staleLogDropped.set(prev);
        }
    }

    /**
     * 错误日志双写（异常日志 + 运行日志）：消息统一带 uid 前缀。
     * <p>同样按「模块 → 账号」顺序加前缀，便于直接看出是哪个模块抛的。
     */
    private static void writeError(String s) {
        // 本代已作废：此后本线程的错误日志都是收尾噪音（成片 catch(Throwable) 各打一行），整段静音；
        // 丢弃条数累计，任务收尾时报告，避免把这段时间的真故障无声吞掉
        if (RunGeneration.isStale()) {
            staleLogDropped.set(staleLogDropped.get() + 1);
            return;
        }
        boolean toError = io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog();
        boolean toRuntime = io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog();
        if (!toError && !toRuntime) {
            return;
        }
        String msg = withModulePrefix(withUser(s));
        // 优先只写异常日志：原先「异常 + 运行」双写，同一条记录在两个文件里各存一份
        // （实测 error.<date>.log 里 99% 的行在 runtime.<date>.log 中重复）。
        // 仅当用户关掉「查看异常日志」时，才退回写运行日志，避免错误彻底不可见。
        // ⚠️ 本地定制保留：上游 v1.2.8 改回无条件双写，会把上面这条优化倒退，故不采纳。
        if (toError) {
            errorLogger.i(msg);
        } else {
            runtimeLoggerOf(MODULE_TAG.get()).i(msg);
        }
    }

    public static void i(String s) {
        runtimeWrite(s);
    }

    public static void i(String tag, String s) {
        i(tag + ", " + s);
    }

    /**
     * 当前任务线程的模块日志计数：用于判断某模块本轮是否产生了实际动作。
     * 计数与各日志开关无关，开关关闭时同样计数。
     */
    private static final ThreadLocal<int[]> MODULE_LOG_COUNTER = new ThreadLocal<>();

    /**
     * 开始统计当前线程的模块日志条数（由 ModelTask 在模块 run() 前调用）
     */
    public static void startModuleLogCount() {
        MODULE_LOG_COUNTER.set(new int[]{0});
    }

    /**
     * 结束统计并返回当前线程的模块日志条数
     */
    public static int stopModuleLogCount() {
        int[] counter = MODULE_LOG_COUNTER.get();
        MODULE_LOG_COUNTER.remove();
        return counter == null ? 0 : counter[0];
    }

    private static void countModuleLog() {
        int[] counter = MODULE_LOG_COUNTER.get();
        if (counter != null) {
            counter[0]++;
        }
    }

    public static void record(String str) {
        countModuleLog();
        // 记录日志(record)已停用,只按「查看运行日志」开关写入运行日志
        runtimeWrite(str);
    }

    /**
     * system 记录(配置加载/保存/重置等)：统一并入运行日志，不再单独写 system.&lt;date&gt;.log。
     * <p>这些调用点旁边本就有一条内容相同的 Log.i，单独建文件只是重复副本，
     * 而且查看器里也没有对应的日志类目。仍受「查看运行日志」开关控制。
     */
    public static void system(String tag, String s) {
        runtimeWrite(tag + ", " + s);
    }

    public static void forest(String s) {
        countModuleLog();
        // 本地定制：forest.log 与 farm/other 一样，现在同时收多个分组
        // （上游 e811eb0f 拆组后 = 森林 + 保护[古树/保护生态] + 物种[神奇动物]），
        // 开关是按分组给的，故先查当前模块属于哪个分组再决定是否落盘（见 allowCategoryFile）。
        //
        // ⚠️ 无模块上下文时不能直接交给 allowCategoryFile —— 它的 null 分支返回 true（放行），
        //    那会让这类日志绕过「森林日志」开关（旧行为是受它管的）。这里显式回落旧行为。
        String module = getCurrentModule();
        boolean allow = (module == null || module.isEmpty())
                ? io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableForestLog()
                : allowCategoryFile(module);
        writeModuleLog(s, allow, forestLogger);
    }

    public static void goldenBeans(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableGoldenBeansLog(), goldenBeansLogger);
    }

    /**
     * 该模块是否允许写入分类文件。
     *
     * <p>分类文件是按**业务域**切的，一个文件里装着多个分组：farm.log 同时收
     * 庄园（AntFarm）/ 新村（AntStall）/ 农场（AntOrchard），other.log 同时收
     * 运动（AntSports）/ 会员（AntMember）等。而开关是**按分组**给的，
     * 所以写文件前要先看"当前是哪个模块"，再查它所属分组的那个开关。
     *
     * <p>归属靠当前线程的模块名（{@link #getCurrentModule()}，由 ModelTask 在模块
     * 执行线程内 {@code set(task.getName())}）。⚠️ 它取的是**模块的显示名（中文）**，
     * 不是类简名 —— 如「庄园」「新村」「农场」「运动」「会员」，故 switch 用中文匹配。
     * 不在模块上下文中（界面进程、或模块未登记）时**放行** ——
     * 宁可多写一条，也不要因为认不出归属就把日志吞掉。
     *
     * <p>关闭开关只影响**以后**是否写；已经写进文件的历史日志保留不动
     * （同一个文件里还混着兄弟分组的日志，按文件清空会误伤）。
     */
    private static boolean allowCategoryFile(String module) {
        io.github.aw1y2z.sesame.data.AppConfig config = io.github.aw1y2z.sesame.data.AppConfig.INSTANCE;
        if (module == null || module.isEmpty()) {
            return true;
        }
        switch (module) {
            case "庄园":
                return config.getEnableFarmLog();
            case "新村":
                return config.isEnableStallLog();
            case "农场":
                return config.isEnableOrchardLog();
            case "运动":
                return config.isEnableSportsLog();
            case "会员":
                return config.isEnableMemberLog();
            case "森林":
                return config.getEnableForestLog();
            case "金豆夺宝":
                return config.getEnableGoldenBeansLog();
            // ── 2026-10-09：上游 e811eb0f 拆出的新分组，各用自己的开关 ──
            // 不列在这里的话它们会落到 default，与「其他」共用一个开关（拨一个动两行）。
            // 「古树」「保护生态」同属保护组，共用一个开关；「AI答」不在此列 ——
            // 它的日志只有 Log.record（写运行日志），开关即 enableViewRuntimeLog。
            case "古树":
            case "保护":
                return config.isEnableProtectLog();
            case "物种":
                return config.isEnableSpeciesLog();
            case "海洋":
                return config.isEnableOceanLog();
            case "经营":
                return config.isEnableGreenFinanceLog();
            default:
                return config.getEnableOtherLog();
        }
    }

    public static void farm(String s) {
        countModuleLog();
        // 本地定制：farm.log 同时收「庄园/新村/农场」三个分组，开关是按分组给的，
        // 故写文件前先看当前模块属于哪个分组，再查它自己的开关（见 allowCategoryFile）。
        writeModuleLog(s, allowCategoryFile(getCurrentModule()), farmLogger);
    }

    public static void other(String s) {
        countModuleLog();
        // 同上：other.log 收「运动/会员」等，按当前模块所属分组的开关决定是否落盘。
        writeModuleLog(s, allowCategoryFile(getCurrentModule()), otherLogger);
    }

    /**
     * 模块收尾汇总：按当前线程所属模块（{@link #MODULE_TAG}）路由到对应的分类文件。
     * <p>用于 {@code ModelTask} 在模块 run() 结束后打「本轮完成 / 本轮无操作」，是各模块结果汇总的
     * 唯一入口——新增模块只要在 {@code ModelGroup} 里给了 code，这里自动选对分类文件。
     * <p>路由：森林/物种（FOREST/SPECIES）→ forest；庄园/新村/农场（FARM/STALL/ORCHARD）→ farm；
     * 金豆（GOLDENBEANS）→ goldenBeans；其余（海洋/运动/会员/经营/保护/AI/其他）→ other。
     */
    /** 本线程本轮是否还有未处理完的任务（模块可显式声明，见 {@link #markUnresolved}） */
    private static final ThreadLocal<Boolean> MODULE_UNRESOLVED = ThreadLocal.withInitial(() -> false);

    /** 重置本轮未完成标记（由 {@code ModelTask} 在模块 run() 前调用） */
    public static void resetUnresolved() {
        MODULE_UNRESOLVED.set(false);
    }

    /** 模块声明本轮仍有待完成/待领取的任务，收尾汇总会据此打「⏳仍有待处理」 */
    public static void markUnresolved() {
        MODULE_UNRESOLVED.set(true);
    }

    /** 本线程本轮是否有未完成的任务 */
    public static boolean isUnresolved() {
        Boolean v = MODULE_UNRESOLVED.get();
        return v != null && v;
    }

    public static void moduleSummary(String s) {
        String tag = MODULE_TAG.get();
        if ("FOREST".equals(tag) || "SPECIES".equals(tag)) {
            forest(s);
        } else if ("FARM".equals(tag) || "STALL".equals(tag) || "ORCHARD".equals(tag)) {
            farm(s);
        } else if ("GOLDENBEANS".equals(tag)) {
            goldenBeans(s);
        } else {
            other(s);
        }
    }

    public static void debug(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableDebugLog()) {
            return;
        }
        debugLogger.d(withModulePrefix(withUser(s)));
    }

    public static void error(String s) {
        writeError(s);
    }

    public static void printStackTrace(Throwable t) {
        // 代际作废不是故障：不写堆栈，避免切号/重载时在每个剩余动作上刷屏
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(android.util.Log.getStackTraceString(t));
    }

    public static void printStackTrace(String tag, Throwable t) {
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(tag + ", " + android.util.Log.getStackTraceString(t));
    }

    /**
     * 记录异常：一次调用同时写异常日志与运行日志，替代成对出现的
     * {@code Log.i(TAG, "xxx err:"); Log.printStackTrace(TAG, t);}。
     * <p>原先那种写法会占两行、只写其中一行时不易察觉，且运行日志里同一个异常会出现两行。
     *
     * @param tag 标签（通常传 TAG）
     * @param msg 说明，如 "answerQuestion err:"
     * @param t   异常
     */
    public static void err(String tag, String msg, Throwable t) {
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(tag + ", " + msg + "\n" + android.util.Log.getStackTraceString(t));
    }

    public static String getLogFileName(String logName) {
        SimpleDateFormat sdf = DATE_FORMAT_THREAD_LOCAL.get();
        if (sdf == null) {
            sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
        return logName + "." + sdf.format(new Date()) + ".log";
    }

    public static String getFormatDateTime() {
        SimpleDateFormat simpleDateFormat = DATE_TIME_FORMAT_THREAD_LOCAL.get();
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        }
        return simpleDateFormat.format(new Date());
    }

    public static String getFormatDate() {
        return getFormatDateTime().split(" ")[0];
    }

    public static String getFormatTime() {
        return getFormatDateTime().split(" ")[1];
    }

    /* //日期转换为时间戳 */
    public static long timeToStamp(String timers) {
        Date d = new Date();
        long timeStamp;
        try {
            SimpleDateFormat simpleDateFormat = OTHER_DATE_TIME_FORMAT_THREAD_LOCAL.get();
            if (simpleDateFormat == null) {
                simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
            }
            Date newD = simpleDateFormat.parse(timers);
            if (newD != null) {
                d = newD;
            }
        } catch (ParseException e) {
            // 解析不了就退回「当前时间」：调用方仍拿到可用值，但要留痕
            debug("时间解析失败，退回当前时间: " + timers);
        }
        timeStamp = d.getTime();
        return timeStamp;
    }

    public static class CustomDateFileNameGenerator implements FileNameGenerator {

        ThreadLocal<SimpleDateFormat> mLocalDateFormat = new ThreadLocal<SimpleDateFormat>() {

            @Override
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }

        };

        private final String name;

        public CustomDateFileNameGenerator(String name) {
            this.name = name;
        }

        @Override
        public boolean isFileNameChangeable() {
            return true;
        }

        /**
         * Generate a file name which represent a specific date.
         */
        @Override
        public String generateFileName(int logLevel, long timestamp) {
            SimpleDateFormat sdf = mLocalDateFormat.get();
            if (sdf == null) {
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }
            return name + "." + sdf.format(new Date(timestamp)) + ".log";
        }
    }

}
