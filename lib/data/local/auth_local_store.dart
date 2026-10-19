import 'package:shared_preferences/shared_preferences.dart';

/// 本地保存的登录信息。
///
/// 只保存 token / 用户名 / 角色，**不保存密码**——密码只在登录请求中
/// 使用一次，任何情况下都不落盘。
class SavedAuthSession {
  final String token;
  final String? username;
  final String? role;

  const SavedAuthSession({required this.token, this.username, this.role});
}

/// 登录信息本地存储接口。
///
/// 单独抽成接口，方便测试注入内存实现，也让上层不依赖具体存储方案。
abstract interface class AuthLocalStore {
  Future<SavedAuthSession?> readSession();
  Future<void> saveSession(SavedAuthSession session);
  Future<void> clearSession();

  /// 只记用户名（不记密码），用于下次打开时预填账号。
  Future<String?> readLastUsername();
  Future<void> saveLastUsername(String username);
}

/// 基于 shared_preferences 的实现。
///
/// Windows 上落在应用支持目录的 `shared_preferences.json`，
/// Android/iOS 走各自的原生键值存储。
class SharedPrefsAuthLocalStore implements AuthLocalStore {
  SharedPrefsAuthLocalStore();

  static const String _tokenKey = 'auth.token';
  static const String _usernameKey = 'auth.username';
  static const String _roleKey = 'auth.role';
  static const String _lastUsernameKey = 'auth.last_username';

  SharedPreferences? _prefs;

  /// 存储不可用时（例如工具环境、权限受限）静默降级为「未登录」，
  /// 不影响功能，只是不会自动登录。
  Future<SharedPreferences?> _instance() async {
    if (_prefs != null) return _prefs;
    try {
      _prefs = await SharedPreferences.getInstance();
    } catch (_) {
      return null;
    }
    return _prefs;
  }

  @override
  Future<SavedAuthSession?> readSession() async {
    final prefs = await _instance();
    if (prefs == null) return null;
    final token = prefs.getString(_tokenKey);
    if (token == null || token.isEmpty) return null;
    return SavedAuthSession(
      token: token,
      username: prefs.getString(_usernameKey),
      role: prefs.getString(_roleKey),
    );
  }

  @override
  Future<void> saveSession(SavedAuthSession session) async {
    final prefs = await _instance();
    if (prefs == null) return;
    await prefs.setString(_tokenKey, session.token);
    final username = session.username;
    if (username != null) {
      await prefs.setString(_usernameKey, username);
    } else {
      await prefs.remove(_usernameKey);
    }
    final role = session.role;
    if (role != null) {
      await prefs.setString(_roleKey, role);
    } else {
      await prefs.remove(_roleKey);
    }
  }

  @override
  Future<void> clearSession() async {
    final prefs = await _instance();
    if (prefs == null) return;
    await prefs.remove(_tokenKey);
    await prefs.remove(_usernameKey);
    await prefs.remove(_roleKey);
  }

  @override
  Future<String?> readLastUsername() async {
    final prefs = await _instance();
    return prefs?.getString(_lastUsernameKey);
  }

  @override
  Future<void> saveLastUsername(String username) async {
    final prefs = await _instance();
    if (prefs == null) return;
    await prefs.setString(_lastUsernameKey, username);
  }
}

/// 内存实现：用于单元测试和组件预览。
class MemoryAuthLocalStore implements AuthLocalStore {
  SavedAuthSession? _session;
  String? _lastUsername;

  @override
  Future<SavedAuthSession?> readSession() async => _session;

  @override
  Future<void> saveSession(SavedAuthSession session) async =>
      _session = session;

  @override
  Future<void> clearSession() async => _session = null;

  @override
  Future<String?> readLastUsername() async => _lastUsername;

  @override
  Future<void> saveLastUsername(String username) async =>
      _lastUsername = username;
}
