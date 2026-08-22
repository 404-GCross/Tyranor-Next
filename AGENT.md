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
- **禁止在顶部栏放置任何返回按钮/图标**。返回统一依赖系统返回键/手势（`Activity` 默认 `finish()`），不要通过 `onBack` 参数下发返回回调。

### 2. 高度

- 标题区高度固定为 **64dp**。
- 顶部栏整体无需在 64dp 之外再叠加额外高度。

### 3. 标题

- 标题**居左**，水平内边距 `horizontal = 16.dp`，纵向居中。
- 标题字号使用 `MaterialTheme.typography.titleLarge`。
- 标题**必须加粗**：`fontWeight = FontWeight.Bold`。

### 4. 背景色

- 顶部栏**使用页面背景色 `colorScheme.background`（不透明）**（`Modifier.background(colorScheme.background)`），标题与图标统一使用 `colorScheme.onBackground`。
- 禁止使用主题色 `primary` 作为顶部栏背景。

### 5. 状态栏

- 状态栏必须是**透明沉浸式**（`window.statusBarColor = Color.TRANSPARENT`），顶部栏的
  页面背景色向上延伸覆盖状态栏区域。
- 状态栏/导航栏图标使用**深色**（`SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)`），因为页面背景为浅色。
- 顶部栏需使用 `Modifier.statusBarsPadding()`，使标题内容避开状态栏但背景色连续延伸。
- 不要自行给状态栏设置非透明背景色。

### 6. 位置顺序

```
Column(fillMaxSize)                                // 页面根
├── Column(fillMaxWidth, background(background))    // 页面背景色容器（不透明）
│   ├── Column(statusBarsPadding)                    // 避开状态栏（背景延伸至状态栏）
│   └── Column(height 64dp, padding horizontal 16dp)  // 标题区
└── 正文内容
```

> 设置类页面若使用 `MiuixScaffold`，顶部栏在 `topBar` 槽中按同样规则实现：
> `Column(background(background)) { Column(statusBarsPadding) { Row(height 64dp, padding horizontal 16dp) { ... } } }`，
> 并设 `contentWindowInsets = WindowInsets(0.dp)` 避免系统 inset 再次叠加间距。

---

## 跨页面一致性要求

- 所有页面共用 `PlaceholderPage`（或与之一致的结构），禁止各页面各自实现不同样式的顶部栏。
- 新增页面时：页面主体内容放入 `Column` 的正文区域即可，顶部栏保持相同。
- 顶部栏下方禁止放置页面说明/描述文案；正文区域应直接展示该页面的实际内容或列表。
- 所有页面之间的切换必须使用淡入淡出效果；主 Screen 间切换使用 `Crossfade`，Activity 页面进入/返回使用系统 fade 动画或等效淡入淡出动画。
- 组件统一圆角数值为 **8dp**；列表项卡片、功能项卡片、弹窗等圆角组件都应使用 `RoundedCornerShape(8.dp)`。
- 所有弹窗背景必须为白色，且圆角必须使用统一圆角数值 **8dp**。

## 页面内容文字尺寸规范

页面内容（顶部栏除外）只允许使用**两种**文字尺寸，与引擎设置页保持一致：

1. `MaterialTheme.typography.titleMedium` —— 卡片头、对话框标题、列表项主标题（可加粗）。
2. `MaterialTheme.typography.bodyMedium` —— 正文、行值、辅助描述、按钮文字、空态/错误提示。

- **禁止**使用 `bodySmall` / `bodyLarge` / `labelMedium` / `labelLarge` / `headlineMedium` / `headlineSmall` 等其它排版尺寸。
- Miuix preference 组件标题默认用 `headline1`(17sp)，已在 `MiuixSettingsTheme` 中全局覆盖为 16sp（`defaultTextStyles(headline1 = TextStyle(fontSize = 16.sp))`），使其严格落入两档；不要自行在单行上改字号。
- 顶部栏标题不受此限制，仍用 `MaterialTheme.typography.titleLarge` Bold。

## 构建

- 构建命令：`./gradlew assembleDebug --no-daemon`
- 使用 Android CLI（`--sdk=/tmp/androidsdk`）安装到实机。
