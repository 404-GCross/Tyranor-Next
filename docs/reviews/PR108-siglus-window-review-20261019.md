# PR #108：Siglus 窗口生命周期及配置探测复审

> **版本范围：** 本报告固定对应 `e86e5788` 窗口修复候选。随后按提交者要求，关闭/空链路径已恢复上游 SurfaceView 同步逻辑，不再额外依赖宿主 GLES 或显示拷贝；当前行为以 [关闭路径复核](PR108-siglus-disabled-review-20261019.md) 为准。

## 审核对象与结论

日期：2026-10-19（Asia/Singapore）。基线 `740d7c130fce019350e135066478de61fa9b439e`。
对应人工审查：[评论 6250398212](https://github.com/Weiss-UltimateSavior/Tyranor-Next/pull/108#issuecomment-6250398212)，
其固定对象为旧 head `ed4dad4ef0d41d856ed2a00dff287617de2d3f43`。
本轮普通追加：BOM 修复 `551c985`，宿主生命周期修复 `e9fa64566fd1900bd958e568aaea80027e547bbb`。
旧 [初次原库报告](PR108-keytoml-review-20261018.md) 仅作历史记录，其无 P1/P2 判断已被人工复审推翻。

四项反馈均接受并修复。作者、独立 Agent 与主 Agent 分别复读实现、调用点及模型用例，
当前静态复核未发现新的可确认功能缺陷；这不等于真实驱动与设备生命周期已验收。
本轮只通过候选分支 CI 执行测试和编译；精确最终 head、任务结果及链接更新在 PR 评论，
未执行本地项目测试、构建或实机测试，不将旧候选验收推广到本轮实现。

## 逐项修复

### P2：SurfaceHolder 销毁后原生线程仍可能使用窗口

原因：Java Surface 快照与 ANativeWindow 引用无法延长 SurfaceHolder 回调的使用许可；
队列检查也无法排除正在进行的 create/set_surface/step。

修复：原生引擎从创建开始仅绑定宿主私有 producer，永不接收 Android 显示窗口。
输出改由独立 GLES owner 连接 TextureView。对已经接管的 SurfaceTexture，销毁回调返回 false，
客户端承担最终 release；UI、待执行/执行中命令、已连接 EGL 各持独立引用。
解绑/销毁确认后才归还 EGL 引用，最后一个引用释放后才释放 Surface 和 SurfaceTexture。
onDestroy 不提前放弃 View 尚在使用的 UI 引用；Activity 回调不 join GPU/native。

依据：[Android SurfaceTextureListener 所有权契约](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener#onSurfaceTextureDestroyed(android.graphics.SurfaceTexture))。
显示租约全进程最多四个；GLES owner 共用既有两个资源席位；原生 owner 单实例。
永久故障保留资源也占用席位，不靠无上限重建或抢先释放规避。

### P2：迟到 A 确认清理代理后没有连接最新 B

原因：旧直出恢复将 producer 交接与发起交接的显示世代混用，晚确认可能把新的可用窗口漏掉。

修复：删除原生直出/恢复分支。舞台交接严格按旧 GLES 断开显示 → 原生确认替换私有 producer
→ 释放旧 producer → 连接当前显示窗口执行。ACK 不绑定发起时的显示世代。
显示就绪与首帧回调在主线程再次核验输出世代；每个输出世代单独通知首帧。
普通重复后台解绑为幂等操作，不能覆盖含完成回调的 producer 交接屏障。

### P2：普通输入饱和挤掉窗口控制且没有重试

原因：旧 create/setSurface 使用普通输入的 64 项额度，有限慢调用可使有效生命周期请求被丢弃。

修复：原生 producer 控制和 GLES 显示控制各用独立的最新请求合并槽，不占输入额度。
每槽一个执行中命令加一个待执行请求；待执行请求被替换时显式取消并释放其引用。
执行中命令独立持有资源至完成，close 只撤销待执行请求。
普通输入及 reset/消息框保留槽仍保持既有边界；慢调用和输入饱和只记录诊断，
不永久停止帧时钟，有限延迟结束后可自动继续，不依赖额外窗口事件。

### P3：UTF-16LE BOM 导致最小三条 DAT 配置误拒绝

原因：文本探测前未移除 U+FEFF，首条 # 配置未计数；恰好三条时被误判为非文本。

修复：每次解码后先去除开头 BOM，再执行原有三条探测。最终 SCREEN_SIZE 校验、
非法定义拒绝、文件优先级、路径/体积/取消边界均未放宽。
增加四个测试方法，配置测试由 23 项增至 27 项，覆盖明文 UTF-16、原始 LZSS、
header + mask + LZSS、BOM/无 BOM、三/四条、低于阈值及非法最终尺寸。

## 独立审查中的追加修正

- EGL attachment 与输出命令各持一份租约，失败 detach 不能释放命令之外的 UI/EGL 引用。
- onDestroy 保留 TextureView listener 至真实销毁回调，避免框架提前释放仍由 EGL 持有的纹理。
- 后台切换立即撤销输出 wanted；幂等普通 detach 保留交接屏障，不能因准备舞台而继续向旧窗口 swap。
- 迟到 OutputReady 与首帧通知在主线程校验世代；同窗口尺寸改变也会失效旧回调。
- 原生忙警告不停止帧时钟；初始化失败/超时复用已有错误提示，不只留下黑屏或静默退出。

九项新增测试直接使用生产 `SiglusLatestControl`、`SiglusOutputState`、`SiglusLeasePool` 与
`SiglusStepDispatch`：64 项输入饱和、A/B 交错、重复后台屏障、UI/任务/EGL 引用独立、
失败解绑保留、pending 替换释放、close 与执行中命令、四窗口上限、同窗口 resize 的旧 ACK。
它们是确定性调度模型回归，不冒充真实 Handler/EGL/TextureView 集成或设备测试。

## 架构、规范与来源复核

修改限于 engine 宿主与纯 Java 所有权/调度模型；未引入 app UI/Compose 反向依赖。
复用现有初始化文案，无新增设置项或界面主题改动；manifest 仅修正 SurfaceView 过时注释。
提交按 BOM 修复、窗口修复、文档分别追加，不重写旧历史。
上游根 README.md、AGENTS.md、所有预编译引擎库均保持基线原样。
Siglus 原库 SHA-256 仍为 `fdef411e1f30550ef7adb7ab4ff1f383433aee94be7609a481eb18c13c3563df`。
不重新编译引擎、不新增 native ABI、不引入来源未知代码、CuNNy2 或用户导入。

AGENTS 要求的多 Agent 独立审查已执行；指定 Android CLI 审查技能在本机可用命令、
技能/插件目录及项目索引中未找到，未冒称使用，也未安装替代工具。
无图标导航入口和 API 26/27 输入模板兼容仍等待维护者裁决；本轮未修改规范，亦未宣称获豁免。

## 兼容性、性能和验证边界

- 新窗口路线使 Siglus 关闭滤镜/配置无效时也使用私有启动拷贝，增加 GLES3、external OES 依赖
  及显示复制开销。未取得可信配置时，启动拷贝的请求尺寸不标为原始舞台。
- 启动消费者不可用时显示初始化失败并结束，没有不安全的原生直连回退。
  舞台候选在交给 native 前初始化失败，可继续健康的启动拷贝；交接后无法确认所有权则停止呈现。
- 窗口租约避免生命周期违规，不保证永久驱动阻塞可恢复。长期故障保留有界资源并拒绝新准入。
- 旧原库候选 `32342ef3` 及 `ed4dad4e` 的 CI/实机记录只属于旧实现。官方 Artemis 两小时、
  多引擎多个样本的历史验收保留，但不能代替本轮 Siglus 的首次启动、连续后台切换及交接测试。
- 本轮运行普通 Android CI：资源/来源检查、文案、JVM 回归、Ethornell 门禁、Debug APK 编译
  及产物检查；未单独执行 Release/R8 或完整 Lint。人工审查对旧 head 报告的既有 Lint 错误仍是历史记录，
  不声称完整 Lint 通过，也不将旧 head 的测试数写成本轮结果。
- 分析器后台位置偏移及原版亦有的特定 KR 样本偏色继续暂缓；文本排除与用户导入暂停。
