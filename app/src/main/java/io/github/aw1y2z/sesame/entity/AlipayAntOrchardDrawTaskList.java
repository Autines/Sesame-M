package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.idMap.AntOrchardDrawTaskListMap;

/**
 * 金豆夺宝专场（antorchard 抽奖活动）黑名单候选列表。
 * <p>与「农场肥料任务」({@link AlipayAntOrchardTaskList}) 不同，专场的任务标识是稳定的
 * {@code taskType}（如 {@code CHOUCHOULE_GAME_ZJD1500}）——**标题会变、taskType 不变**，
 * 所以黑名单的**存储值必须是 taskType**，不能存标题。
 * <p>但界面和日志要给人看中文，因此这里用 id→name 的方式单独提供"显示名"：
 * name 取自 {@link AntOrchardDrawTaskListMap}（运行时用服务端返回的真实标题持续刷新），
 * 下面的 {@link #BUILT_IN} 只是内置兜底，保证刚装好、还没跑过任务时列表里不会显示成英文。
 * <p>游戏类（如生存33天电台招募）服务端拒收 RPC，默认已在黑名单字段初始值里；
 * 运行过程中被自动拉黑的任务同样会写进该字段，无需在此穷举。
 */
public class AlipayAntOrchardDrawTaskList extends IdAndName {

    /**
     * 内置兜底显示名：taskType → 中文标题。
     * <p>取自 2026-10-01 K40 抓包 {@code listTaskantorchard} 的真实应答（不是猜的）。
     * 运行时会用服务端最新标题覆盖，所以哪天标题变了也只是"这里显示旧名"，不影响功能。
     */
    private static final String[][] BUILT_IN = {
            {"ORCHARD_DRAW_DAILY_SIGN", "每日签到"},
            {"DRAW_GOLDENBEAN_liulan", "去逛金豆夺宝得机会"},
            {"CHOUCHOULE_GAME_ZJD1500", "【限时】攒金豆得2次机会"},
            {"CHOUCHOULE_GAME_ZJD3000", "继续攒豆得2次机会"},
            {"CHOUCHOULE_GAME_ZJD5000", "继续攒豆得3次机会"},
            {"CHOUCHOULE_GAME_SC33T_ZH1", "生存33天电台招募10次"},
    };

    public AlipayAntOrchardDrawTaskList(String i, String n) {
        id = i;
        name = n;
    }

    /**
     * 把内置兜底显示名补进映射（已有真实标题的不覆盖，服务端标题优先）。**只改内存不落盘**：
     * 落盘必须等 {@link AntOrchardDrawTaskListMap#load()} 之后，否则会把已学到的标题覆盖掉。
     *
     * @return 是否有新增（true 表示调用方可以在 load() 之后落盘）
     */
    public static boolean seedDefaults() {
        boolean changed = false;
        for (String[] kv : BUILT_IN) {
            if (AntOrchardDrawTaskListMap.get(kv[0]) == null) {
                AntOrchardDrawTaskListMap.add(kv[0], kv[1]);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 每次现算、不缓存：运行期会不断学到新 taskType 的真实标题，
     * 缓存住会导致设置页一直显示英文（或新任务根本不出现）。
     */
    public static List<AlipayAntOrchardDrawTaskList> getList() {
        seedDefaults();
        Map<String, String> map = AntOrchardDrawTaskListMap.getMap();
        List<AlipayAntOrchardDrawTaskList> list = new ArrayList<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            list.add(new AlipayAntOrchardDrawTaskList(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public static void remove(String id) {
        AntOrchardDrawTaskListMap.remove(id);
    }

}
