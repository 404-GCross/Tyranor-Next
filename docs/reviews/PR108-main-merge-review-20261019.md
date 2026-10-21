# PR #108：同步 main 1.61 的合并复核

日期：2026-10-19（Asia/Singapore）。提交者要求处理 PR 合并冲突。
合并前 head `069ca4784bf2ab78dc9d0959b82609b4003128c6`；普通合并上游 main `bd8a94b00f41cbd1978e525bbb574a2593630a39`，合并提交 `87d1d6bb886d7f1b10dc3710fdeaff34565c593f`。
此前候选保存在本地 `backup/pr108-before-main-20261019`；没有 force-push。

## 解决方式

唯一文本冲突在 SettingsScreen.kt：上游从 MiuixScaffold 的 innerPadding 改为
MiuixTopBarBlurScaffold 的 topInset，本 PR 在同一调用位置增加 graphicsContent。
保留上游新 topInset 参数和完整 graphicsContent lambda，滤镜状态及保存回调不丢失。
更新红点使用 offset、NoticeDotRed、CircleShape；删除冲突区没有实际引用的旧导入。

其余上游改动正常合入，包括 1.61 版本、默认风格顶栏模糊、自动导航样式、更新红点、
Artemis Ogg 修复和 Ethornell 库/来源证明更新。根 README、AGENTS、应用版本与全部预编译库
相对新 main 无差异；采用的是上游内容，没有为本 PR 另改规范、版本或重编引擎库。

## 独立增量复核

主 Agent 与两路独立 Agent 分别检查 UI/core 和后端自动合并的交互，未发现合并新增的
可确认 P0–P3。本轮检查的是上游增量与滤镜接入的交互，不冒称重新全审完整 PR。

- 全局图形设置的 remember(kind)、EngineSettingsStore.setGraphicsSettings 与单游戏
  graphicsOverride / setGraphicsOverride / PerGameGraphicsSettings 保留，跟随/覆盖状态不变。
- 两页仍在 MiuixSettingsTheme 内，由新顶栏宿主提供 topInset；默认 Android12+ 列表使用
  contentPadding，玻璃及旧系统由宿主 Column 回退且 topInset=0，没有重复顶部间距。
- AppSettingsStore 的导航样式/更新状态与图形配置分属原有存储；上游自动解析和更新红点保留。
  顶栏公共组件、AppSettingsStore 与新 main 一致。
- 官方 Artemis 的派生 Vorbis 修复库与图形安装/加载使用同一路径；图形门禁核验 ELF Build ID
  与 basename，上游定点音频修复不改这两项。音频修复、图形 hook、引擎启动的顺序保留。
- Siglus 全部源码、桥接及登记相对合并前 head 不变，Android16 显示解耦、关闭滤镜原版路线保留。
- jniLibs 与 Ethornell provenance 完全沿用新 main；CI 保留来源、依赖闭包和实际 APK 检查，
  没有新增引擎重编步骤。

## 验证边界和保留事项

只运行合并候选 Android CI；精确 head、运行链接及结果发布在 PR 评论/正文与本地索引。
没有本地构建、实机、故障注入、heap/GC、额外 Release/R8 或完整 Lint。此前候选的 CI 和
设备验收不冒充新合并候选的验证；指定 Android CLI 技能本机仍不可用，未声称执行。

[导航图标 P3 四张裁剪图及豁免申请](https://github.com/Weiss-UltimateSavior/Tyranor-Next/pull/108#issuecomment-6266687290)
已发布，当前无左图标布局保持，等待维护者裁决；API26/27 输入模板例外同样未擅自关闭。
截图及裁剪产物不入 Git，正文原有 11 张截图保留。
