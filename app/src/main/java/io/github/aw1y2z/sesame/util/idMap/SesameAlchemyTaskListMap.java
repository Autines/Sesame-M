package io.github.aw1y2z.sesame.util.idMap;

import java.util.Map;

import io.github.aw1y2z.sesame.util.FileUtil;

/**
 * 芝麻炼金「任务标题 → 显示名」映射（同时充当该列表的黑名单键集合）。
 * <p>与 {@link MemberCreditSesameTaskListMap} 分开存放：炼金任务（"玩30秒XX"）与
 * 芝麻粒任务（"逛一逛XX"）形态虽同源，但任务名不重叠，混在一起会让用户在
 * 黑名单界面里分不清哪个是哪个。
 */
public class SesameAlchemyTaskListMap {

    private static final StringMapStore STORE = new StringMapStore(ignoredUserId -> FileUtil.getSesameAlchemyTaskListMapFile());

    public static Map<String, String> getMap() {
        return STORE.getMap();
    }

    public static String get(String key) {
        return STORE.get(key);
    }

    public static void add(String key, String value) {
        STORE.add(key, value);
    }

    public static void remove(String key) {
        STORE.remove(key);
    }

    public static void load() {
        STORE.load(null);
    }

    public static boolean save() {
        return STORE.save(null);
    }

    public static void clear() {
        STORE.clear();
    }

}
