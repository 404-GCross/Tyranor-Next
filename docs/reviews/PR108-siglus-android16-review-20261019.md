# PR #108：Android 16 显示窗口与界面引用解耦复核

日期：2026-10-19（Asia/Singapore）。固定前序候选 `bc8de9b3`、基线 `740d7c13`。
响应 [维护者全量复审 6265732878](https://github.com/Weiss-UltimateSavior/Tyranor-Next/pull/108#issuecomment-6265732878)，
代码修复 `e6935f500df8c09d885ffc034c08179c1cf850fb`；普通追加提交，不重写历史。

## 问题原因与影响范围

旧 frame-available listener 的清理有效，但 Android 16 的 TextureView 在对应框架开关开启时还会注册独立的隐藏
frame-rate listener。若输出 EGL 解绑/销毁持续失败，重试耗尽仍保留 outputLease，静态
retained record 可经 SurfaceTexture 的 frame-rate Handler、TextureView、Activity Context
连带保留已销毁界面。注册本身即可形成持有链，不要求实际发出帧率请求。

这是一项有界、永久驱动清理失败条件下的 P3；不是正常退出必现，也不声称崩溃或无界增长。
EGL 正常归还租约时不成立，仅 eglTerminate 失败也不足以成立。

## 修复与引用链检查

仅有效开启滤镜的显示 TextureView 使用 application Context。Activity 终结时依次：

1. 停帧、标记 destroying，取消尺寸读取及界面观察。
2. 从 rootLayout 正常 removeView；此时 SurfaceTextureListener 仍在，框架销毁回调正常
   归还 UI lease。当前父布局没有 animation/LayoutTransition，公开移除路径同步清 parent
   和 AttachInfo；不在 texture 销毁回调里重入 removeView。
3. removeView 返回后清 SurfaceTextureListener、OnTouchListener；API29+ 清继承的
   content-capture session 缓存，避免保留旧窗口关联。
4. 按原有顺序关闭 native owner；producer 销毁后才处置 consumer。

只替换 Context 不够：surface/touch listener 和父布局都曾直接持有 Activity。
只改弱引用不够：View 的构造 Context 仍可能保留 Activity。
先清 surface listener 也不正确：可能跳过 UI lease 的归还。

修复后，已审的持有链止于已隔离的 application-context View；隐藏 Handler 本身没有被删除，
它仅从传入 Handler 取得 Looper，不保留原 ViewRoot Handler。故障下仍可有界保留必要驱动资源
及该孤立 View，不声称永久驱动故障被恢复或所有未来框架/OEM 私有引用都已覆盖。

关闭/空链的 SurfaceView 构造和上游同步路径不变。临时后台销毁不清宿主 listener，恢复路径不变。
后台早已销毁纹理时，终结移除可能不再触发纹理回调，但仍清父布局与业务监听；未 attach、
登记窗口前即结束的初始化失败或容量拒绝没有已逃逸窗口租约；登记后初始化失败仍按正常
detach 归还 UI lease。私有 producer listener、EGL 引用计数、
重试和资源准入不变；不反射隐藏 API、不提前 release texture、不修改原生库或 JNI ABI。

## 复核证据

主 Agent 与两路独立 Agent 复核实际差异、创建/后台/销毁调用点、UI/命令/EGL 租约、迟到回调
世代检查及未初始化/容量拒绝分支，未发现本轮新增的可确认 P0–P3。
这是增量静态复核；维护者此前覆盖全量 PR 的构建/审查仍固定于 bc8de9b3，不冒充本轮执行。

固定源码：[AOSP Android 16 TextureView](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/view/TextureView.java)、
[SurfaceTexture](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/graphics/java/android/graphics/SurfaceTexture.java)、
[ViewGroup](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/view/ViewGroup.java)、
[View](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/view/View.java)。

按提交者约束仅运行候选 Android CI，精确 head 和结果记录在 PR 评论及正文；未做本地构建、
实机、Android16 驱动故障注入或 heap/GC 验证，也未另跑 Release/R8 或完整 Lint。
未为框架引用清理新增重复实现的模型测试；现有 CI 不等同真实框架回收证据。
指定 Android CLI 审查技能在本机仍未找到，未冒称使用。

## 保留的规范裁决与提交边界

导航图标 P3 按提交者意见保持当前无图标布局。另向 PR 提交四张裁掉顶部状态栏的原图对比：
当前实机、分类加图标错误示例、添加入口加图标错误示例、原版群聊导航图标示例。
滤镜分类/添加入口属于局部装配流程，原版群聊项属于不同目标的功能跳转；这一差异是申请
豁免的理由，不把申请或本地代码注释视为维护者已批准。继续复用 AppNavItem，不修改 AGENTS。
截图含已摘出的 CuNNy2，仅用于展示布局，不能代表本候选重新提供该滤镜。

API26/27 可编辑输入模板例外仍待裁决；不撤销已确认必要的兼容分支。
根 README、AGENTS、应用版本、Beta 工作流及全部预编译库不变；截图、裁剪文件和本机索引不入 Git。
