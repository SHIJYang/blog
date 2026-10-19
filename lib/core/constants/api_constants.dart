class ApiConstants {
  ApiConstants._();

  /// 后端地址，可切换为线上环境
  static const String baseUrl = 'https://api.qisont.art';

  // ---- 认证 ----
  static const String login = '/api/auth/login';

  // ---- 文章 ----
  static const String adminArticles = '/api/admin/articles';
}
