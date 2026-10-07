package io.github.aw1y2z.sesame.util;

import com.fasterxml.jackson.databind.JsonMappingException;

import lombok.Data;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelField;

import java.io.File;
import java.util.*;

@Data
public class Status {
    
    private static final String TAG = Status.class.getSimpleName();
    
    public static final Status INSTANCE = new Status();

    /** P0 全局风控暂停：命中一次风控挑战后，所有任务统一暂停的时长（分钟） */
    public static final int GLOBAL_RISK_PAUSE_MINUTES = 30;
    /** P0 停滞护栏：同一任务连续异常轮次达到该值，当日暂停该任务 */
    public static final int STALL_MAX_ERROR_ROUNDS = 3;
    /** P0 停滞护栏：单轮执行软超时（毫秒），超过视为"卡顿" */
    public static final long STALL_SOFT_TIMEOUT_MS = 10L * 60 * 1000;
    /** P0 停滞护栏：连续软超时轮次达到该值，当日暂停该任务 */
    public static final int STALL_MAX_SLOW_ROUNDS = 2;

    /** 帮喂好友/家庭成员：当日总次数已达上限（服务端返回 resultCode=391），全局标记 */
    public static final String FLAG_FEED_FRIEND_ANIMAL_LIMIT = "farm::feedFriendAnimalLimit";
    
    // forest
    private final Map<String, Integer> waterFriendLogList = new HashMap<>();
    private final Map<String, Integer> wateredFriendLogList = new HashMap<>();
    private final Map<String, Integer> wateringFriendLogList = new HashMap<>();
    private final Map<String, Integer> forestHuntHelpLogList = new HashMap<>();
    private final Map<String, Integer> vitality_ExchangeBenefitLogList = new HashMap<>();
    private final Map<Integer, Integer> exchangeReserveLogList = new HashMap<>();
    private final Set<String> ancientTreeCityCodeList = new HashSet<>();
    
    private int doubleTimes = 0;
    
    // farm
    private final Map<String, Integer> feedFriendLogList = new HashMap<>();
    private final Map<String, Integer> visitFriendLogList = new HashMap<>();
    private final Map<String, Integer> gameCenterBuyMallItemList = new HashMap<>();
    private int useAccelerateToolCount = 0;
    private int useSpecialFoodCount = 0;
    
    // orchard
    private final Set<String> orchardShareP2PLogList = new HashSet<>();
    
    // stall
    private final Map<String, Integer> stallHelpedCountLogList = new HashMap<>();
    private final Set<String> stallShareP2PLogList = new HashSet<>();
    
    // member
    private final Set<String> memberPointExchangeBenefitLogList = new HashSet<>();
    
    // other
    private final Set<String> flagLogList = new HashSet<>();

    /** P0 停滞护栏：任务名 -> 连续异常轮次（进程内计数，跨天随 status 重置） */
    private final Map<String, Integer> taskErrorStreak = new HashMap<>();
    /** P0 停滞护栏：任务名 -> 连续软超时轮次 */
    private final Map<String, Integer> taskSlowStreak = new HashMap<>();
    /**
     * 上面两个 streak 归属的日期（yyyy-MM-dd）。
     * <p>跨天后第一轮 note 时自动把 streak 清零重算，避免"昨天的连续计数"叠到今天的阈值上。
     */
    private String streakDate;
    /**
     * P0 停滞护栏：任务名 -> 触发停滞护栏的“日期”（yyyy-MM-dd）。
     * <p>把日期随标记一起持久化，判定时只认“今天”写下的那条，跨天自然失效。
     * <p>历史实现把未带日期的 stall 标记塞进 {@link #flagLogList}，清理完全依赖每日重置；
     * 一旦重置链路任一环没落到该标记上（实测发生过），标记就会永久存活，任务被整天拦死且不再有任何告警。
     */
    private final Map<String, String> stallSince = new HashMap<>();

    // 注：原「爱心鸡结号(S2) 按轮幂等」的 competitionDonatedRound 字段，
    // 已随上游 d73879ca（捐蛋改为每日一次）删除 —— 该逻辑改为当日标记
    // （公益 farm::donation / S2 antFarm::competitionDonatedToday），见 AntFarm。
    /**
     * 当日整型标记：tag -> 累计值（如金豆夺宝芝麻粒换豆当日已兑换金豆数）。
     * <p>随 status.json 的每日重置自动清空（updateDay -> unload -> new Status()）。
     */
    private final Map<String, Integer> intFlagLogList = new HashMap<>();
    
    // 保存时间
    private Long saveTime = 0L;

    /** P0 全局风控暂停：所有任务统一的"安全验证截止时间"（毫秒时间戳），过期前整轮静默 */
    private long globalRiskControlUntil = 0L;
    
    /**
     * 绿色经营，收取好友金币已完成用户
     */
    private boolean greenFinancePointFriend = false;
    
    /**
     * 绿色经营，评级领奖已完成用户
     */
    private final Set<Integer> greenFinancePrizesSet = new HashSet<>();

    /**
     * 金豆，已领取奖励的任务ID
     */
    private final Set<String> goldenBeansTaskReceivedSet = new HashSet<>();
    
    public static synchronized Boolean hasFlagToday(String tag) {
        return INSTANCE.flagLogList.contains(tag);
    }
    
    public static synchronized void flagToday(String tag) {
        if (!hasFlagToday(tag)) {
            INSTANCE.flagLogList.add(tag);
            save();
        }
    }
    
    //在写入status中时，重要数据提前记录Uid,一定程度上避免因支付宝账号切换导致标记到下一个账号的少数情况
    public static synchronized void flagToday(String tag, String taskUid) {
        if (!hasFlagToday(tag)) {
            if (taskUid.equals(UserIdMap.getCurrentUid())) {
                INSTANCE.flagLogList.add(tag);
                save();
            }
        }
    }
    
    /**
     * 读取当日整型标记（不存在时返回 0）
     */
    public static synchronized int getIntFlagToday(String tag) {
        Integer value = INSTANCE.intFlagLogList.get(tag);
        return value == null ? 0 : value;
    }
    
    /**
     * 写入当日整型标记（用于累计类额度，如芝麻粒换豆当日已兑换金豆数）
     */
    public static synchronized void setIntFlagToday(String tag, int value) {
        INSTANCE.intFlagLogList.put(tag, value);
        save();
    }
    
    // 清除单个指定Flag
    public static synchronized void clearFlag(String tag) {
        if (INSTANCE.flagLogList.contains(tag)) {
            INSTANCE.flagLogList.remove(tag);
            save(); // 清除后需保存状态，避免下次加载时恢复
        }
    }

    // ===================== P0 全局风控暂停 =====================
    /**
     * 命中风控挑战时调用：把所有任务的"安全验证截止时间"后推 {@link #GLOBAL_RISK_PAUSE_MINUTES} 分钟。
     * <p>一旦置位，{@link #isGlobalRiskControlActive()} 为 true 期间，所有任务在 {@code ModelTask.startTask}
     * / {@code startAllTask} 入口统一跳过，不再空转重打被拦接口、也不加重风控。
     * <p>与现有 per-task 当日熔断标记互补：全局暂停是"短冷却、跨任务"，per-task 标记是"该接口当天不再试"。
     */
    public static synchronized void markGlobalRiskControl() {
        long until = System.currentTimeMillis() + GLOBAL_RISK_PAUSE_MINUTES * 60_000L;
        if (until > INSTANCE.globalRiskControlUntil) {
            INSTANCE.globalRiskControlUntil = until;
            try {
                save();
            } catch (Throwable t) {
                Log.err(TAG, "markGlobalRiskControl save err:", t);
            }
            Log.system(TAG, "全局风控暂停⏸已激活，所有任务暂停约 " + GLOBAL_RISK_PAUSE_MINUTES + " 分钟");
        }
    }

    /** 全局风控暂停是否仍在生效（过期前返回 true） */
    public static synchronized boolean isGlobalRiskControlActive() {
        return System.currentTimeMillis() < INSTANCE.globalRiskControlUntil;
    }

    /** 全局风控暂停剩余毫秒数（已过期返回 0） */
    public static synchronized long getGlobalRiskControlRemainMs() {
        return Math.max(0L, INSTANCE.globalRiskControlUntil - System.currentTimeMillis());
    }

    // ===================== P0 停滞/进度护栏 =====================
    /**
     * 任务本轮执行抛异常：累加连续异常轮次；达到 {@link #STALL_MAX_ERROR_ROUNDS} 则当日暂停该任务。
     * <p>用于兜住"任务线程卡死/每轮重打崩溃"——连续异常不再每轮空耗资源，直到次日重置。
     */
    public static synchronized void noteTaskError(String taskName) {
        if (StringUtil.isEmpty(taskName)) {
            return;
        }
        rollStreakIfNewDay();
        int n = INSTANCE.taskErrorStreak.getOrDefault(taskName, 0) + 1;
        INSTANCE.taskErrorStreak.put(taskName, n);
        if (n >= STALL_MAX_ERROR_ROUNDS) {
            markStalled(taskName, "⚠️", "连续 " + n + " 轮异常");
        }
    }

    /** 任务本轮正常结束（无异常）：清零连续异常计数 */
    public static synchronized void resetTaskError(String taskName) {
        if (!StringUtil.isEmpty(taskName) && INSTANCE.taskErrorStreak.remove(taskName) != null) {
            // 真正清掉了非空值才落盘：保证磁盘不残留陈旧计数，
            // 否则进程重启后 rollStreakIfNewDay() 会因"日期相同"而跳过清零，把旧计数错当今天的。
            save();
        }
    }

    /**
     * 任务本轮执行超时（卡顿）：累加连续超时轮次；达到 {@link #STALL_MAX_SLOW_ROUNDS} 则当日暂停该任务。
     * <p>软超时 {@link #STALL_SOFT_TIMEOUT_MS} 较高（10 分钟），且需连续两次才暂停，避免正常长任务误伤。
     */
    public static synchronized void noteTaskSlow(String taskName) {
        if (StringUtil.isEmpty(taskName)) {
            return;
        }
        rollStreakIfNewDay();
        int n = INSTANCE.taskSlowStreak.getOrDefault(taskName, 0) + 1;
        INSTANCE.taskSlowStreak.put(taskName, n);
        if (n >= STALL_MAX_SLOW_ROUNDS) {
            markStalled(taskName, "⏱️", "连续 " + n + " 轮执行超时");
        }
    }

    /**
     * 若已跨天，把两个 streak 表清零并把归属日期更新为今天。
     * <p>让"连续 N 轮"始终只在同一天内累计 —— 不依赖 {@code updateDay()} 的重置是否命中。
     */
    private static synchronized void rollStreakIfNewDay() {
        String today = TimeUtil.getDateString();
        if (!today.equals(INSTANCE.streakDate)) {
            INSTANCE.taskErrorStreak.clear();
            INSTANCE.taskSlowStreak.clear();
            INSTANCE.streakDate = today;
        }
    }

    /**
     * 打上"当日停滞"标记（带日期持久化）。已存在今天的标记时只补一条可见日志，不重复打标。
     * <p>⚠️ 与历史实现的关键差别：<b>告警不再被"标记已存在"吞掉</b>。
     * 历史实现是 {@code if (!hasFlagToday(flag)) { flagToday(flag); Log.system(...); }}
     * ——标记一旦跨天残留，就永远走不进告警分支，问题彻底静默（实测出现过整天无一条护栏日志）。
     * 现在改成"没标记就打标记+告警；已有今天的标记则只提示一次"，任何一轮都能在日志里看见它被拦。
     */
    private static synchronized void markStalled(String taskName, String icon, String reason) {
        String today = TimeUtil.getDateString();
        String already = INSTANCE.stallSince.get(taskName);
        if (today.equals(already)) {
            // 今天的标记已生效：仍记一条"本轮因停滞护栏跳过"的事实，便于观测持续时长
            Log.system(TAG, "停滞护栏" + icon + "[" + taskName + "]当日标记仍生效（于 " + already + " 触发），本轮跳过");
            return;
        }
        INSTANCE.stallSince.put(taskName, today);
        save();
        Log.system(TAG, "停滞护栏" + icon + "[" + taskName + "]" + reason + "，今日暂停该任务");
    }

    /**
     * 该任务今天是否已被停滞护栏拦下。
     * <p>判据是"标记里的日期 == 今天"，跨天自动失效 —— 不再依赖 {@code flagLogList} 的每日重置是否成功命中。
     */
    public static synchronized boolean isStalledToday(String taskName) {
        if (StringUtil.isEmpty(taskName)) {
            return false;
        }
        String since = INSTANCE.stallSince.get(taskName);
        if (since == null) {
            // 兼容旧数据：历史上只写了 flagLogList，没有日期信息。无法判断是哪天打的，
            // 一律视为"非今天"，避免把不可考证的残留标记继续当作今天的判决。
            return false;
        }
        return since.equals(TimeUtil.getDateString());
    }

    /** 任务本轮未超时：清零连续超时计数 */
    public static synchronized void resetTaskSlow(String taskName) {
        if (!StringUtil.isEmpty(taskName) && INSTANCE.taskSlowStreak.remove(taskName) != null) {
            // 同 resetTaskError：清掉非空值才落盘，避免磁盘残留旧计数被重启读回后误判
            save();
        }
    }
    
    //根据助力场景记录助力次数
    public static synchronized void forestHuntHelpToday(String taskType, int count, String taskUid) {
        if (taskUid.equals(UserIdMap.getCurrentUid())) {
            INSTANCE.forestHuntHelpLogList.put(taskType, count);
            save();
        }
    }
    
    public static synchronized Integer getforestHuntHelpToday(String taskType) {
        Integer count = INSTANCE.forestHuntHelpLogList.get(taskType);
        if (count == null) {
            return 0;
        }
        else {
            return count;
        }
    }
    
    //记录完成任务次数
    public static synchronized void rpcRequestListToday(String taskName, int count) {
        INSTANCE.forestHuntHelpLogList.put(taskName, count);
        save();
    }
    
    public static synchronized Integer getrpcRequestListToday(String taskName) {
        Integer count = INSTANCE.forestHuntHelpLogList.get(taskName);
        if (count == null) {
            return 0;
        }
        else {
            return count;
        }
    }
    
    public static synchronized void wateredFriendToday(String id) {
        Integer count = INSTANCE.wateredFriendLogList.get(id);
        if (count == null) {
            count = 0; // 首次被浇水，次数初始化为0
        }
        INSTANCE.wateredFriendLogList.put(id, count + 1);
        save();
    }
    //Log.forest("统计被水🍯
    //Log.forest("统计浇水🚿
    public static synchronized void getWateredFriendToday() {
        // 1. 基础统计：浇水好友数量（Map的key数量）
        int friendCount = INSTANCE.wateredFriendLogList.size();
        // 2. 统计总浇水量（遍历Map累加所有value）
        int totalWaterAmount = 0;
        
        // 3. 遍历Map的键值对并输出详细信息
        for (Map.Entry<String, Integer> entry : INSTANCE.wateredFriendLogList.entrySet()) {
            String friendId = entry.getKey();       // 好友ID
            Integer waterAmount = entry.getValue(); // 给该好友的浇水量（避免空指针）
            if (waterAmount == null) {
                waterAmount = 0;
            }
            
            // 可选：通过UserIdMap获取好友昵称（如果需要显示名称而非ID）
            String friendName = UserIdMap.getShowName(friendId);
            
            // 输出单条明细（日志/控制台）
            Log.forest("统计被水🍯被["+friendName+"]浇水"+waterAmount+"次");
            
            // 累加总浇水量
            totalWaterAmount += waterAmount;
        }
        
        // 4. 输出汇总统计信息
        Log.forest("统计被水🍯共计被"+friendCount+"个好友浇水"+ totalWaterAmount+"次");
    
        // 5. 从ConfigV2读取wateredFriendList配置值
        Map<String, Integer> configWateredFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null && wateredFriendField.getValue() instanceof Map) {
                    configWateredFriendList = (Map<String, Integer>) wateredFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "获取wateredFriendList配置失败:", e);
        }
        
        // 6. 如果配置存在且不为空，输出预计统计和差别
        if (configWateredFriendList != null && !configWateredFriendList.isEmpty()) {
            // 6.1 输出预计统计信息
            int configFriendCount = 0;
            int configTotalWaterAmount = 0;
            for (Map.Entry<String, Integer> entry : configWateredFriendList.entrySet()) {
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    configFriendCount++;
                    configTotalWaterAmount += configWaterAmount;
                }
            }
            Log.forest("统计被水🍯预计被"+configFriendCount+"个好友浇水"+ configTotalWaterAmount+"次");

            // 6.2 对比配置和实际浇水情况，输出差别
            for (Map.Entry<String, Integer> entry : configWateredFriendList.entrySet()) {
                String friendId = entry.getKey();
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    Integer actualWaterAmount = INSTANCE.wateredFriendLogList.get(friendId);
                    String friendName = UserIdMap.getShowName(friendId);
                    int actual = actualWaterAmount == null ? 0 : actualWaterAmount;
                    int diff = configWaterAmount - actual;
                    if (diff > 0) {
                        Log.forest("统计被水🍯被[" + friendName + "]少浇" + diff + "次");
                    } else if (diff < 0) {
                        Log.forest("统计被水🍯被[" + friendName + "]多浇" + (-diff) + "次");
                    }
                }
            }
            // 6.3 检查不在预计列表中但实际被浇了水的好友
            for (Map.Entry<String, Integer> entry : INSTANCE.wateredFriendLogList.entrySet()) {
                String friendId = entry.getKey();
                if (!configWateredFriendList.containsKey(friendId)) {
                    Integer actualWaterAmount = entry.getValue();
                    if (actualWaterAmount != null && actualWaterAmount > 0) {
                        String friendName = UserIdMap.getShowName(friendId);
                        Log.forest("统计被水🍯被[" + friendName + "]多浇" + actualWaterAmount + "次");
                    }
                }
            }
        }
    }

    public static synchronized void fillWateredFriendList() {
        // 1. 获取当前被浇水的好友数据
        Map<String, Integer> currentData = INSTANCE.wateredFriendLogList;
        if (currentData == null || currentData.isEmpty()) {
            Log.forest("填入被水🍯[当前没有被浇水数据]");
            return;
        }

        // 2. 从ConfigV2读取wateredFriendList配置值
        Map<String, Integer> configWateredFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null && wateredFriendField.getValue() instanceof Map) {
                    configWateredFriendList = (Map<String, Integer>) wateredFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "获取wateredFriendList配置失败:", e);
        }

        // 3. 如果配置为空，创建一个新的LinkedHashMap
        if (configWateredFriendList == null) {
            configWateredFriendList = new LinkedHashMap<>();
        }

        // 4. 将当前被浇水数据填入配置
        int addedCount = 0;
        int updatedCount = 0;
        for (Map.Entry<String, Integer> entry : currentData.entrySet()) {
            String friendId = entry.getKey();
            Integer waterAmount = entry.getValue();
            if (waterAmount != null && waterAmount > 0) {
                if (configWateredFriendList.containsKey(friendId)) {
                    // 更新已有记录
                    configWateredFriendList.put(friendId, waterAmount);
                    updatedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯更新[" + friendName + "]为" + waterAmount + "次");
                } else {
                    // 新增记录
                    configWateredFriendList.put(friendId, waterAmount);
                    addedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯新增[" + friendName + "]" + waterAmount + "次");
                }
            }
        }

        // 5. 清除配置中不在实际被浇水列表里的项
        int removedCount = 0;
        Iterator<Map.Entry<String, Integer>> iterator = configWateredFriendList.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            String friendId = entry.getKey();
            if (!currentData.containsKey(friendId)) {
                    Integer waterAmount = entry.getValue();
                    iterator.remove();
                    removedCount++;
                    String friendName = UserIdMap.getShowName(friendId);
                    Log.forest("填入被水🍯清除[" + friendName + "]" + (waterAmount != null ? waterAmount : 0) + "次");
                }
        }

        // 6. 保存配置到ConfigV2
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> wateredFriendField = antForestFields.get("wateredFriendList");
                if (wateredFriendField != null) {
                    wateredFriendField.setObjectValue(configWateredFriendList);
                    String currentUid = UserIdMap.getCurrentUid();
                    ConfigV2.save(currentUid, true);
                    Log.forest("填入被水🍯完成新增" + addedCount + "个更新" + updatedCount + "个清除" + removedCount + "个");
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "保存wateredFriendList配置失败:", e);
        }
    }
    
    public static synchronized void wateringFriendToday(String id) {
        Integer count = INSTANCE.wateringFriendLogList.get(id);
        if (count == null) {
            count = 0; // 首次被浇水，次数初始化为0
        }
        INSTANCE.wateringFriendLogList.put(id, count + 1);
        // 与 wateredFriendToday 对齐：不落盘的话进程被重启后「今日浇水」统计会丢失
        save();
    }
    
    public static synchronized void getWateringFriendToday() {
        // 1. 基础统计：浇水好友数量（Map的key数量）
        int friendCount = INSTANCE.wateringFriendLogList.size();
        // 2. 统计总浇水量（遍历Map累加所有value）
        int totalWaterAmount = 0;
        
        // 3. 遍历Map的键值对并输出详细信息
        for (Map.Entry<String, Integer> entry : INSTANCE.wateringFriendLogList.entrySet()) {
            String friendId = entry.getKey();       // 好友ID
            Integer waterAmount = entry.getValue(); // 给该好友的浇水量（避免空指针）
            if (waterAmount == null) {
                waterAmount = 0;
            }
            
            // 可选：通过UserIdMap获取好友昵称（如果需要显示名称而非ID）
            String friendName = UserIdMap.getShowName(friendId);
            
            // 输出单条明细（日志/控制台）
            Log.forest("统计浇水🚿给["+friendName+"]浇水"+waterAmount+"次");
            
            // 累加总浇水量
            totalWaterAmount += waterAmount;
        }
        
        // 4. 输出汇总统计信息
        Log.forest("统计浇水🚿共计给"+friendCount+"个好友浇水"+ totalWaterAmount+"次");
        
        // 5. 从ConfigV2读取waterFriendList配置值
        Map<String, Integer> configWaterFriendList = null;
        try {
            ModelFields antForestFields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
            if (antForestFields != null) {
                ModelField<?> waterFriendField = antForestFields.get("waterFriendList");
                if (waterFriendField != null && waterFriendField.getValue() instanceof Map) {
                    configWaterFriendList = (Map<String, Integer>) waterFriendField.getValue();
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "获取waterFriendList配置失败:", e);
        }
        
        // 6. 如果配置存在且不为空，输出预计统计和差别
        if (configWaterFriendList != null && !configWaterFriendList.isEmpty()) {
            // 6.1 输出预计统计信息
            int configFriendCount = 0;
            int configTotalWaterAmount = 0;
            for (Map.Entry<String, Integer> entry : configWaterFriendList.entrySet()) {
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    configFriendCount++;
                    configTotalWaterAmount += configWaterAmount;
                }
            }
            Log.forest("统计浇水🚿预计给"+configFriendCount+"个好友浇水"+ configTotalWaterAmount+"次");

            // 6.2 对比配置和实际浇水情况，输出差别
            for (Map.Entry<String, Integer> entry : configWaterFriendList.entrySet()) {
                String friendId = entry.getKey();
                Integer configWaterAmount = entry.getValue();
                if (configWaterAmount != null && configWaterAmount > 0) {
                    Integer actualWaterAmount = INSTANCE.wateringFriendLogList.get(friendId);
                    String friendName = UserIdMap.getShowName(friendId);
                    int actual = actualWaterAmount == null ? 0 : actualWaterAmount;
                    int diff = configWaterAmount - actual;
                    if (diff != 0) {
                        Log.forest("统计浇水🚿给[" + friendName + "]缺少" + diff + "次");       
                    }
                }
            }
        }
    }
    
    public static synchronized Boolean canWaterFriendToday(String id, int newCount) {
        Integer count = INSTANCE.waterFriendLogList.get(id);
        if (count == null) {
            return true;
        }
        return count < newCount;
    }
    
    public static synchronized int getWaterFriendToday(String id) {
        Integer count = INSTANCE.waterFriendLogList.get(id);
        return count == null ? 0 : count;
    }

    public static synchronized void waterFriendToday(String id, int count, String taskUid) {
        if (taskUid.equals(UserIdMap.getCurrentUid())) {
            // 累加当日已浇次数：覆盖式记账会让「部分失败后再浇满」越过用户配置
            INSTANCE.waterFriendLogList.put(id, getWaterFriendToday(id) + count);
            save();
        }
    }

    /** 显式清零当日浇水次数：累加语义下不能用「加 0」代替重置 */
    public static synchronized void resetWaterFriendToday(String id, String taskUid) {
        if (taskUid.equals(UserIdMap.getCurrentUid())) {
            INSTANCE.waterFriendLogList.put(id, 0);
            save();
        }
    }


    
    public static synchronized int getVitalityExchangeBenefitCountToday(String skuId) {
        Integer exchangedCount = INSTANCE.vitality_ExchangeBenefitLogList.get(skuId);
        if (exchangedCount == null) {
            exchangedCount = 0;
        }
        return exchangedCount;
    }
    
    public static synchronized Boolean canVitalityExchangeBenefitToday(String skuId, int count) {
        return !hasFlagToday("forest::exchangeLimit::" + skuId) && getVitalityExchangeBenefitCountToday(skuId) < count;
    }
    
    public static synchronized void vitalityExchangeBenefitToday(String skuId) {
        int count = getVitalityExchangeBenefitCountToday(skuId) + 1;
        INSTANCE.vitality_ExchangeBenefitLogList.put(skuId, count);
        save();
    }
    
    public static synchronized int getGameCenterBuyMallItemCountToday(String skuId) {
        Integer buyedCount = INSTANCE.gameCenterBuyMallItemList.get(skuId);
        if (buyedCount == null) {
            buyedCount = 0;
        }
        return buyedCount;
    }
    
    public static synchronized Boolean canGameCenterBuyMallItemToday(String skuId, int count) {
        return !hasFlagToday("farm::buyLimit::" + skuId) && getGameCenterBuyMallItemCountToday(skuId) < count;
    }
    
    public static synchronized void gameCenterBuyMallItemToday(String skuId) {
        int count = getGameCenterBuyMallItemCountToday(skuId) + 1;
        INSTANCE.gameCenterBuyMallItemList.put(skuId, count);
        save();
    }
    
    public static synchronized int getExchangeReserveCountToday(int id) {
        Integer count = INSTANCE.exchangeReserveLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static synchronized Boolean canExchangeReserveToday(int id, int count) {
        return getExchangeReserveCountToday(id) < count;
    }
    
    public static synchronized void exchangeReserveToday(int id) {
        int count = getExchangeReserveCountToday(id) + 1;
        INSTANCE.exchangeReserveLogList.put(id, count);
        save();
    }
    
    public static synchronized Boolean canMemberPointExchangeBenefitToday(String benefitId) {
        return !INSTANCE.memberPointExchangeBenefitLogList.contains(benefitId);
    }
    
    public static synchronized void memberPointExchangeBenefitToday(String benefitId) {
        if (canMemberPointExchangeBenefitToday(benefitId)) {
            INSTANCE.memberPointExchangeBenefitLogList.add(benefitId);
            save();
        }
    }
    
    public static synchronized Boolean canAncientTreeToday(String cityCode) {
        return !INSTANCE.ancientTreeCityCodeList.contains(cityCode);
    }
    
    public static synchronized void ancientTreeToday(String cityCode) {
        Status stat = INSTANCE;
        if (!stat.ancientTreeCityCodeList.contains(cityCode)) {
            stat.ancientTreeCityCodeList.add(cityCode);
            save();
        }
    }
    
    private static int getFeedFriendCountToday(String id) {
        Integer count = INSTANCE.feedFriendLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static synchronized Boolean canFeedFriendToday(String id, int countLimit) {
        return !hasFlagToday(FLAG_FEED_FRIEND_ANIMAL_LIMIT) && getFeedFriendCountToday(id) < countLimit;
    }
    
    public static synchronized void feedFriendToday(String id) {
        int count = getFeedFriendCountToday(id) + 1;
        INSTANCE.feedFriendLogList.put(id, count);
        save();
    }
    
    private static int getVisitFriendCountToday(String id) {
        Integer count = INSTANCE.visitFriendLogList.get(id);
        return count == null ? 0 : count;
    }
    
    public static synchronized Boolean canVisitFriendToday(String id, int countLimit) {
        countLimit = Math.max(countLimit, 0);
        countLimit = Math.min(countLimit, 3);
        return !hasFlagToday("farm::visitFriendLimit::" + id) && getVisitFriendCountToday(id) < countLimit;
    }
    
    public static synchronized void visitFriendToday(String id) {
        int count = getVisitFriendCountToday(id) + 1;
        INSTANCE.visitFriendLogList.put(id, count);
        save();
    }
    
    public static synchronized void visitFriendToday(String id, int count) {
        INSTANCE.visitFriendLogList.put(id, count);
        save();
    }
    
    public static synchronized boolean canStallHelpToday(String id) {
        Integer count = INSTANCE.stallHelpedCountLogList.get(id);
        if (count == null) {
            return true;
        }
        return count < 3;
    }
    
    public static synchronized void stallHelpToday(String id, boolean limited) {
        Integer count = INSTANCE.stallHelpedCountLogList.get(id);
        if (count == null) {
            count = 0;
        }
        if (limited) {
            count = 3;
        }
        else {
            count += 1;
        }
        INSTANCE.stallHelpedCountLogList.put(id, count);
        save();
    }
    
    public static synchronized Boolean canUseAccelerateToolToday() {
        return !hasFlagToday("farm::useFarmToolLimit::" + "ACCELERATE" + "TOOL") && INSTANCE.useAccelerateToolCount < 8;
    }
    
    public static synchronized void useAccelerateToolToday() {
        INSTANCE.useAccelerateToolCount += 1;
        save();
    }
    
    public static synchronized Boolean canUseSpecialFoodToday() {
        AntFarm task = ModelTask.getModel(AntFarm.class);
        if (task == null) {
            return false;
        }
        int countLimit = task.getUseSpecialFoodCountLimit().getValue();
        if (countLimit == 0) {
            return true;
        }
        return INSTANCE.useSpecialFoodCount < countLimit;
    }
    
    public static synchronized void useSpecialFoodToday() {
        INSTANCE.useSpecialFoodCount += 1;
        save();
    }
    
    public static synchronized Boolean canOrchardShareP2PToday(String friendUserId) {
        return !hasFlagToday("orchard::shareP2PLimit") && !hasFlagToday("orchard::shareP2PLimit::" + friendUserId) && !INSTANCE.orchardShareP2PLogList.contains(friendUserId);
    }
    
    public static synchronized void orchardShareP2PToday(String friendUserId) {
        if (canOrchardShareP2PToday(friendUserId)) {
            INSTANCE.orchardShareP2PLogList.add(friendUserId);
            save();
        }
    }
    
    public static synchronized Boolean canStallShareP2PToday(String friendUserId) {
        return !hasFlagToday("stall::shareP2PLimit") && !hasFlagToday("stall::shareP2PLimit::" + friendUserId) && !INSTANCE.stallShareP2PLogList.contains(friendUserId);
    }
    
    public static synchronized void stallShareP2PToday(String friendUserId) {
        if (canStallShareP2PToday(friendUserId)) {
            INSTANCE.stallShareP2PLogList.add(friendUserId);
            save();
        }
    }
    
    public static synchronized boolean canDoubleToday() {
        AntForestV2 task = ModelTask.getModel(AntForestV2.class);
        if (task == null) {
            return false;
        }
        // 双击卡使用次数配置已停用（AntForestV2 里对应的 addField 被注释）→ 字段必为 null，
        // 按"当日不能再双击"处理，避免 NPE（与其它双击卡配置的停用语义保持一致）
        if (task.getDoubleCountLimit() == null || task.getDoubleCountLimit().getValue() == null) {
            return false;
        }
        return INSTANCE.doubleTimes < task.getDoubleCountLimit().getValue();
    }
    
    public static synchronized void DoubleToday() {
        INSTANCE.doubleTimes += 1;
        save();
    }
    
    /**
     * 绿色经营-是否可以收好友金币
     *
     * @return true是，false否
     */
    public static synchronized boolean canGreenFinancePointFriend() {
        return !INSTANCE.greenFinancePointFriend;
    }
    
    /**
     * 绿色经营-收好友金币完了
     */
    public static synchronized void greenFinancePointFriend() {
        Status stat = INSTANCE;
        if (!stat.greenFinancePointFriend) {
            stat.greenFinancePointFriend = true;
            save();
        }
    }
    
    /**
     * 绿色经营-是否可以做评级任务
     *
     * @return true是，false否
     */
    public static synchronized boolean canGreenFinancePrizesMap() {
        int week = TimeUtil.getWeekNumber(new Date());
        return !INSTANCE.greenFinancePrizesSet.contains(week);
    }
    
    /**
     * 绿色经营-评级任务完了
     */
    public static synchronized void greenFinancePrizesMap() {
        int week = TimeUtil.getWeekNumber(new Date());
        Status stat = INSTANCE;
        if (!stat.greenFinancePrizesSet.contains(week)) {
            stat.greenFinancePrizesSet.add(week);
            save();
        }
    }

    /**
     * 金豆-是否已领取任务奖励
     */
    public static synchronized boolean canGoldenBeansTaskReceive(String taskId) {
        return !INSTANCE.goldenBeansTaskReceivedSet.contains(taskId);
    }

    /**
     * 金豆-任务奖励已领取
     */
    public static synchronized void goldenBeansTaskReceived(String taskId) {
        Status stat = INSTANCE;
        if (!stat.goldenBeansTaskReceivedSet.contains(taskId)) {
            stat.goldenBeansTaskReceivedSet.add(taskId);
            save();
        }
    }
    
    public static synchronized Status load() {
        String currentUid = UserIdMap.getCurrentUid();
        try {
            if (StringUtil.isEmpty(currentUid)) {
                Log.i(TAG, "用户为空，状态加载失败");
                throw new RuntimeException("用户为空，状态加载失败");
            }
            File statusFile = FileUtil.getStatusFile(currentUid);
            if (statusFile.exists()) {
                String json = FileUtil.readFromFile(statusFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                // 注：旧数据里 flagLogList 的 "stall::xxx"（无日期）不再参与停滞护栏判定（见 isStalledToday），
                // 且 "stall::" 前缀在本项目里是多个功能的公共命名空间（新村也有 stall::shareP2PLimit 等业务标记），
                // 因此这里不做批量清理，交给每日重置自然淘汰，避免误删他人标记。
                String formatted = JsonUtil.toFormatJsonString(INSTANCE);
                if (formatted != null && !formatted.equals(json)) {
                    // 回写只允许改格式、不许改数据
                    if (JsonUtil.isRewriteLossless(json, formatted)) {
                        Log.i(TAG, "重新格式化 status.json");
                        FileUtil.write2File(formatted, FileUtil.getStatusFile(currentUid));
                    } else {
                        Log.i(TAG, "status.json 加载结果与磁盘数据不一致，已跳过重新格式化");
                    }
                }
            }
            else {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                Log.i(TAG, "初始化 status.json");
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "状态文件格式有误，已重置");
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
            }
            catch (JsonMappingException e) {
                Log.printStackTrace(TAG, e);
            }
        }
        if (INSTANCE.saveTime == 0) {
            INSTANCE.saveTime = System.currentTimeMillis();
        }
        // 加载后自检：streak 若不属于今天，一律清空。
        // 兜住历史遗留数据（旧版 resetTask* 不落盘，磁盘上可能留着昨天的计数），
        // 也等于把 rollStreakIfNewDay() 的跨天判据提前到加载时刻执行。
        try {
            if (!TimeUtil.getDateString().equals(INSTANCE.streakDate)
                    && (!INSTANCE.taskErrorStreak.isEmpty() || !INSTANCE.taskSlowStreak.isEmpty())) {
                Log.i(TAG, "清理非当日的停滞计数 " + INSTANCE.taskErrorStreak + " / " + INSTANCE.taskSlowStreak);
                INSTANCE.taskErrorStreak.clear();
                INSTANCE.taskSlowStreak.clear();
            }
            INSTANCE.streakDate = TimeUtil.getDateString();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return INSTANCE;
    }
    
    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new Status());
        }
        catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static synchronized void save() {
        save(Calendar.getInstance());
    }
    
    public static synchronized void save(Calendar nowCalendar) {
        String currentUid = UserIdMap.getCurrentUid();
        if (StringUtil.isEmpty(currentUid)) {
            Log.record("用户为空，状态保存失败");
            throw new RuntimeException("用户为空，状态保存失败");
        }
        if (updateDay(nowCalendar)) {
            Log.system(TAG, "重置 status.json");
        }
        else {
            // 每次落盘都记一行会淹没有效日志（实测约 68 行/天），降为由「抓包记录」开关控制的调试日志
            Log.debug(TAG + ", 保存 status.json");
        }
        long lastSaveTime = INSTANCE.saveTime;
        try {
            INSTANCE.saveTime = System.currentTimeMillis();
            FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatusFile(currentUid));
        }
        catch (Exception e) {
            INSTANCE.saveTime = lastSaveTime;
            throw e;
        }
    }
    
    public static synchronized Boolean updateDay(Calendar nowCalendar) {
        if (TimeUtil.isLessThanSecondOfDays(INSTANCE.saveTime, nowCalendar.getTimeInMillis())) {
            Status.unload();
            // 跨天：解禁超期的"自动拉黑"任务，给它们一次重试机会
            MessageUtil.sweepExpiredBlackList();
            // 跨天：释放原先预置拉黑的技术性不可自动化项，交给自动拉黑机制判定
            MessageUtil.sweepReleasedDefaults();
            // 跨天：清理已废弃列表的历史残留（旧版本写过、现列表已不存在的数据）
            MessageUtil.sweepObsoleteBlackLists();
            return true;
        }
        else {
            return false;
        }
    }
    
    @Data
    private static class WaterFriendLog {
        String userId;
        int waterCount = 0;
        
        public WaterFriendLog() {
        }
        
        public WaterFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class ReserveLog {
        String projectId;
        int applyCount = 0;
        
        public ReserveLog() {
        }
        
        public ReserveLog(String id) {
            projectId = id;
        }
    }
    
    @Data
    private static class BeachLog {
        String cultivationCode;
        int applyCount = 0;
        
        public BeachLog() {
        }
        
        public BeachLog(String id) {
            cultivationCode = id;
        }
    }
    
    @Data
    private static class FeedFriendLog {
        String userId;
        int feedCount = 0;
        
        public FeedFriendLog() {
        }
        
        public FeedFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class VisitFriendLog {
        String userId;
        int visitCount = 0;
        
        public VisitFriendLog() {
        }
        
        public VisitFriendLog(String id) {
            userId = id;
        }
    }
    
    @Data
    private static class StallShareIdLog {
        String userId;
        String shareId;
        
        public StallShareIdLog() {
        }
        
        public StallShareIdLog(String uid, String sid) {
            userId = uid;
            shareId = sid;
        }
    }
    
    @Data
    private static class StallHelpedCountLog {
        String userId;
        int helpedCount = 0;
        int beHelpedCount = 0;
        
        public StallHelpedCountLog() {
        }
        
        public StallHelpedCountLog(String id) {
            userId = id;
        }
    }
    
}