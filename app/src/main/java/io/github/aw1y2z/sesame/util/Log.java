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
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    /** 通用运行日志（用于 system/i 调用），tag 固定为 RUNTIME */
    private static final Logger runtimeLogger = XLog.tag("RUNTIME").printers(RUNTIME_FILE_PRINTER).build();

    /** 各模块向 runtime.log 写入时使用的专用 logger（不同 tag，同文件） */
    private static final Logger runtimeForestLogger = XLog.tag("FOREST").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeGoldenBeansLogger = XLog.tag("GOLDENBEANS").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeFarmLogger = XLog.tag("FARM").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeOtherLogger = XLog.tag("OTHER").printers(RUNTIME_FILE_PRINTER).build();

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
     * 模块日志双写（运行日志 + 分类文件）：只写开关打开的一侧，消息统一带 uid 前缀。
     * <p>前缀顺序固定为「模块 → 账号」，即 {@code [庄园] [账号1] 投喂小鸡…}：
     * 查看器把正文里**第一个非账号方括号**当成模块归属，所以模块必须排在最前，
     * 账号标识随后追加（仍是脱敏的账号简称，不带 uid 与昵称）。
     * <p>⚠️ 拼接顺序不可写成 {@code withUser(withModulePrefix(s))}：那样账号会跑到最前，
     * 变成 {@code [账号1][庄园] …}，查看器会把「账号1」误判成模块名，
     * 分组页（新村/农场/运动/会员）会因为这些日志归属不到任何模块而整页空白。
     */
    private static void writeModuleLog(String s, boolean toRuntime, Logger runtimeTarget,
                                       boolean toFile, Logger fileTarget) {
        if (!toRuntime && !toFile) {
            return;
        }
        String msg = withModulePrefix(withUser(s));
        if (toRuntime) {
            runtimeTarget.i(msg);
        }
        if (toFile) {
            fileTarget.i(msg);
        }
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
        if (toError) {
            errorLogger.i(msg);
        } else {
            runtimeLogger.i(msg);
        }
    }

    public static void i(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger.i(withModulePrefix(withUser(s)));
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
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeLogger.i(withModulePrefix(withUser(str)));
        }
    }

    /**
     * system 记录(配置加载/保存/重置等)：统一并入运行日志，不再单独写 system.&lt;date&gt;.log。
     * <p>这些调用点旁边本就有一条内容相同的 Log.i，单独建文件只是重复副本，
     * 而且查看器里也没有对应的日志类目。仍受「查看运行日志」开关控制。
     */
    public static void system(String tag, String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger.i(withModulePrefix(withUser(tag + ", " + s)));
    }

    public static void forest(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeForestLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableForestLog(), forestLogger);
    }

    public static void goldenBeans(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeGoldenBeansLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableGoldenBeansLog(), goldenBeansLogger);
    }

    public static void farm(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeFarmLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableFarmLog(), farmLogger);
    }

    public static void other(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeOtherLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableOtherLog(), otherLogger);
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
        } catch (ParseException ignored) {
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
