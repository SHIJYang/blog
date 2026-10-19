class AuthSession {
  final String token;
  final String? username;
  final String? role;

  const AuthSession({required this.token, this.username, this.role});

  bool get isAuthorOrAdmin => role == 'AUTHOR' || role == 'ADMIN';

  /// 头像用的首字母，用户名缺失时退化成问号。
  String get initial {
    final name = username?.trim();
    if (name == null || name.isEmpty) return '?';
    return name.substring(0, 1).toUpperCase();
  }
}
