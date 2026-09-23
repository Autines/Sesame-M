package io.github.aw1y2z.sesame.data.task;

import android.os.Build;
import android.os.Handler;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ThreadUtil;

import java.util.Map;
import java.util.concurrent.*;

public class SystemChildTaskExecutor implements ChildTaskExecutor {

    private final Handler handler;

    private final Map<String, ThreadPoolExecutor> groupChildTaskExecutorMap = new ConcurrentHashMap<>();

    /** 每组允许同时运行的最大子任务线程数（原先上限是 Integer.MAX_VALUE，等于无限起线程） */
    private static final int MAX_CHILD_TASK_THREADS = 16;

    public SystemChildTaskExecutor() {
        handler = ApplicationHook.getMainHandler();
    }

    @Override
    public Boolean addChildTask(ModelTask.ChildModelTask childTask) {
        ThreadPoolExecutor threadPoolExecutor = getChildGroupHandler(childTask.getGroup());
        long execTime = childTask.getExecTime();
        if (execTime > 0) {
            Runnable runnable = () -> {
                if (childTask.getIsCancel()) {
                    return;
                }
                //String modelTaskId = getName();
                //Log.i("任务模块:" + modelTaskId + " 添加子任务:" + id);
                Future<?> future = threadPoolExecutor.submit(() -> {
                    try {
                        long delay = childTask.getExecTime() - System.currentTimeMillis();
                        if (delay > 0) {
                            try {
                                Thread.sleep(delay);
                            } catch (Exception e) {
                                //Log.record("任务模块:" + modelTaskId + " 中断子任务:" + id);
                                return;
                            }
                        }
                        runWithModule(childTask);
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                        //Log.record("任务模块:" + modelTaskId + " 异常子任务:" + id);
                    } finally {
                        childTask.getModelTask().removeChildTask(childTask.getId());
                        //Log.i("任务模块:" + modelTaskId + " 移除子任务:" + id);
                    }
                });
                childTask.setCancelTask(() -> future.cancel(true));
            };
            long delayMillis = execTime - System.currentTimeMillis();
            if (delayMillis > 3000) {
                handler.postDelayed(runnable, delayMillis - 2500);
                childTask.setCancelTask(() -> handler.removeCallbacks(runnable));
            } else {
                childTask.setCancelTask(() -> handler.removeCallbacks(runnable));
                handler.post(runnable);
            }
        } else {
            Future<?> future = threadPoolExecutor.submit(() -> {
                //Log.i("任务模块:" + modelTaskId + " 添加子任务:" + id);
                try {
                    runWithModule(childTask);
                } catch (Exception e) {
                    Log.printStackTrace(e);
                    //Log.record("任务模块:" + getName() + " 异常子任务:" + childTask.getId());
                } finally {
                    childTask.getModelTask().removeChildTask(childTask.getId());
                    //Log.i("任务模块:" + modelTaskId + " 移除子任务:" + id);
                }
            });
            childTask.setCancelTask(() -> future.cancel(true));
        }
        return true;
    }

    @Override
    public Boolean removeChildTask(ModelTask.ChildModelTask childTask) {
        childTask.cancel();
        return true;
    }

    @Override
    public Boolean clearGroupChildTask(String group) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            groupChildTaskExecutorMap.compute(group, (keyInner, valueInner) -> {
                if (valueInner != null) {
                    ThreadUtil.shutdownAndAwaitTermination(valueInner, 3, TimeUnit.SECONDS);
                }
                return null;
            });
        } else {
            synchronized (groupChildTaskExecutorMap) {
                ThreadPoolExecutor threadPoolExecutor = groupChildTaskExecutorMap.get(group);
                if (threadPoolExecutor != null) {
                    ThreadUtil.shutdownAndAwaitTermination(threadPoolExecutor, 3, TimeUnit.SECONDS);
                    groupChildTaskExecutorMap.remove(group);
                }
            }
        }
        return true;
    }

    @Override
    public Boolean clearAllChildTask() {
        for (ThreadPoolExecutor threadPoolExecutor : groupChildTaskExecutorMap.values()) {
            ThreadUtil.shutdownNow(threadPoolExecutor);
        }
        groupChildTaskExecutorMap.clear();
        return true;
    }

    /**
     * 在子任务线程上执行，并临时接管「当前模块」上下文。
     * <p>
     * 子任务跑在独立的线程池线程上，而 {@link Log#CURRENT_MODULE} 是 ThreadLocal，
     * 因此子线程里读不到主任务设置的模块名，日志就会丢掉 [模块名] 前缀
     * （典型如「收取能量🪂」这类蹲点收取子任务）。
     * 这里统一在子任务执行期间补上上下文，跑完立刻清除，避免线程被复用后串味。
     */
    private static void runWithModule(ModelTask.ChildModelTask childTask) {
        ModelTask modelTask = childTask.getModelTask();
        String moduleName = modelTask == null ? null : modelTask.getName();
        String previous = Log.getCurrentModule();
        Log.setCurrentModule(moduleName);
        try {
            childTask.run();
        } finally {
            // 恢复原值而不是直接清空：线程池线程可能被复用，也可能正处于嵌套任务中
            Log.setCurrentModule(previous);
        }
    }

    private ThreadPoolExecutor getChildGroupHandler(String group) {
        ThreadPoolExecutor threadPoolExecutor = groupChildTaskExecutorMap.get(group);
        if (threadPoolExecutor != null) {
            return threadPoolExecutor;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            threadPoolExecutor = groupChildTaskExecutorMap.compute(group, (keyInner, valueInner) -> {
                if (valueInner == null) {
                    // 子任务有时效性，必须立即开始，所以保留 SynchronousQueue（不排队）；
                    // 但上限不能是 Integer.MAX_VALUE——那等于"每个子任务新建一条线程"。
                    // 改为有限上限后，超出部分由 CallerRunsPolicy 在提交线程内执行（背压，仍然立即执行不排队）。
                    valueInner = new ThreadPoolExecutor(1, MAX_CHILD_TASK_THREADS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
                }
                return valueInner;
            });
        } else {
            synchronized (groupChildTaskExecutorMap) {
                threadPoolExecutor = groupChildTaskExecutorMap.get(group);
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = new ThreadPoolExecutor(1, MAX_CHILD_TASK_THREADS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
                    groupChildTaskExecutorMap.put(group, threadPoolExecutor);
                }
            }
        }
        return threadPoolExecutor;
    }

}
