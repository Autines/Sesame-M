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

}
