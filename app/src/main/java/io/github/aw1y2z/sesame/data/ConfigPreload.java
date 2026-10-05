package io.github.aw1y2z.sesame.data;

import io.github.aw1y2z.sesame.util.FileUtil;
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
import io.github.aw1y2z.sesame.util.idMap.MarathonIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.NewAncientTreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.PathThemeMapListMap;
import io.github.aw1y2z.sesame.util.idMap.PlantSceneIdMap;
import io.github.aw1y2z.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.aw1y2z.sesame.util.idMap.ReserveIdMap;
import io.github.aw1y2z.sesame.util.idMap.TreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.VitalityBenefitIdMap;
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
}
