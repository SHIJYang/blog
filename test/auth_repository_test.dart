import 'package:flutter_test/flutter_test.dart';

import 'package:addtoblog/data/local/auth_local_store.dart';
import 'package:addtoblog/data/models/api_result.dart';
import 'package:addtoblog/data/repositories/auth_repository.dart';
import 'package:addtoblog/data/services/api_client.dart';
import 'package:addtoblog/data/services/auth_api_service.dart';

/// 假客户端：不走网络，直接按后端约定返回登录结果。
class _FakeApiClient extends ApiClient {
  _FakeApiClient() : super(baseUrl: 'https://example.test');

  @override
  Future<ApiResult<T>> post<T>(
    String path,
    Map<String, dynamic> body, [
    T Function(dynamic)? fromData,
  ]) async {
    if (path == '/api/auth/login') {
      return ApiResult<T>(
        code: 200,
        message: 'ok',
        data: fromData?.call(<String, dynamic>{
          'token': 'token-123',
          'username': 'alice',
          'role': 'AUTHOR',
        }),
      );
    }
    return ApiResult<T>(code: 200, message: 'ok');
  }
}

void main() {
  group('AuthRepository 本地登录信息', () {
    test('登录成功后写入本地，并给 HTTP 客户端带上 token', () async {
      final client = _FakeApiClient();
      final store = MemoryAuthLocalStore();
      final repository = AuthRepository(AuthApiService(client), store);

      final result = await repository.login('alice', 'pw');

      expect(result.isSuccess, isTrue);
      expect(client.token, 'token-123');

      final saved = await store.readSession();
      expect(saved, isNotNull);
      expect(saved!.token, 'token-123');
      expect(saved.username, 'alice');
      expect(await store.readLastUsername(), 'alice');
    });

    test('取消「保持登录」时不落盘 token，但记住用户名', () async {
      final client = _FakeApiClient();
      final store = MemoryAuthLocalStore();
      final repository = AuthRepository(AuthApiService(client), store);

      await repository.login('alice', 'pw', remember: false);

      expect(await store.readSession(), isNull);
      expect(await store.readLastUsername(), 'alice');
      expect(client.token, 'token-123');
    });

    test('重启后能恢复会话并还原 token', () async {
      final client = _FakeApiClient();
      final store = MemoryAuthLocalStore();
      await store.saveSession(
        const SavedAuthSession(
          token: 'saved-token',
          username: 'bob',
          role: 'ADMIN',
        ),
      );

      final repository = AuthRepository(AuthApiService(client), store);
      final session = await repository.restore();

      expect(session, isNotNull);
      expect(session!.username, 'bob');
      expect(client.token, 'saved-token');
      expect(repository.isSignedIn, isTrue);
    });

    test('退出登录清除 token 与本地会话，保留用户名', () async {
      final client = _FakeApiClient();
      final store = MemoryAuthLocalStore();
      final repository = AuthRepository(AuthApiService(client), store);
      await repository.login('alice', 'pw');

      await repository.logout();

      expect(client.token, isNull);
      expect(await store.readSession(), isNull);
      expect(await store.readLastUsername(), 'alice');
      expect(repository.isSignedIn, isFalse);
    });

    test('本地没有会话时 restore 返回 null', () async {
      final repository = AuthRepository(
        AuthApiService(_FakeApiClient()),
        MemoryAuthLocalStore(),
      );

      expect(await repository.restore(), isNull);
    });
  });
}
