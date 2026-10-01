package io.github.aw1y2z.sesame.util.idMap;

import java.util.Map;

import io.github.aw1y2z.sesame.util.FileUtil;

/**
 * 金豆夺宝专场：{@code taskType → 中文标题} 的展示映射。
 * <p>专场的黑名单**存储值必须是 taskType**（标题会变、taskType 不变，匹配靠它），
 * 但设置页与日志要给人看中文，所以单独维护一份 id→name 映射（沿用 idMap 系列既有套路）。
 * <p>内容由 {@code AntOrchard.orchardDrawTask()} 每次拉取任务列表时用服务端真实标题刷新，
 * 所以标题变了会自动跟上，不需要人工维护。
 */
public class AntOrchardDrawTaskListMap {

    private static final StringMapStore STORE = new StringMapStore(ignoredUserId -> FileUtil.getAntOrchardDrawTaskListMapFile());

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
