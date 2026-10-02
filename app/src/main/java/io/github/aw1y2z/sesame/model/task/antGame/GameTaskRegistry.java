package io.github.aw1y2z.sesame.model.task.antGame;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.aw1y2z.sesame.util.Log;

/**
 * 游戏任务适配器的注册表：按注册顺序找第一个能处理该任务的适配器。
 *
 * <p>新增一个游戏只需：① 写一个 {@link IGameTask} 实现；② 在下面的静态块里注册一行。
 * 业务模块（农场/森林/庄园/金豆夺宝专场……）只调 {@link #match}，不必知道具体有哪些游戏。
 */
public class GameTaskRegistry {

    private static final String TAG = GameTaskRegistry.class.getSimpleName();

    private static final List<IGameTask> ADAPTERS = new ArrayList<>();

    static {
        register(MiniProgramGameTask.INSTANCE);
    }

    private GameTaskRegistry() {
    }

    public static void register(IGameTask adapter) {
        if (adapter != null && !ADAPTERS.contains(adapter)) {
            ADAPTERS.add(adapter);
        }
    }

    /**
     * 找到能处理该任务的适配器；没有则返回 {@code null}（调用方按"不是游戏任务"处理）。
     * <p>单个适配器抛异常不影响其它适配器匹配。
     */
    public static IGameTask match(JSONObject task, String taskType, String title, String desc) {
        if (task == null) {
            return null;
        }
        for (IGameTask adapter : ADAPTERS) {
            try {
                if (adapter.canHandle(task, taskType, title, desc)) {
                    return adapter;
                }
            } catch (Throwable t) {
                Log.err(TAG, "canHandle err(" + adapter.getGameName() + "):", t);
            }
        }
        return null;
    }
}
