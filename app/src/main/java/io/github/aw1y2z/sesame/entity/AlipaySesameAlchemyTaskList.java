package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.idMap.SesameAlchemyTaskListMap;

public class AlipaySesameAlchemyTaskList extends IdAndName {
    private static List<AlipaySesameAlchemyTaskList> list;

    public AlipaySesameAlchemyTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipaySesameAlchemyTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : SesameAlchemyTaskListMap.getMap().entrySet()) {
                list.add(new AlipaySesameAlchemyTaskList(entry.getKey(), entry.getValue()));
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

    /**
     * 丢弃静态缓存，让下一次 {@link #getList()} 按 idMap 重建。
     * <p>⚠️ 静态缓存是"永久"的：App（界面）进程里若在任何 load 之前调用过 {@link #getList()}，
     * 当时读到的空 idMap 会建成空列表**并被缓存住**，之后再 load 也不会刷新
     * ⇒ 黑名单界面永远空白。故 {@code ConfigPreload} 在加载完 idMap 后必须清一次
     * （与 {@code AlipayGoldenBeansMallItem} / {@code AlipayWelfareFundTaskList} 同一处理）。
     */
    public static void clear() {
        list = null;
    }

}
