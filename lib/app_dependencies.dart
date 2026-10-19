import 'core/constants/api_constants.dart';
import 'data/local/auth_local_store.dart';
import 'data/repositories/article_repository.dart';
import 'data/repositories/auth_repository.dart';
import 'data/services/api_client.dart';
import 'data/services/article_api_service.dart';
import 'data/services/auth_api_service.dart';

/// 应用依赖装配。集中在一处，UI 层不再自己 new 服务。
class AppDependencies {
  final ApiClient apiClient;
  final AuthRepository authRepository;
  final ArticleRepository articleRepository;

  AppDependencies({
    required this.apiClient,
    required this.authRepository,
    required this.articleRepository,
  });

  factory AppDependencies.production({
    String baseUrl = ApiConstants.baseUrl,
    AuthLocalStore? authStore,
  }) {
    final client = ApiClient(baseUrl: baseUrl);
    return AppDependencies(
      apiClient: client,
      authRepository: AuthRepository(
        AuthApiService(client),
        authStore ?? SharedPrefsAuthLocalStore(),
      ),
      articleRepository: ArticleRepository(ArticleApiService(client)),
    );
  }
}
