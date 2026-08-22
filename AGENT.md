# Tyranor Next — AGENT 规范

本文档是 AI Agent 在本项目内开发时必须遵循的统一规范。新增或修改代码前请先阅读，与既有实现保持一致。

## 技术栈

- Android Jetpack Compose + Material 3
- Kotlin
- 底部导航结构：`app/src/main/java/com/example/tyranornext/ui/main/MainScreen.kt`
- 各页面位于 `app/src/main/java/com/example/tyranornext/ui/pages/`

---

## 页面顶部栏统一规范

所有页面（首页 / 游戏 / 书库 / 设置）的顶部栏必须统一，规则如下。当前已由统一入口
`com.example.tyranornext.ui.pages.PlaceholderPage` 实现，新页面应复用或遵循同等效果。

### 1. 结构

- 顶部栏使用 **Column + Centre**，**不使用** Material3 的 `TopAppBar` / `Scaffold`。
- 页面整体由外层 `Column` 组装，顺序固定为：顶部栏 → 正文内容。

### 2. 高度

- 标题区高度固定为 **64dp**。
- 顶部栏整体无需在 64dp 之外再叠加额外高度。

### 3. 标题

- 标题**居左**，水平内边距 `horizontal = 16.dp`，纵向居中。
- 标题字号使用 `MaterialTheme.typography.titleLarge`。

### 4. 背景色

- 顶部栏**不设置任何独立背景色**（透明），与页面背景 `colorScheme.background` 保持一致。
- 禁止给顶部栏单独填充 `surfaceContainer` / 其他色值。

### 5. 状态栏沉浸

- 状态栏必须是**透明沉浸式**，跟随页面背景色（`MainActivity` 已通过
  `enableEdgeToEdge(..., SystemBarStyle.light(TRANSPARENT, TRANSPARENT))` 配置）。
- 顶部栏需使用 `Modifier.statusBarsPadding()`，使其标题内容避开状态栏但不产生异色区域。
- 不要自行给状态栏设置非透明背景色。

### 6. 位置顺序

```
Column(fillMaxSize)                 // 页面根
├── 顶部栏
│   ├── Column(statusBarsPadding)   // 避开状态栏
│   └── Column(height 64dp, padding horizontal 16dp)  // 标题区
└── 正文内容
```

---

## 跨页面一致性要求

- 所有页面共用 `PlaceholderPage`（或与之一致的结构），禁止各页面各自实现不同样式的顶部栏。
- 新增页面时：页面主体内容放入 `Column` 的正文区域即可，顶部栏保持相同。
- 顶部栏下方禁止放置页面说明/描述文案；正文区域应直接展示该页面的实际内容或列表。
- 所有页面之间的切换必须使用淡入淡出效果；主 Screen 间切换使用 `Crossfade`，Activity 页面进入/返回使用系统 fade 动画或等效淡入淡出动画。
- 组件统一圆角数值为 **8dp**；列表项卡片、功能项卡片、弹窗等圆角组件都应使用 `RoundedCornerShape(8.dp)`。
- 所有弹窗背景必须为白色，且圆角必须使用统一圆角数值 **8dp**。

## 构建

- 构建命令：`./gradlew assembleDebug --no-daemon`
- 使用 Android CLI（`--sdk=/tmp/androidsdk`）安装到实机。
