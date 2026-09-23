package io.github.aw1y2z.sesame.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.model.base.ModelOrder;
import io.github.aw1y2z.sesame.util.Log;
import lombok.Getter;

public abstract class Model {

    private static final Map<ModelGroup, Map<String, ModelConfig>> groupModelConfigMap = new LinkedHashMap<>();

    private static final Map<Class<? extends Model>, Model> modelMap = new ConcurrentHashMap<>();

    private static final List<Class<? extends Model>> modelClazzList = ModelOrder.getClazzList();

    @Getter
    private static final Model[] modelArray = new Model[modelClazzList.size()];

    private final BooleanModelField enableField;

    public final BooleanModelField getEnableField() {
        return enableField;
    }

    public Model() {
        this.enableField = new BooleanModelField("enable", getEnableFieldName(), false);
    }

    public String getEnableFieldName() {
        return "开启" + getName();
    }

    public final Boolean isEnable() {
        return enableField.getValue();
    }

    public ModelType getType() {
        return ModelType.NORMAL;
    }

    public abstract String getName();

    public abstract ModelGroup getGroup();

    public abstract ModelFields getFields();

    public void prepare() {}

    public void boot(ClassLoader classLoader) {}

    public void destroy() {}

    public static Map<String, ModelConfig> getGroupModelConfig(ModelGroup modelGroup) {
        Map<String, ModelConfig> map = groupModelConfigMap.get(modelGroup);
        if (map == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 把所有分组里的 ModelConfig 摊平成一张表（modelCode -> ModelConfig）。
     * 供只需「拿到全部已注册模型」的场景使用，例如 ConfigV2 重建配置字段表。
     */
    public static Map<String, ModelConfig> getAllModelConfig() {
        Map<String, ModelConfig> all = new LinkedHashMap<>();
        for (Map<String, ModelConfig> groupMap : groupModelConfigMap.values()) {
            all.putAll(groupMap);
        }
        return Collections.unmodifiableMap(all);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> T getModel(Class<T> modelClazz) {
        return (T) modelMap.get(modelClazz);
    }

    public static synchronized void initAllModel() {
        destroyAllModel();
        for (int i = 0, len = modelClazzList.size(); i < len; i++) {
            Class<? extends Model> modelClazz = modelClazzList.get(i);
            try {
                Model model = modelClazz.getDeclaredConstructor().newInstance();
                ModelConfig modelConfig = new ModelConfig(model);
                modelArray[i] = model;
                modelMap.put(modelClazz, model);
                String modelCode = modelConfig.getCode();
                ModelGroup group = modelConfig.getGroup();
                Map<String, ModelConfig> groupMap = groupModelConfigMap.get(group);
                if (groupMap == null) {
                    groupMap = new LinkedHashMap<>();
                    groupModelConfigMap.put(group, groupMap);
                }
                groupMap.put(modelCode, modelConfig);
            } catch (ReflectiveOperationException e) {
                Log.printStackTrace(e);
            }
        }
    }

    /**
     * 注册表是否已经建好（可读 Model / ModelConfig）。
     */
    public static synchronized boolean isInitialized() {
        return !groupModelConfigMap.isEmpty();
    }

    /**
     * 非破坏性初始化：仅在注册表为空时才构建，已有注册表一律原样保留。
     * <p>
     * 与 {@link #initAllModel()} 的区别是**绝不重建**。重建会 new 出全新的 Model 实例，
     * 字段值随之回到默认值，若该处没有紧接着重新加载配置，用户已保存的配置就会在保存时被默认值覆盖。
     * 因此只有「只读注册表、不参与配置编辑」的页面（如日志页要按分组列出入口）才可以使用本方法；
     * 配置编辑链路请继续使用 {@link #initAllModel()} 并紧跟 ConfigPreload.prepare()。
     */
    public static synchronized void initAllModelIfNeeded() {
        if (!groupModelConfigMap.isEmpty()) {
            return;
        }
        initAllModel();
    }

    public static synchronized void bootAllModel(ClassLoader classLoader) {
        for (Model model : modelArray) {
            try {
                model.prepare();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            try {
                if (model.getEnableField().getValue()) {
                    model.boot(classLoader);
                }
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
    }

    public static synchronized void destroyAllModel() {
        for (int i = 0, len = modelArray.length; i < len; i++) {
            Model model = modelArray[i];
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                    model.destroy();
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
                modelArray[i] = null;
            }
        }
        // 清空放在循环外：它们描述的是「整张注册表」而非单个 model，放在循环里每轮重清既无意义也易误读。
        // groupModelConfigMap 原先漏清 —— initAllModel() 会复用上一轮的 group -> map，
        // 若某个 model 新一轮实例化失败（下方 catch），它的旧 ModelConfig 会残留在分组表里，
        // 表现为配置页出现「幽灵分组条目」。
        modelMap.clear();
        groupModelConfigMap.clear();
    }

}
