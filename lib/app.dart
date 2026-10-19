import 'package:flutter/material.dart';

import 'app_dependencies.dart';
import 'core/constants/app_constants.dart';
import 'core/theme/app_theme.dart';
import 'presentation/pages/blog_upload_page.dart';

class App extends StatefulWidget {
  const App({super.key, this.dependencies});

  /// 可在测试 / 预览时注入替身依赖，生产环境自动装配。
  final AppDependencies? dependencies;

  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  late final AppDependencies _dependencies;

  @override
  void initState() {
    super.initState();
    _dependencies = widget.dependencies ?? AppDependencies.production();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppInfo.name,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      home: BlogUploadPage(
        authRepository: _dependencies.authRepository,
        articleRepository: _dependencies.articleRepository,
      ),
    );
  }
}
