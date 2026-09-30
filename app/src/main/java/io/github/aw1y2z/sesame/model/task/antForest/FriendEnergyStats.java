package io.github.aw1y2z.sesame.model.task.antForest;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 好友能量模式统计（参考 Sen UserEnergyPatternManager 思路；Sesame 自有实现，未复制其源码）。
 * <p>
 * 用指数滑动平均记录每个好友"本次查询是否有能量可收"的成功率，用于跳过长期无能量的好友，
 * 减少无意义的 RPC 请求（与已存在的「精准等待采集 BubbleTimerTask」「好友护盾跳过」互补）。
 * <p>
 * 设计约束：
 * - 统计落盘到 friendEnergyStats.json（按账号分目录），跨重启累积，解决"纯内存重启即清空、几乎永不触发"的问题。
 * - 任何异常都被吞掉，绝不影响森林采集主流程。
 * - 每天至少给一次复核机会，避免把"偶尔有能量"的好友永久跳过。
 * - 兼容旧版：无该文件时 stats 为空，行为与旧版一致（首轮不跳过）。
 */
public final class FriendEnergyStats {
    private static final String TAG = FriendEnergyStats.class.getSimpleName();

    private static final int MIN_ATTEMPTS = 5;                       // 至少观察这么多次才下结论
    private static final double LOW_RATE = 0.1;                      // 成功率低于此视为"长期无能量"
    private static final long RECHECK_INTERVAL_MS = 24L * 60 * 60 * 1000; // 每天至少复核一次

    private static final Map<String, Stat> stats = new ConcurrentHashMap<>();

    // 落盘加载标记：同一账号只从磁盘加载一次，避免本轮内存累积被覆盖
    private static boolean loaded = false;
    private static String loadedAccountId = null;

    private FriendEnergyStats() {
    }

    /**
     * 从磁盘加载指定账号的好友能量统计（跨重启累积）。
     * 同一账号且内存已有数据时不再重复覆盖，保留本轮正在累积的样本。
     */
    public static void load(String accountId) {
        try {
            if (accountId == null) {
                return;
            }
            if (loaded && accountId.equals(loadedAccountId) && !stats.isEmpty()) {
                return;
            }
            loadedAccountId = accountId;
            loaded = true;
            File f = FileUtil.getFriendEnergyStatsFile(accountId);
            String json = FileUtil.readFromFile(f);
            stats.clear();
            if (json == null || json.isEmpty()) {
                return;
            }
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("stats");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id");
                if (id == null || id.isEmpty()) {
                    continue;
                }
                Stat s = new Stat();
                s.successRate = o.optDouble("rate", 0.5);
                s.attempts = o.optInt("attempts", 0);
                s.lastAttemptMs = o.optLong("lastAttempt", 0);
                s.lastCollectMs = o.optLong("lastCollect", 0);
                stats.put(id, s);
            }
            if (!stats.isEmpty()) {
                Log.record("好友能量统计已从磁盘加载 " + stats.size() + " 条");
            }
        } catch (Throwable t) {
            // 加载失败：留空统计，不影响采集
            stats.clear();
        }
    }

    /**
     * 把当前内存统计落盘到 friendEnergyStats.json（按账号分目录）。
     */
    public static void save(String accountId) {
        try {
            if (accountId == null) {
                return;
            }
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, Stat> e : stats.entrySet()) {
                Stat s = e.getValue();
                JSONObject o = new JSONObject();
                o.put("id", e.getKey());
                o.put("rate", s.successRate);
                o.put("attempts", s.attempts);
                o.put("lastAttempt", s.lastAttemptMs);
                o.put("lastCollect", s.lastCollectMs);
                arr.put(o);
            }
            root.put("stats", arr);
            FileUtil.write2File(root.toString(), FileUtil.getFriendEnergyStatsFile(accountId));
        } catch (Throwable t) {
            // 保存失败绝不影响采集
        }
    }

    /**
     * 记录一次对该好友的采集尝试结果。
     *
     * @param userId       好友 userId
     * @param collectedAny 本次是否采集到能量（或存在即将成熟的能量球）
     */
    public static void recordAttempt(String userId, boolean collectedAny) {
        try {
            if (userId == null) {
                return;
            }
            Stat s = stats.computeIfAbsent(userId, k -> new Stat());
            // 成功率指数滑动平均：成功 → ×0.9+0.1，失败 → ×0.9（权重参考 Sen 的 0.9/0.1）
            double rate = s.successRate;
            rate = collectedAny ? rate * 0.9 + 0.1 : rate * 0.9;
            if (rate < 0) {
                rate = 0;
            }
            if (rate > 1) {
                rate = 1;
            }
            s.successRate = rate;
            s.attempts++;
            s.lastAttemptMs = System.currentTimeMillis();
            if (collectedAny) {
                s.lastCollectMs = s.lastAttemptMs;
            }
        } catch (Throwable t) {
            // 统计失败绝不影响采集
        }
    }

    /**
     * 是否应跳过该好友（长期无能量且近期已复核过）。
     *
     * @return true=跳过
     */
    public static boolean shouldSkip(String userId) {
        try {
            Stat s = stats.get(userId);
            if (s == null || s.attempts < MIN_ATTEMPTS) {
                return false; // 数据不足，不跳过
            }
            if (s.successRate > LOW_RATE) {
                return false; // 有时有能量，不跳过
            }
            // 低成功率：若 24h 内已复核过则跳过；超过 24h 给一次复核机会，避免永久跳过
            return System.currentTimeMillis() - s.lastAttemptMs < RECHECK_INTERVAL_MS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class Stat {
        double successRate = 0.5; // 初始中性，避免新好友一上来就被全跳
        int attempts = 0;
        long lastAttemptMs = 0;
        long lastCollectMs = 0;
    }
}
