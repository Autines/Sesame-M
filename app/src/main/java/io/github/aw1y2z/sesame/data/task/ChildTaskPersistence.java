package io.github.aw1y2z.sesame.data.task;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 子任务排程的落盘/读盘。
 * <p>
 * 参照「森」的 {@code FarmWaitingPersistence} 做的事，但按本项目（Sesame-M）的方式实现：
 * 存 {@code config/&lt;uid&gt;/childTasks.json}，**按账号隔离**（森是按 uid 存 DataStore，
 * 但它的读写封在 {@code persistenceScope} 协程里；本项目走同步文件 IO，更简单也更好排查）。
 * <p>
 * 三个与「森」一致的关键设计：
 * <ol>
 *   <li><b>按 uid 存</b>：切号不会把上一账号的排程捡起来跑；</li>
 *   <li><b>过期即弃</b>：读出来时按「执行时刻已过 / 存档太旧」过滤，绝不执行隔夜的老排程；</li>
 *   <li><b>执行前先从磁盘摘掉</b>：避免「删了但崩在中间」导致同一排程被重复执行。</li>
 * </ol>
 */
public class ChildTaskPersistence {

    private static final String TAG = "ChildTaskPersistence";

    private static final String FILE_NAME = "childTasks.json";

    /**
     * 存档最长保留时长（毫秒）。超过就当陈旧数据丢掉。
     * <p>
     * 取 6 小时：庄园排程里最远的是「明早的定时起床/睡觉」，跨过一夜就没什么意义了，
     * 而「森」用的是 {@code max(MAX_TASK_AGE, 2×轮询间隔)}，本质是同一思路。
     */
    private static final long MAX_ARCHIVE_AGE_MS = 6L * 60 * 60 * 1000;

    /**
     * 执行时刻比存档时刻早这么多毫秒以上，就认为「已错过」不再执行。
     * <p>
     * 取 30 分钟：宿主进程可能被打断好一会儿（锁屏、息屏），回来后如果还在，
     * 那点时间窗已经没意义（投喂槽早就满了、起床也早已过点），与其打一次无效 RPC
     * 触发风控，不如丢掉、让下一轮 run() 重新排。
     */
    private static final long MISSED_TOLERANCE_MS = 30L * 60 * 1000;

    /**
     * 单账号存档条数上限（超出时丢最旧的）。
     * <p>
     * 为什么需要：像「定时睡觉」这类排程的 id 里带<b>时刻</b>（{@code "AS|" + 时刻}），
     * 而时刻是每次 run 重算的 —— 跨天/改配置就会产生新 id，旧的那条不会被业务逻辑移除。
     * 内存里它们跑完就没了，可存档文件是「全量覆盖」，如果不设上限，
     * 文件会随着每天重算不断累积僵尸条目。
     */
    private static final int MAX_ARCHIVE_COUNT = 20;

    private ChildTaskPersistence() {
    }

    /** 该账号的存档文件。 */
    public static File getArchiveFile(String userId) {
        return new File(FileUtil.getUserConfigDirectoryFile(userId), FILE_NAME);
    }

    /**
     * 读取存档，**并已过滤掉过期/错账号/已错过的条目**。
     * <p>
     * 同 key 保留 {@code execTime} 最大的那一条（后写入的排期覆盖先前的）。
     */
    public static List<ChildTaskArchive> load(String userId) {
        List<ChildTaskArchive> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        java.util.Map<String, ChildTaskArchive> byKey = new java.util.HashMap<>();
        for (ChildTaskArchive a : readRaw(userId)) {
            if (StringUtil.isEmpty(a.getId()) || StringUtil.isEmpty(a.getGroup())) {
                continue;
            }
            // 账号校验：文件按 uid 存，但 uid 也可能因换号/重装而与文件内容不同步
            if (!java.util.Objects.equals(userId, a.getUserId())) {
                continue;
            }
            // 太旧的存档直接丢
            if (now - a.getSavedTime() > MAX_ARCHIVE_AGE_MS) {
                continue;
            }
            // 错过的排程直接丢
            if (now > a.getExecTime() + MISSED_TOLERANCE_MS) {
                continue;
            }
            ChildTaskArchive exist = byKey.get(a.key());
            if (exist == null || a.getExecTime() > exist.getExecTime()) {
                byKey.put(a.key(), a);
            }
        }
        result.addAll(byKey.values());
        return result;
    }

    /**
     * 读原始 JSON，不做任何过滤。解析失败返回空表（并已记日志），
     * 这样 {@link #load} 与 {@link #remove} 两个调用方都能安全地拿到可迭代的列表。
     */
    private static List<ChildTaskArchive> readRaw(String userId) {
        List<ChildTaskArchive> raw = new ArrayList<>();
        if (StringUtil.isEmpty(userId)) {
            return raw;
        }
        File file = getArchiveFile(userId);
        if (!file.exists()) {
            return raw;
        }
        try {
            String json = FileUtil.readFromFile(file);
            if (StringUtil.isEmpty(json)) {
                return raw;
            }
            List<ChildTaskArchive> parsed = JsonUtil.copyMapper()
                    .readValue(json, new TypeReference<List<ChildTaskArchive>>() {
                    });
            if (parsed != null) {
                for (ChildTaskArchive a : parsed) {
                    if (a != null) {
                        raw.add(a);
                    }
                }
            }
        } catch (Throwable t) {
            // ⚠️ 这里必须打完整堆栈到 error 日志：之前只 Log.i 一句话，
            // 「存档解析失败」被 catch 吞掉后表面上功能正常（排程照常重排），
            // 实际上「读回自己写的文件」这条链已经废了（跨模块合并静默失效），
            // 排查时看不到任何线索。解析失败属于不该被吞掉的错误。
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "子任务存档解析失败，已忽略（下次 run 会重新排程）");
        }
        return raw;
    }

    /**
     * 写入某个模块的排程，<b>保留其它模块已存档的条目</b>。
     * <p>
     * ⚠️ 不能用「全量覆盖」：存档文件是<b>全模块共用一份</b>（一个账号一个文件），
     * 而 {@code scheduleArchive} 是每个模块各自调的 —— 若整份重写，庄园写完就把
     * 森林写的条目抹掉了。这里按 {@link ChildTaskArchive#getModelName()} 做
     * 「同模块先剔除、其它模块原样带回」的合并。
     *
     * @param modelName 发起写盘的模块名（{@code ModelTask#getName()}）
     * @return 写盘后文件里的总条数
     */
    public static int saveForModel(String userId, String modelName, List<ChildTaskArchive> archives) {
        if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(modelName)) {
            return 0;
        }
        try {
            // 读出别的模块的条目，原样带回
            List<ChildTaskArchive> merged = new ArrayList<>();
            for (ChildTaskArchive a : readRaw(userId)) {
                if (!modelName.equals(a.getModelName())) {
                    merged.add(a);
                }
            }
            if (archives != null) {
                merged.addAll(archives);
            }
            // 超上限时丢最旧的（按 execTime 升序，前面是最旧）
            if (merged.size() > MAX_ARCHIVE_COUNT) {
                merged.sort(java.util.Comparator.comparingLong(ChildTaskArchive::getExecTime));
                merged = new ArrayList<>(merged.subList(merged.size() - MAX_ARCHIVE_COUNT, merged.size()));
            }
            return save(userId, merged);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return 0;
        }
    }

    /**
     * 全量覆盖写。
     *
     * @return 磁盘上现在有几个排程
     */
    public static int save(String userId, List<ChildTaskArchive> archives) {
        if (StringUtil.isEmpty(userId)) {
            return 0;
        }
        List<ChildTaskArchive> safe = (archives == null) ? new ArrayList<>() : archives;
        try {
            String json = JsonUtil.toFormatJsonString(safe);
            if (!FileUtil.write2File(json, getArchiveFile(userId))) {
                Log.i(TAG, "子任务存档写入失败（多半是缺文件权限），本次排程重启后不会恢复");
                return 0;
            }
            return safe.size();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return 0;
        }
    }

    /**
     * 把某个排程从存档里摘掉（执行前调用，避免重放）。
     * <p>
     * 做法是读出全量、剔除、整份回写 —— 排程量是个位数到几十，全量重写比做增量补丁更不容易出错。
     * <p>
     * ⚠️ 这里读的是**未经过滤的原始全量**而不是 {@link #load}：{@code load} 会丢掉已过期条目，
     * 而「执行前摘除」必须能摘掉那条刚被判定为已错过的排程，否则它会一直躺在文件里。
     */
    public static void remove(String userId, String group, String id, long execTime) {
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        List<ChildTaskArchive> all = readRaw(userId);
        boolean changed = false;
        for (java.util.Iterator<ChildTaskArchive> it = all.iterator(); it.hasNext(); ) {
            ChildTaskArchive a = it.next();
            if (java.util.Objects.equals(a.getGroup(), group)
                    && java.util.Objects.equals(a.getId(), id)
                    && (execTime <= 0 || a.getExecTime() == execTime)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            save(userId, all);
        }
    }

    /** 清空该账号的存档（物理删文件，用于「清空所有蹲点」）。 */
    public static void wipe(String userId) {
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        File file = getArchiveFile(userId);
        if (file.exists() && !file.delete()) {
            Log.i(TAG, "子任务存档文件删除失败");
        }
    }
}
