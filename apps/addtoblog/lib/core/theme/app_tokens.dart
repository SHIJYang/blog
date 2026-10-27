/// 全局布局令牌：间距、圆角、断点。
///
/// 所有面板与组件只从这里取值，避免出现 8/10/12 混用的间距与不统一圆角。
class AppSpacing {
  AppSpacing._();

  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
  static const double huge = 48;
}

class AppRadius {
  AppRadius._();

  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double pill = 999;
}

class AppLayout {
  AppLayout._();

  /// 内容最大宽度（页面不再用卡片包一层，直接约束正文宽度）。
  static const double contentMaxWidth = 960;

  /// 登录 / 完成等单列表单的阅读宽度。
  static const double formMaxWidth = 420;

  /// 宽屏断点：达到该宽度后编辑面板改为左右分栏。
  static const double twoPaneBreakpoint = 860;

  /// 窄屏断点：达到该宽度后页面改用紧凑内边距。
  static const double compactBreakpoint = 560;

  /// 编辑器表单栏在宽屏下的固定宽度。
  static const double formPaneWidth = 400;
}
