import '../../domain/entities/auth_session.dart';
import '../local/auth_local_store.dart';
import '../models/api_result.dart';
import '../services/auth_api_service.dart';

/// 认证仓库：负责登录、恢复本地会话、退出登录。
///
/// 登录成功后按用户选择把会话写入本地（“在本机保持登录”），
/// 退出登录时清除本地 token，但保留用户名用于下次预填。
class AuthRepository {
  final AuthApiService _service;
  final AuthLocalStore _store;

  AuthSession? _session;

  AuthRepository(this._service, this._store);

  AuthSession? get session => _session;

  bool get isSignedIn => _session != null;

  Future<ApiResult<AuthSession>> login(
    String username,
    String password, {
    bool remember = true,
  }) async {
    final result = await _service.login(username, password);
    final session = result.data;

    if (result.isSuccess && session != null) {
      _session = session;
      _service.applySession(session);
      await _store.saveLastUsername(session.username ?? username);
      if (remember) {
        await _store.saveSession(
          SavedAuthSession(
            token: session.token,
            username: session.username,
            role: session.role,
          ),
        );
      } else {
        await _store.clearSession();
      }
    }
    return result;
  }

  /// 启动时恢复本地会话；没有或读取失败时返回 null（回到登录页）。
  Future<AuthSession?> restore() async {
    try {
      final saved = await _store.readSession();
      if (saved == null) return null;

      final session = AuthSession(
        token: saved.token,
        username: saved.username,
        role: saved.role,
      );
      _session = session;
      _service.applySession(session);
      return session;
    } catch (_) {
      return null;
    }
  }

  Future<String?> lastUsername() async {
    try {
      return await _store.readLastUsername();
    } catch (_) {
      return null;
    }
  }

  Future<void> logout() async {
    _session = null;
    _service.applySession(null);
    try {
      await _store.clearSession();
    } catch (_) {
      // 清理失败不影响本地登出状态。
    }
  }
}
