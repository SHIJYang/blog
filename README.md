# addtoblog · Markdown 发布器

把本地 Markdown 文件拖进窗口，确认标题/摘要/发布状态后一键发布到博客后台。

界面是扁平的：没有卡片、没有步骤条、没有说明性文案堆叠，只保留内容与操作。

## 运行

```bash
flutter pub get
flutter run -d windows      # 桌面端（推荐）
flutter test                # 单元 + 布局测试
```

默认窗口 1200×820，标题「Markdown 发布器」。

## 目录结构

```
lib/
├── app.dart                  # MaterialApp：主题、首页装配
├── app_dependencies.dart     # 依赖装配（ApiClient / 仓库 / 本地存储）
├── core/
│   ├── constants/            # 接口地址、应用信息、文件限制
│   ├── theme/                # app_theme（明暗主题）+ app_tokens（间距/圆角/断点）
│   └── utils/file_parser.dart
├── data/
│   ├── local/                # 登录信息本地存储（shared_preferences）
│   ├── models/               # 请求体与接口返回
│   ├── repositories/         # 认证、文章
│   └── services/             # HTTP 客户端与接口调用
├── domain/entities/          # Article / AuthSession
└── presentation/
    ├── components/           # 纯 UI：顶栏、阶段标题、拖拽区、预览、错误条…
    ├── panels/               # 阶段面板：登录 / 选择文件 / 编辑发布 / 完成
    ├── pages/                # 页面外壳：顶栏 + 分隔线 + 标题 + 内容
    └── providers/            # 页面状态机
```

前端"纯组件式"约定：

- `presentation/components/` 里的组件只接收数据和回调，不读仓储、不发请求；
- `presentation/panels/` 里每个阶段面板同样只接数据 + 回调，便于测试与复用；
- `pages/` 只负责组合：顶栏 + 分隔线 + 阶段标题 + 内容；
- 所有间距、圆角、断点统一取自 `core/theme/app_tokens.dart`。

## 布局规则

页面结构固定为四层，自上而下：顶栏（应用名 + 账号）→ 细分隔线 → 阶段标题 → 内容。
没有卡片外框、阴影、步骤条和底部说明条。

| 宽度 | 行为 |
| --- | --- |
| ≥ 860px | 编辑面板左右分栏：左表单 400px，右实时预览 |
| < 860px | 单栏 + 「编辑 / 预览」文字标签切换 |
| < 560px | 页面内边距收紧 |
| 登录 / 完成 | 标题与内容整体垂直居中，宽度收窄到 420px |

滚动只发生在内容区：预览区 `MarkdownWidget` 自带滚动，外层不再套
`SingleChildScrollView`（否则会出现"垂直视图高度无界"的布局异常）。

## 登录信息保存在本机

- 登录成功后 token / 用户名 / 角色写入 `shared_preferences`
  （Windows 位于 `%APPDATA%\com.example\flutter_application_1\shared_preferences.json`）；
- 下次启动自动恢复会话，直接进入「选择文件」，不需要重新登录；
- 登录页「保持登录」开关关闭时：只记用户名、不落盘 token；
- 退出登录清除 token，但保留用户名用于下次预填；
- **密码只用于登录请求，任何情况下都不会被存储**。

若需要更高安全级别，可把 `AuthLocalStore` 换成 `flutter_secure_storage` 实现，
其余代码无需改动。
