import '../../domain/entities/auth_session.dart';
import '../models/api_result.dart';
import '../models/login_request.dart';
import 'api_client.dart';

class AuthApiService {
  final ApiClient _client;
  AuthApiService(this._client);

  /// 调用 POST /api/auth/login
  /// 后端返回 { code, message, data: { token, role, ... } }
  Future<ApiResult<AuthSession>> login(String username, String password) async {
    final body = LoginRequest(username: username, password: password).toJson();

    final result = await _client.post<AuthSession>('/api/auth/login', body, (
      data,
    ) {
      final map = Map<String, dynamic>.from(data as Map);
      // 兼容常见字段名：token / accessToken / jwt
      final token =
          (map['token'] ??
                  map['accessToken'] ??
                  map['jwt'] ??
                  map['tokenValue'])
              ?.toString();
      if (token == null || token.isEmpty) {
        throw StateError('登录响应缺少 token 字段');
      }
      return AuthSession(
        token: token,
        username: map['username']?.toString(),
        role: map['role']?.toString(),
      );
    });

    if (result.isSuccess && result.data != null) {
      _client.setToken(result.data!.token);
    }
    return result;
  }

  void logout() => _client.clearToken();
}
