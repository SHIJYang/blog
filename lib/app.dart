import 'package:flutter/material.dart';

import 'core/constants/api_constants.dart';
import 'core/theme/app_theme.dart';
import 'data/repositories/article_repository.dart';
import 'data/repositories/auth_repository.dart';
import 'data/services/api_client.dart';
import 'data/services/article_api_service.dart';
import 'data/services/auth_api_service.dart';
import 'presentation/pages/blog_upload_page.dart';
import 'routes/app_routes.dart';

class App extends StatefulWidget {
  const App({super.key});

  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  late final ApiClient _apiClient;
  late final AuthRepository _authRepository;
  late final ArticleRepository _articleRepository;

  @override
  void initState() {
    super.initState();
    _apiClient = ApiClient(baseUrl: ApiConstants.baseUrl);
    _authRepository = AuthRepository(AuthApiService(_apiClient));
    _articleRepository = ArticleRepository(ArticleApiService(_apiClient));
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Blog 上传',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      initialRoute: AppRoutes.blogUpload,
      routes: {
        AppRoutes.blogUpload: (_) => BlogUploadPage(
          authRepository: _authRepository,
          articleRepository: _articleRepository,
        ),
      },
    );
  }
}
