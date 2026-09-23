package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.Data;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

import java.io.File;

@Data
public class AppConfig {

    private static final String TAG = AppConfig.class.getSimpleName();

    // 存到与注入进程（支付宝模块）共享的 sesame 目录，确保日志开关在模块进程同样生效
    private static final File APP_CONFIG_DIRECTORY_FILE = FileUtil.MAIN_DIRECTORY_FILE;

    public static final AppConfig INSTANCE = new AppConfig();

    /** 界面风格：HyperOS/Miuix 观感（历史默认，保持既有样式不变） */
    public static final String UI_STYLE_MIUIX = "miuix";

    /** 界面风格：Material Design 3（Material You，支持动态取色） */
    public static final String UI_STYLE_MATERIAL3 = "material3";

    @JsonIgnore
    private boolean init;

    private Boolean newUI = true;

    /** 界面风格，取值见 UI_STYLE_* 常量；缺省为 miuix 以保持历史观感 */
    private String uiStyle = UI_STYLE_MIUIX;
    private Boolean languageSimplifiedChinese = true;

    private Boolean darkMode = false;
    private Boolean followSystem = true;

    private Boolean enableForestLog = true;
    private Boolean enableGoldenBeansLog = true;
    private Boolean enableFarmLog = true;
    private Boolean enableOtherLog = true;
    private Boolean enableDebugLog = false;
    private Boolean enableViewErrorLog = true;
    private Boolean enableViewRuntimeLog = true;
    private Boolean batteryPerm = true;

    // 模块级开关：原先是按账号存在 BaseModel 里，现改为全局（注入进程与模块 App 共用同一份）
    private Boolean newRpc = true;
    private Boolean showToast = true;
    private Integer toastOffsetY = 0;
    private Boolean enableOnGoing = false;
    private Boolean closeCaptchaDialog = true;

    public Boolean getNewRpc() {
        return newRpc;
    }

    public void setNewRpc(Boolean value) {
        newRpc = value;
    }

    public Boolean getShowToast() {
        return showToast;
    }

    public void setShowToast(Boolean value) {
        showToast = value;
    }

    public Integer getToastOffsetY() {
        return toastOffsetY;
    }

    public void setToastOffsetY(Integer value) {
        toastOffsetY = value;
    }

    public Boolean getEnableOnGoing() {
        return enableOnGoing;
    }

    public void setEnableOnGoing(Boolean value) {
        enableOnGoing = value;
    }

    public Boolean getCloseCaptchaDialog() {
        return closeCaptchaDialog;
    }

    public void setCloseCaptchaDialog(Boolean value) {
        closeCaptchaDialog = value;
    }

    public Boolean getLanguageSimplifiedChinese() {
        return languageSimplifiedChinese;
    }

    public void setLanguageSimplifiedChinese(Boolean value) {
        languageSimplifiedChinese = value;
    }

    /**
     * 界面风格。必须显式声明：Lombok 的 @Data 只在 javac 阶段织入访问器，
     * Kotlin 编译器看不到生成的方法，因此 UI 层（Kotlin）读取的字段都要有显式 getter。
     * 取值非法或缺失时回落 HyperOS 风格，保证旧配置升级后观感不变。
     */
    public String getUiStyle() {
        return uiStyle == null ? UI_STYLE_MIUIX : uiStyle;
    }

    public void setUiStyle(String value) {
        uiStyle = value;
    }

    public Boolean getDarkMode() {
        return darkMode;
    }

    public void setDarkMode(Boolean value) {
        darkMode = value;
    }

    public Boolean getFollowSystem() {
        return followSystem;
    }

    public void setFollowSystem(Boolean value) {
        followSystem = value;
    }

    public Boolean getEnableForestLog() { return enableForestLog; }
    public void setEnableForestLog(Boolean value) { enableForestLog = value; }

    public Boolean getEnableGoldenBeansLog() { return enableGoldenBeansLog; }
    public void setEnableGoldenBeansLog(Boolean value) { enableGoldenBeansLog = value; }

    public Boolean getEnableFarmLog() { return enableFarmLog; }
    public void setEnableFarmLog(Boolean value) { enableFarmLog = value; }

    public Boolean getEnableOtherLog() { return enableOtherLog; }
    public void setEnableOtherLog(Boolean value) { enableOtherLog = value; }

    public Boolean getEnableDebugLog() { return enableDebugLog; }
    public void setEnableDebugLog(Boolean value) { enableDebugLog = value; }

    public Boolean getEnableViewErrorLog() { return enableViewErrorLog; }
    public void setEnableViewErrorLog(Boolean value) { enableViewErrorLog = value; }

    public Boolean getEnableViewRuntimeLog() { return enableViewRuntimeLog; }
    public void setEnableViewRuntimeLog(Boolean value) { enableViewRuntimeLog = value; }

    public Boolean getBatteryPerm() { return batteryPerm; }
    public void setBatteryPerm(Boolean value) { batteryPerm = value; }

    public static Boolean save() {
        return FileUtil.write2File(toSaveStr(), new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json"));
    }

    public static synchronized AppConfig load() {
        File appConfigFile = new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json");
        try {
            if (appConfigFile.exists()) {
                String json = FileUtil.readFromFile(appConfigFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                // 注意：必须先加载文件再打印日志，否则开关判断仍取默认值 true，
                // 导致「运行日志已关闭」时启动进程仍写入加载日志
                Log.i("加载APP配置");
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化APP配置");
                    FileUtil.write2File(formatted, appConfigFile);
                }
            } else {
                unload();
                Log.i(TAG, "初始APP配置");
                FileUtil.write2File(toSaveStr(), appConfigFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "重置APP配置");
            try {
                unload();
                FileUtil.write2File(toSaveStr(), appConfigFile);
            } catch (Exception e) {
                Log.printStackTrace(TAG, t);
            }
        }
        INSTANCE.setInit(true);
        return INSTANCE;
    }

    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new AppConfig());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }

}