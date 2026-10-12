# 项目自定义的 ProGuard/R8 规则。

# --- 关键修复：保护 Kotlin 标准库不被 R8 混淆 ---
# 背景：Kotlin 2.2 的 kotlin-stdlib 不再通过 consumer rules 自动携带 keep 规则，
# 导致 R8 把 kotlin.collections / kotlin.* 等标准库类重命名（如 AbstractList -> y），
# 与 androidx.emoji2 / androidx.startup 初始化时通过反射/继承访问标准库类冲突，
# 抛 IllegalAccessError: Class kotlin.collections.b extended by class me is inaccessible，
# release 版启动即闪退。显式 keep 住整个 kotlin 标准库即可。
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }
-dontwarn kotlin.**

# 若只想要体积与安全兼顾，也可以用官方推荐的更细粒度规则（kotlinx 官方维护）：
# 但上面「整包 keep」对个人练手项目最省心、零风险，体积代价可接受。
