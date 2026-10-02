package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.idMap.GoldenBeansTaskListMap;

public class AlipayGoldenBeansTaskList extends IdAndName {

    public AlipayGoldenBeansTaskList(String i, String n) {
        id = i;
        name = n;
    }

    /**
     * 候选列表**每次现算**，不做静态缓存。
     * <p>内容来自运行期学到的 {@link GoldenBeansTaskListMap}。一旦缓存住，只要首次构建发生在
     * 映射载入之前（例如从主界面渲染设置项时），之后无论怎么刷新都只会剩下英文稳定键 ——
     * 黑名单页里就会整片英文。
     */
    public static List<AlipayGoldenBeansTaskList> getList() {
        List<AlipayGoldenBeansTaskList> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : GoldenBeansTaskListMap.getMap().entrySet()) {
            result.add(new AlipayGoldenBeansTaskList(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** 从候选里剔除一项（键是服务端 taskId）；需调用方自行 save 才落盘 */
    public static void remove(String id) {
        GoldenBeansTaskListMap.remove(id);
    }

}
