# Sesame-GR ProGuard Rules
# 用于启用 R8 时保留必要的类和方法

# ============================================================
# 1. Xposed 框架入口类
# ============================================================
-keep class io.github.libxposed.** { *; }
-keep class io.github.aw1y2z.sesame.hook.ApplicationHook { *; }
-keepclassmembers class io.github.aw1y2z.sesame.hook.ApplicationHook {
    public <init>(...);
}

# ============================================================
# 2. Model 系统：所有 Model 子类通过反射实例化，必须保留
# ============================================================
# 保留 Model 基类及关键方法
-keep class io.github.aw1y2z.sesame.data.Model { *; }
-keep class io.github.aw1y2z.sesame.data.ModelType { *; }
-keep class io.github.aw1y2z.sesame.data.ModelGroup { *; }
-keep class io.github.aw1y2z.sesame.data.ModelFields { *; }
-keep class io.github.aw1y2z.sesame.data.ModelConfig { *; }
-keepclassmembers class io.github.aw1y2z.sesame.data.Model {
    public <init>(...);
}

# 保留所有 ModelTask 子类（通过反射实例化）
-keep class io.github.aw1y2z.sesame.data.task.ModelTask { *; }
-keep class io.github.aw1y2z.sesame.data.task.BaseTask { *; }

# ============================================================
# 3. ModelField 系统：用于 Jackson 序列化/反序列化
#    注意：所有 ModelField 子类及内部类必须完整保留
# ============================================================
-keep class io.github.aw1y2z.sesame.data.ModelField { *; }
-keep class io.github.aw1y2z.sesame.data.modelFieldExt.** { *; }
-keepclassmembers class io.github.aw1y2z.sesame.data.ModelField {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.modelFieldExt.** {
    public <init>(...);
}
# 保留所有 ModelField 子类的内部类（如 GoldenBeansTaskList 等匿名内部类）
-keep class io.github.aw1y2z.sesame.data.**$* { *; }

# 3. 警告忽略（Jackson 依赖的 java.beans 类在 Android SDK 中不完整）
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# ============================================================
# 4. 配置类：Jackson 序列化/反序列化
# ============================================================
-keep class io.github.aw1y2z.sesame.data.ConfigV2 { *; }
-keep class io.github.aw1y2z.sesame.data.ConfigPreload { *; }
-keep class io.github.aw1y2z.sesame.data.AppConfig { *; }
-keep class io.github.aw1y2z.sesame.data.TokenConfig { *; }
-keepclassmembers class io.github.aw1y2z.sesame.data.ConfigV2 {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.AppConfig {
    public <init>(...);
}
-keepclassmembers class io.github.aw1y2z.sesame.data.TokenConfig {
    public <init>(...);
}

# ============================================================
# 5. 状态与统计类：Jackson 序列化
# ============================================================
-keep class io.github.aw1y2z.sesame.util.Status { *; }
-keep class io.github.aw1y2z.sesame.util.Statistics { *; }
-keepclassmembers class io.github.aw1y2z.sesame.util.Status {
    public static ** INSTANCE;
}
-keepclassmembers class io.github.aw1y2z.sesame.util.Statistics {
    public static ** INSTANCE;
}

# ============================================================
# 6. RPC 请求/响应相关
# ============================================================
-keep class io.github.aw1y2z.sesame.hook.RpcRequest { *; }
-keep class io.github.aw1y2z.sesame.hook.ServerCommon { *; }
-keep class io.github.aw1y2z.sesame.hook.BaseHandler { *; }
-keep class io.github.aw1y2z.sesame.rpc.bridge.* { *; }
-keepclassmembers class io.github.aw1y2z.sesame.hook.RpcRequest {
    public <init>(...);
}

# ============================================================
# 7. IdMap 类：通过 ConfigPreload.prepare() 调用，保留类本身
# ============================================================
-keep class io.github.aw1y2z.sesame.util.idMap.** { *; }

# ============================================================
# 8. Entity 类
# ============================================================
-keep class io.github.aw1y2z.sesame.entity.** { *; }

# ============================================================
# 9. 扩展模块（ExtensionsHandleAlpha 通过 Class.forName 加载）
# ============================================================
-keep class io.github.aw1y2z.sesame.model.extensions.** { *; }
-keepclassmembers class io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle {
    public static java.lang.Object handleAlphaRequest(java.lang.String, java.lang.String, java.lang.Object);
}
# ============================================================
# 10. Hook 工具类
# ============================================================
# 保留整个 hook 包（ApplicationHook 被 PermissionUtil 引用，R8 可能误删）
-keep class io.github.aw1y2z.sesame.hook.** { *; }
-keep class io.github.aw1y2z.sesame.util.XHelpers { *; }
-keep class io.github.aw1y2z.sesame.util.compat.** { *; }
-keep class io.github.aw1y2z.sesame.util.ClassUtil { *; }
-keep class io.github.aw1y2z.sesame.util.FileUtil { *; }
-keep class io.github.aw1y2z.sesame.util.Log { *; }
-keep class io.github.aw1y2z.sesame.util.JsonUtil { *; }
-keep class io.github.aw1y2z.sesame.util.TimeUtil { *; }
-keep class io.github.aw1y2z.sesame.util.NotificationUtil { *; }
-keep class io.github.aw1y2z.sesame.util.PermissionUtil { *; }
-keep class io.github.aw1y2z.sesame.util.StringUtil { *; }
-keep class io.github.aw1y2z.sesame.util.ThreadUtil { *; }
-keep class io.github.aw1y2z.sesame.util.ToastUtil { *; }
-keep class io.github.aw1y2z.sesame.util.TypeUtil { *; }

# ============================================================
# 11. Model 实现类（任务模块）
# ============================================================
-keep class io.github.aw1y2z.sesame.model.** { *; }

# ============================================================
# 12. UI 类（Activity/Fragment 由系统加载）
# ============================================================
-keep class io.github.aw1y2z.sesame.ui.** { *; }
-keep class io.github.aw1y2z.sesame.SesameApplication { *; }

# ============================================================
# 13. Xposed 模块元数据（AndroidManifest 中声明的 Activity）
# ============================================================
-keep class * implements io.github.libxposed.api.XposedModule { *; }

# ============================================================
# 14. 通用：保留反射用到的字段和方法
# ============================================================
# 保留 Lombok 生成的 getter/setter（R8 可能误删）
-keepclassmembers class ** {
    public * get*();
    public void set*(...);
}

# ============================================================
# 15. 第三方库排除（不必要的类保持原样）
# ============================================================
-keep class com.fasterxml.jackson.** { *; }
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# 16. MiUIX / AppCompat Tab 组件：防止 R8 移除内部类构造函数
#    TabAdapter 是 ScrollingTabContainerView 的内部类，
#    R8 可能移除其合成构造函数导致 Jackson/反射失败
# ============================================================
-keep class androidx.appcompat.widget.ScrollingTabContainerView { *; }
-keep class androidx.appcompat.widget.ScrollingTabContainerView$* { *; }
-keep class androidx.appcompat.widget.AbsActionBarView { *; }
-keep class androidx.appcompat.widget.AbsActionBarView$* { *; }
