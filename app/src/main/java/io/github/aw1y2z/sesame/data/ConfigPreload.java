package io.github.aw1y2z.sesame.data;

import io.github.aw1y2z.sesame.entity.AlipayGoldenBeansMallItem;
import io.github.aw1y2z.sesame.entity.AlipaySesameAlchemyTaskList;
import io.github.aw1y2z.sesame.entity.AlipayGoldTicketTaskList;
import io.github.aw1y2z.sesame.entity.AlipayWelfareFundTaskList;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.idMap.AnimalIdMap;
import io.github.aw1y2z.sesame.util.idMap.AntDodoTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.aw1y2z.sesame.entity.AlipayAntOrchardDrawTaskList;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardDrawTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntSportsTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntStallTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.BeachIdMap;
import io.github.aw1y2z.sesame.util.idMap.CooperationIdMap;
import io.github.aw1y2z.sesame.util.idMap.FarmOrnamentsIdMap;
import io.github.aw1y2z.sesame.util.idMap.GameCenterMallItemMap;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansMallItemMap;
import io.github.aw1y2z.sesame.util.idMap.MarathonIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.SesameAlchemyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.NewAncientTreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.PathThemeMapListMap;
import io.github.aw1y2z.sesame.util.idMap.PlantSceneIdMap;
import io.github.aw1y2z.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.aw1y2z.sesame.util.idMap.ReserveIdMap;
import io.github.aw1y2z.sesame.util.idMap.TreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.VitalityBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.GoldTicketTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.WelfareFundTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.ForestHuntIdMap;
import io.github.aw1y2z.sesame.util.idMap.rpcRequestMap;

/**
 * 配置相关的预加载逻辑（原 SettingsActivity / NewSettingsActivity 中的初始化）。
 * miuix Compose 界面复用同一套数据，必须在这里完成 IdMap 加载与 ConfigV2.load，
 * 否则 SELECT_ONE / SELECT 等字段的选项列表为空。
 */
public final class ConfigPreload {

    private ConfigPreload() {
    }

    public static void prepare(String userId) {
        UserIdMap.setCurrentUserId(userId);
        UserIdMap.load(userId);
        CooperationIdMap.load(userId);
        VitalityBenefitIdMap.load(userId);
        GameCenterMallItemMap.load(userId);
        FarmOrnamentsIdMap.load(userId);
        MemberBenefitIdMap.load(userId);
        PromiseSimpleTemplateIdMap.load(userId);
        TreeIdMap.load();
        ReserveIdMap.load();
        AnimalIdMap.load();
        MarathonIdMap.load();
        NewAncientTreeIdMap.load();
        BeachIdMap.load();
        PlantSceneIdMap.load();
        rpcRequestMap.load();
        ForestHuntIdMap.load();
        MemberCreditSesameTaskListMap.load();
        // 芝麻炼金 | 黑名单列表：此前漏登记，界面进程从不 load 该 idMap → 候选列表恒为空
        SesameAlchemyTaskListMap.load();
        // 候选实体的静态缓存是注入进程写的，App 进程必须清一次才会重建（同 AlipayGoldenBeansMallItem）
        AlipaySesameAlchemyTaskList.clear();
        AntForestVitalityTaskListMap.load();
        AntForestHuntTaskListMap.load();
        AntFarmDoFarmTaskListMap.load();
        AntFarmDrawMachineTaskListMap.load();
        AntDodoTaskListMap.load();
        AntOceanAntiepTaskListMap.load();
        AntOceanFishBlackListMap.load();
        AntOrchardTaskListMap.load();
        AntOrchardDrawTaskListMap.load();
        // 补上专场任务的内置显示名并落盘（必须在 load() 之后：否则会把已学到的真实标题覆盖掉）
        if (AlipayAntOrchardDrawTaskList.seedDefaults()) {
            AntOrchardDrawTaskListMap.save();
        }
        AntStallTaskListMap.load();
        AntSportsTaskListMap.load();
        PathThemeMapListMap.load();
        AntMemberTaskListMap.load();
        GoldenBeansTaskListMap.load();
        GoldenBeansMallItemMap.load();
        // 候选实体的静态缓存是注入进程写的，App 进程必须清一次才会重建
        AlipayGoldenBeansMallItem.clear();
        WelfareFundTaskListMap.load();
        AlipayWelfareFundTaskList.clear();
        GoldTicketTaskListMap.load();
        AlipayGoldTicketTaskList.clear();
        MonopolyTaskListMap.load();
        ConfigV2.load(userId);
    }

    public static boolean isEmpty(String userId) {
        return StringUtil.isEmpty(userId);
    }

    /**
     * 用「当前账号」执行 {@link #prepare(String)}（找不到账号时什么都不做）。
     *
     * <p>给 Kotlin 页面用：{@code UserIdMap.currentUid} 的 getter 是 Lombok 生成的
     * （{@code @Getter} 在 javac 阶段注入），**Kotlin 编译期读不到它**，只能在 Java 这侧转一手。
     *
     * <p>账号用 {@link UserIdMap#resolveCurrentUid()} 解析（拿不到时回退到配置目录里唯一的账号）：
     * 界面进程刚启动时 {@code currentUid} 是空的，直接用 {@code getCurrentUid()} 会**整个 bail 掉**
     * —— 字段表不加载、任务名回落成英文键、解禁也改不动配置，全是同一个源头。
     *
     * <p>真拿不到账号时直接返回，绝不能往下走：{@code prepare(null)} 会按"默认配置"加载，
     * 把整表字段值灌成默认值，看起来就像用户的配置被清空了。
     */
    public static void prepareCurrentUser() {
        String userId = UserIdMap.resolveCurrentUid();
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        prepare(userId);
    }

    public static java.io.File getConfigFile(String userId) {
        if (StringUtil.isEmpty(userId)) {
            return FileUtil.getDefaultConfigV2File();
        }
        return FileUtil.getConfigV2File(userId);
    }

    /** 供 Kotlin 层取「当前登录账号」：拿不到时返回空串（Kotlin 侧好判空，别返回 null） */
    public static String currentUserId() {
        String uid = UserIdMap.resolveCurrentUid();
        return uid == null ? "" : uid;
    }

    /**
     * 彻底删除某账号的配置：**账号目录 + 该账号的滚动备份 + 账号序号登记**。
     *
     * <p>为什么要一次清三处：旧实现只删 {@code config/<uid>/config_v2.json} 一个文件，
     * 于是①目录下 friend/status/self 等文件残留、②accountIndex 里仍登记着该账号、
     * ③{@link ConfigV2#load} 发现文件不存在会**立刻从默认账号复制一份重建** ——
     * 三者叠加，用户看到的就是「删了又回来」。
     *
     * <p>🔴 两条硬保护（确保绝不误删别的账号）：
     * <ol>
     *   <li>{@code userId} 为空直接返回 —— 空值代表「默认账号」，其配置是**根目录**下的
     *       {@code config_v2.json}，根本不存在"账号目录"，绝不能按目录删；</li>
     *   <li>删除路径**全部由 {@code userId} 拼出**（{@code config/<uid>}、
     *       {@code bak/config_v2_<uid>_*}），不会命中其它账号的文件。</li>
     * </ol>
     *
     * <p>⚠️ 若该账号**仍是当前登录账号**，模块下次加载时会重新为它建一份默认配置
     * （{@code ConfigV2.load} 的 else 分支），要真正删干净需先切换到其它账号。
     *
     * @return 是否删掉了配置目录（目录本就不存在时返回 false）
     */
    public static boolean purgeAccount(String userId) {
        if (StringUtil.isEmpty(userId)) {
            Log.record("彻底删除账号：userId 为空（默认账号），已忽略");
            return false;
        }
        // 先取名再删 —— 删完配置后就取不到显示名了
        String label = UserIdMap.getShowName(userId);
        java.io.File dir = FileUtil.getAccountConfigDir(userId);
        boolean removedConfig = dir != null && dir.exists() && FileUtil.deleteFile(dir);
        int backups = FileUtil.deleteAccountBackups(userId);
        UserIdMap.removeAccount(userId);
        Log.record("彻底删除账号🈳" + label
                + "#配置目录=" + (removedConfig ? "已删" : "本就不存在")
                + "#滚动备份=" + backups + "个#序号登记=已清");
        return removedConfig;
    }
}
