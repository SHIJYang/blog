import '../../core/constants/api_constants.dart';
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

    return _client.post<AuthSession>(ApiConstants.login, body, (data) {
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
  }

  /// 把会话（或 null）应用到 HTTP 客户端，后续请求自动带上 Bearer Token。
  void applySession(AuthSession? session) => _client.setToken(session?.token);
}
