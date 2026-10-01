package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 金豆夺宝专场（antorchard 抽奖活动）黑名单候选列表。
 * <p>与「农场肥料任务」({@link AlipayAntOrchardTaskList}) 不同，专场的任务以稳定的 {@code taskType}
 * 标识（如 {@code CHOUCHOULE_GAME_ZJD1500}），标题会变但 taskType 不变，故黑名单按 taskType 匹配。
 * <p>这里只放「已知」的 taskType 作为 UI 可选种子；游戏类（如生存33天电台招募）服务端拒收 RPC、
 * 默认已在黑名单字段初始值里；运行过程中被自动拉黑的任务会写入同一字段，无需在此穷举。
 */
public class AlipayAntOrchardDrawTaskList extends IdAndName {
    private static List<AlipayAntOrchardDrawTaskList> list;

    public AlipayAntOrchardDrawTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayAntOrchardDrawTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (String taskType : new String[]{
                    "ORCHARD_DRAW_DAILY_SIGN",
                    "DRAW_GOLDENBEAN_liulan",
                    "CHOUCHOULE_GAME_ZJD1500",
                    "CHOUCHOULE_GAME_ZJD3000",
                    "CHOUCHOULE_GAME_ZJD5000",
                    "CHOUCHOULE_GAME_SC33T_ZH1"
            }) {
                list.add(new AlipayAntOrchardDrawTaskList(taskType, taskType));
            }
        }
        return list;
    }

    public static void remove(String id) {
        getList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
    }

}
