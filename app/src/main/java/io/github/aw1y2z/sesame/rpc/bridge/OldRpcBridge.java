package io.github.aw1y2z.sesame.rpc.bridge;

import org.json.JSONException;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.aw1y2z.sesame.util.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class OldRpcBridge implements RpcBridge {

    private static final String TAG = OldRpcBridge.class.getSimpleName();

    private ClassLoader loader;

    private Class<?> h5PageClazz;

    private Method rpcCallMethod;

    private Method getResponseMethod;

    private Object curH5PageImpl;

    public void load() throws Exception {
        loader = ApplicationHook.getClassLoader();
        try {
            h5PageClazz = loader.loadClass(ClassUtil.H5PAGE_NAME);
            Log.i(TAG, "rpc loadClass successfully");
        } catch (ClassNotFoundException t) {
            Log.err(TAG, "rpc loadClass err:", t);
            throw new RuntimeException(t);
        } catch (Throwable t) {
            Log.err(TAG, "rpc loadClass err:", t);
            throw t;
        }
        if (rpcCallMethod == null) {
            try {
                rpcCallMethod = loader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil").getMethod(
                        "rpcCall", String.class, String.class, String.class,
                        boolean.class, loader.loadClass(ClassUtil.JSON_OBJECT_NAME), String.class, boolean.class, h5PageClazz,
                        int.class, String.class, boolean.class, int.class, String.class);
                getResponseMethod = loader.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5Response").getMethod("getResponse");
                Log.i(TAG, "get oldRpcCallMethod successfully");
            } catch (Exception e) {
                Log.i(TAG, "get oldRpcCallMethod err:");
                throw e;
            }
        }
    }

    @Override
    public void unload() {
        getResponseMethod = null;
        rpcCallMethod = null;
        h5PageClazz = null;
        loader = null;
    }

    public String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        RpcEntity resRpcEntity = requestObject(rpcEntity, tryCount, retryInterval);
        if (resRpcEntity != null) {
            String response = resRpcEntity.getResponseString();
            if (response != null) {
                return response;
            }
        }
        // P0 止血：风控/异常/离线时 requestObject 返回 null，下游直接 new JSONObject(null)
        // 会对 null 调 String.length() 崩溃并打印 15 行堆栈。统一返回合法空 JSON，
        // 下游 opt* 取默认值走失败分支，不改变任务成败判定。
        Log.record("RPC响应为空，已用空JSON兜底避免解析异常");
        return "{}";
    }

    @Override
    public RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        // 本代已作废就不再发请求，避免旧代继续消耗资产
        if (RunGeneration.isStale()) {
            throw new TaskCancelledException();
        }
        if (ApplicationHook.isOffline()) {
            return null;
        }
        int id = rpcEntity.hashCode();
        String method = rpcEntity.getRequestMethod();
        String args = rpcEntity.getRequestData();
        try {
            int count = 0;
            do {
                count++;
                Object resp;
                try {
                    RpcIntervalLimit.enterIntervalLimit(method);
                    if (rpcCallMethod.getParameterTypes().length == 12) {
                        resp = rpcCallMethod.invoke(
                                null, method, args, "", true, null, null, false, curH5PageImpl, 0, "", false, -1);
                    } else {
                        resp = rpcCallMethod.invoke(
                                null, method, args, "", true, null, null, false, curH5PageImpl, 0, "", false, -1, "");
                    }
                } catch (Throwable t) {
                    rpcEntity.setError();
                    // P0 降噪：1009 风控在 RPC 层直接抛异常，先判定。命中时只记一行摘要，
                    // 不再打 Log.error + 完整堆栈（每次约13行），避免风控期 error.log 被刷爆。
                    // 判定必须仍在 printStackTrace 之前，否则堆栈已经落盘。
                    if (MessageUtil.isRiskControl(t)) {
                        Status.markGlobalRiskControl();
                        Log.record("风控🚫[" + method + "]RPC异常命中1009风控，已触发全局暂停，不再重试");
                        return null;
                    }
                    Log.error("old rpc request | id: " + id + " | method: " + method + " err:");
                    Log.printStackTrace(t);
                    // P0 智能重试：风控类响应不再重试（其他异常按原策略返回）
                    if (rpcEntity.getResponseString() != null && MessageUtil.isRiskControl(rpcEntity.getResponseString())) {
                        Status.markGlobalRiskControl();
                        Log.record("风控🚫[" + method + "]请求异常但响应为风控，已触发全局暂停");
                        return rpcEntity;
                    }
                    if (t instanceof InvocationTargetException) {
                        Throwable cause = t.getCause();
                        if (cause != null) {
                            String msg = cause.getMessage();
                            if (!StringUtil.isEmpty(msg)) {
                                if (msg.contains("登录超时")) {
                                    if (!ApplicationHook.isOffline()) {
                                        ApplicationHook.setOffline(true);
                                        NotificationUtil.updateStatusText("登录超时");
                                        if (BaseModel.getTimeoutRestart().getValue()) {
                                            Log.record("尝试重新登录");
                                            ApplicationHook.reLogin();
                                        }
                                    }
                                } else if (msg.contains("[1004]") && "alipay.antmember.forest.h5.collectEnergy".equals(method)) {
                                    if (BaseModel.getWaitWhenException().getValue() > 0) {
                                        long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                                        RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                                        NotificationUtil.updateStatusText("异常");
                                        Log.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                                    }
                                    if (retryInterval < 0) {
                                        try {
                                            Thread.sleep(600 + RandomUtil.delay());
                                        } catch (InterruptedException e) {
                                            Log.printStackTrace(e);
                                            Thread.currentThread().interrupt();
                                        }
                                    } else if (retryInterval > 0) {
                                        try {
                                            Thread.sleep(retryInterval);
                                        } catch (InterruptedException e) {
                                            Log.printStackTrace(e);
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                } else if (msg.contains("MMTPException")) {
                                    try {
                                        String jsonString = "{\"resultCode\":\"FAIL\",\"memo\":\"MMTPException\",\"resultDesc\":\"MMTPException\"}";
                                        rpcEntity.setResponseObject(new JSONObject(jsonString), jsonString);
                                        return rpcEntity;
                                    } catch (JSONException e) {
                                        Log.printStackTrace(e);
                                    }
                                    if (retryInterval < 0) {
                                        try {
                                            Thread.sleep(600 + RandomUtil.delay());
                                        } catch (InterruptedException e) {
                                            Log.printStackTrace(e);
                                            Thread.currentThread().interrupt();
                                        }
                                    } else if (retryInterval > 0) {
                                        try {
                                            Thread.sleep(retryInterval);
                                        } catch (InterruptedException e) {
                                            Log.printStackTrace(e);
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    continue;
                                }
                            }
                        }
                    }
                    return null;
                }
                try {
                    String resultStr = (String) getResponseMethod.invoke(resp);
                    JSONObject resultObject = new JSONObject(resultStr);
                    rpcEntity.setResponseObject(resultObject, resultStr);
                    if (resultObject.optString("memo", "").contains("系统繁忙")) {
                        ApplicationHook.setOffline(true);
                        NotificationUtil.updateStatusText("系统繁忙，可能需要滑动验证");
                        Log.record("系统繁忙，可能需要滑动验证");
                        return null;
                    }
                    if (!resultObject.optBoolean("success")
                            && !resultObject.optBoolean("isSuccess")) {
                        rpcEntity.setError();
                        Log.error("old rpc response | id: " + id + " | method: " + method + " args: " + args + " | data:" + rpcEntity.getResponseString());
                        // P0 全局风控暂停：命中风控挑战即触发全局静默（自动过滑块不可用，重试只会加重风控）
                        if (MessageUtil.isRiskControl(resultObject)) {
                            Status.markGlobalRiskControl();
                            Log.record("风控🚫[" + method + "]接口被拦截，已触发全局暂停");
                        }
                    }
                    return rpcEntity;
                } catch (Throwable t) {
                    Log.error("old rpc response | id: " + id + " | method: " + method + " get err:");
                }
                return null;
            } while (count < tryCount);
            return null;
        } finally {
            Log.debug("Old RPC\n方法: " + method + "\n参数: " + args + "\n数据: " + rpcEntity.getResponseString() + "\n");
        }
    }

}
