import '../../domain/entities/auth_session.dart';
import '../models/api_result.dart';
import '../services/auth_api_service.dart';

class AuthRepository {
  final AuthApiService _service;
  AuthRepository(this._service);

  Future<ApiResult<AuthSession>> login(String username, String password) =>
      _service.login(username, password);

  void logout() => _service.logout();
}
