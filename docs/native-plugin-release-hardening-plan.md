# Native Plugin Release Hardening Plan

## Goal

把 native 引擎库从 `engine/src/main/jniLibs` 迁移到 `app/src/main/nativeplugins` 后，确保 Debug/Release 都能稳定启动 KRKR、ONS、Artemis，并避免 R8 混淆破坏 JNI/反射入口。

## Checklist

- [x] 启动游戏前同步确保对应 native 插件已安装且完整。
- [x] 插件安装逻辑不只依赖 SharedPreferences 标记，必须校验 `current/arm64-v8a` 下的必备 `.so` 与 manifest。
- [x] `KR2Activity` 的默认 native 加载路径改为插件加载兜底，避免未来误用时加载已删除的 `jniLibs`。
- [x] 补齐 release R8/ProGuard keep 规则，覆盖 JNI、SDL、KRKR、ONS、Artemis、IJK、hook stub 相关包。
- [x] 运行 `assembleDebug` 和 `assembleRelease` 验证。
