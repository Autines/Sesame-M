package io.github.aw1y2z.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 自动拉黑记录：保存由模块自动加入黑名单的任务及其日期，用于"超期自动解禁重试"。
 * <p>
 * 键格式 {@code Module|listTitle|taskTitle}；值格式 {@code hits;lastDay;blackDay;retry}
 * （天序号，{@code blackDay=0} 表示仍在"连续命中观察期"、尚未拉黑）。
 * <p>
 * 只记录模块自动拉黑的任务，用户手动加入的黑名单不在此表内，因此不会被自动解禁。
 *
 * <h3>加载纪律（2026-09-19 修）</h3>
 * 本表是**跨进程**续用的状态：模块跑在宿主（支付宝）进程里，宿主一重启就是新进程。
 * 曾经 {@link #get(String)} 只读内存 map、从不触发加载，而全项目只有两处会加载它
 * （跨天解禁 {@code sweepExpiredBlackList}、默认项种子 {@code isAutoBlackTracked}），
 * 于是「当天没跨天」的新进程里这张表一直是空的：
 * 每次失败都被当成第 1 次命中，{@code BLACKLIST_CONFIRM_HITS}(3) 这道闸门永远跨不过去，
 * 重启一次支付宝就把计数清零 —— 服务端已经死掉的任务（农场肥料 / 会员芝麻信用）于是
 * 每次运行都重试、每次都失败，日志里刷个不停。
 * 现在读入口一律先 {@link #ensureLoaded()}，写入口 {@link #put} 保持不动
 * （避免 reload 把刚写进去还没落盘的记录冲掉）。
 */
public class AutoBlackListMap {

    private static final Map<String, String> idMap = new ConcurrentHashMap<>();

    private static final Map<String, String> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    private static volatile boolean loaded = false;

    public static Map<String, String> getMap() {
        return readOnlyIdMap;
    }

    /** 读取：缺省情况下会先把磁盘内容加载进来（跨进程续用的前提） */
    public static String get(String key) {
        ensureLoaded();
        return idMap.get(key);
    }

    public static Set<String> keys() {
        return readOnlyIdMap.keySet();
    }

    public static synchronized void put(String key, String value) {
        idMap.put(key, value);
    }

    public static synchronized void remove(String key) {
        idMap.remove(key);
    }

    /**
     * 首次访问时确保已从磁盘加载（记录只由模块自己写入，加载一次即可）。
     * <p>与 {@link #load()} 同为类级同步：二者若并发，后到的 reload 会 clear 掉
     * 前一个线程刚 put 进来、还没 save 的记录。
     */
    public static synchronized void ensureLoaded() {
        if (!loaded) {
            reloadFromDisk();
        }
    }

    /** 强制重读磁盘（跨天解禁时用），会丢弃内存中尚未落盘的改动 */
    public static synchronized void load() {
        reloadFromDisk();
    }

    private static void reloadFromDisk() {
        idMap.clear();
        loaded = true;
        try {
            String body = FileUtil.readFromFile(FileUtil.getAutoBlackListMapFile());
            if (!body.isEmpty()) {
                Map<String, String> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, String>>() {
                });
                if (newMap != null) {
                    idMap.putAll(newMap);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static synchronized boolean save() {
        return FileUtil.write2File(JsonUtil.toJsonString(idMap), FileUtil.getAutoBlackListMapFile());
    }

    public static synchronized void clear() {
        idMap.clear();
    }

    private AutoBlackListMap() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
