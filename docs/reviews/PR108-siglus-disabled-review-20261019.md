# PR #108：关闭 Siglus 滤镜时恢复上游原版路径

## 原因与范围

日期：2026-10-19（Asia/Singapore）。本轮在已发布 `e86e5788` 上普通追加，代码修复 `c1ad58e1cdddbb7030182c0452dac79327cb2908`。
提交者要求设置关闭滤镜时使用原版处理逻辑。此前为统一窗口所有权，将所有 Siglus 启动都
接到私有 producer 和宿主 GLES，即使关闭滤镜也增加显示拷贝与 GLES3/OES 依赖，范围过大。
本轮恢复关闭路径；开启有效滤镜的窗口租约、独立控制槽、producer 交接和 BOM 修复保留。

## 实现与上游行为对照

启动时只选一次模式，游戏运行期间不更换 owner 或迁移句柄线程：

| 条件 | 显示与执行 |
| --- | --- |
| 生效开关关闭 | SurfaceView + 主线程同步 JNI，链不进入新增处理管线 |
| 开关开启，但链为空、无效或无有效节点 | 同上游直出路径 |
| 开关开启且有有效节点 | TextureView + 私有 producer + 独立 engine/GLES owner |

关闭路径的 `surfaceCreated` 同步 create 或 setSurface，`surfaceChanged` 同步 resize；
`surfaceDestroyed` 只标记不可呈现、停帧并保留 VM，Activity 销毁才同步 destroy。
渲染、窗口、输入和 IME 的 live-handle JNI 全在主线程，SurfaceHolder 销毁回调不会越过
另一个工作线程上的绘制调用；不把框架借用的 Surface 交给异步 worker 或私自 release。
首个正常 step 隐藏加载层，不等待滤镜 GLES 首帧。同步调用可能阻塞 UI 的上游特征保持。

关闭路径不创建 TextureView、GLES proxy、私有画布/producer 或滤镜线程，不启动新增舞台
配置读取任务，也不新增显示拷贝。上游原生引擎创建中的资源/密钥处理、原有标题回写仍保留。
输入、IME、加载界面、标题回写与消息框在单个 Activity 复用，通过小型宿主接口连接两种 owner，
没有复制整份 Activity 或引入第二个启动页面。新增四项生产启动策略和三项进程准入模型用例，交由 CI 执行。

## 交互与生命周期边界

- Direct create/connect 的 JNI 和确认均在 SurfaceHolder 回调内同步完成。该分支按窗口世代
  判定结果，不依赖尚未赋值的异步 request ID；首次 surfaceChanged 不被异步 create ACK 漏掉。
- step/IME 同步回调可能退出或停止游戏，返回后仅在仍可步进时排下一帧；滤镜 owner 仍保持
  至多一个待执行/执行中的 step。
- 消息框始终先投递主线程 Handler，再核验当前 owner 和句柄，避免 UI 同步 native 回调中
  重入 Rust；销毁或旧对话框的迟到回应不能操作已经释放的句柄。
- 两种模式共用进程级准入 permit。旧异步 owner 仍在创建、关闭或永久阻塞时，新 direct
  owner 也不能启动第二个实例。迟到旧 permit 释放不能解除后续 owner 的占用。
- 已开启滤镜但配置不可用时保留私有启动拷贝，不在运行中切回 direct 或迁移线程。
  只有实际关闭/空链启动不增加宿主 GLES3/OES 依赖；滤镜模式仍有原先的兼容性与故障保留边界。

## 审查与规范

作者、独立 Agent 与主 Agent 对照上游 main `740d7c13` 的 SurfaceView 宿主，分别复核
启动路由、SurfaceHolder 回调、同步 ACK、步进、消息框、输入/IME、跨模式准入及资源释放。
当前作者、独立 Agent 和主 Agent 的静态复核未发现新的可确认 P1/P2/P3；独立意见与本轮 CI 结果在 PR 进度评论中记录；模型回归只证明生产路由/准入规则，
不代替实际 Handler、JNI、EGL、SurfaceView 或设备后台恢复测试。

修改限于 engine 宿主与小型纯 Java 状态模型；没有 app UI/Compose 反向依赖、设置布局或
主题变化。manifest 仅修正宿主双路径注释。上游根 README.md、AGENTS.md、应用版本、
全部预编译引擎库和 JNI ABI 均保持不变；没有重编引擎或增加来源未知代码。
仍不包含 CuNNy2、用户导入或自研 Artemis 画质接口。

遵循 CONTRIBUTING 的功能边界，关闭路径修复与文档独立追加，不 force-push。
AGENTS 要求的多 Agent 独立复核已执行；指定 Android CLI 审查技能此前在本机未找到，
未冒称执行。无图标导航和 API 26/27 输入模板例外继续等待维护者裁决。

## 验证与未完成事项

本轮仅通过候选 Android CI 运行资源/文案、JVM 回归、原生门禁、Debug APK 编译与产物检查。
精确最终 head 与 CI 链接记录在 PR 本轮评论和本地交付索引；没有执行本地项目测试/构建或实机测试，
没有另跑完整 Lint、Release/R8，也不把历史验收推广成这次双路径宿主的设备结果。

此前三项窗口/队列 P2 与 BOM P3 修复保留在开启滤镜的路径；关闭路径不会重新引入
工作线程持有 SurfaceHolder、输入配额挤掉窗口控制或跨世代异步直出恢复。
分析器后台位置偏移、特定 KR 原版偏色、文本排除、用户导入等暂缓项不在本轮修复范围。
