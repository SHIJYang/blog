import 'package:flutter/material.dart';

import '../../data/repositories/article_repository.dart';
import '../../data/repositories/auth_repository.dart';
import '../providers/blog_upload_provider.dart';
import '../widgets/article_editor.dart';
import '../widgets/drop_zone.dart';
import '../widgets/login_form.dart';
import '../widgets/success_view.dart';

class BlogUploadPage extends StatefulWidget {
  final AuthRepository authRepository;
  final ArticleRepository articleRepository;

  const BlogUploadPage({
    super.key,
    required this.authRepository,
    required this.articleRepository,
  });

  @override
  State<BlogUploadPage> createState() => _BlogUploadPageState();
}

class _BlogUploadPageState extends State<BlogUploadPage> {
  late final BlogUploadProvider _provider;

  @override
  void initState() {
    super.initState();
    _provider = BlogUploadProvider(
      authRepository: widget.authRepository,
      articleRepository: widget.articleRepository,
    );
  }

  @override
  void dispose() {
    _provider.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('发布博客')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 800),
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: ListenableBuilder(
                    listenable: _provider,
                    builder: (context, _) => _buildBody(),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBody() {
    switch (_provider.stage) {
      case UploadStage.login:
        return LoginForm(
          loading: _provider.isLoading,
          error: _provider.error,
          onSubmit: _provider.login,
        );
      case UploadStage.drop:
        return DropZone(
          dragging: _provider.isDragging,
          loading: _provider.isLoading,
          onLogout: _provider.logout,
          onDragDone: _provider.onFilesDropped,
          onDragEntered: (_) => _provider.onDragEntered(),
          onDragExited: _provider.onDragExited,
          onPickFile: _provider.pickFile,
          error: _provider.error,
        );
      case UploadStage.edit:
      case UploadStage.submitting:
        return ArticleEditor(provider: _provider);
      case UploadStage.done:
        return SuccessView(onAgain: _provider.resetDraft);
    }
  }
}
