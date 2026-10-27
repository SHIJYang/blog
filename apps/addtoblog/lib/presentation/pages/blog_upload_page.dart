import 'package:flutter/material.dart';

import '../../core/constants/app_constants.dart';
import '../../core/theme/app_tokens.dart';
import '../../data/repositories/article_repository.dart';
import '../../data/repositories/auth_repository.dart';
import '../components/account_chip.dart';
import '../components/app_top_bar.dart';
import '../components/panel_heading.dart';
import '../panels/editor_panel.dart';
import '../panels/login_panel.dart';
import '../panels/restoring_panel.dart';
import '../panels/success_panel.dart';
import '../panels/upload_panel.dart';
import '../providers/blog_upload_provider.dart';

/// 页面外壳：扁平布局，只有「顶栏 + 一条分隔线 + 阶段标题 + 内容」。
class BlogUploadPage extends StatefulWidget {
  const BlogUploadPage({
    super.key,
    required this.authRepository,
    required this.articleRepository,
  });

  final AuthRepository authRepository;
  final ArticleRepository articleRepository;

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
    _provider.initialize();
  }

  @override
  void dispose() {
    _provider.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _provider,
      builder: (context, _) {
        final stage = _provider.stage;
        final meta = _metaFor(stage, _provider.fileName);
        final signedIn =
            stage != UploadStage.login && stage != UploadStage.restoring;
        // 登录 / 完成这类短内容整体垂直居中，避免标题和表单被拉开。
        final centered =
            stage == UploadStage.login ||
            stage == UploadStage.restoring ||
            stage == UploadStage.done;
        final stageMaxWidth = centered
            ? AppLayout.formMaxWidth
            : AppLayout.contentMaxWidth;

        return Scaffold(
          body: SafeArea(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final compact =
                    constraints.maxWidth < AppLayout.compactBreakpoint;
                final horizontal = compact ? AppSpacing.lg : AppSpacing.xl;

                final heading = PanelHeading(
                  title: meta.title,
                  subtitle: meta.subtitle,
                  actions: _headingActions(stage),
                );
                final body = _buildStageBody(stage);

                return Center(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      maxWidth: AppLayout.contentMaxWidth,
                      maxHeight: constraints.maxHeight,
                    ),
                    child: Padding(
                      padding: EdgeInsets.fromLTRB(
                        horizontal,
                        AppSpacing.lg,
                        horizontal,
                        AppSpacing.lg,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          AppTopBar(
                            title: AppInfo.name,
                            trailing: signedIn
                                ? AccountChip(
                                    username: _provider.username,
                                    onLogout: () => _provider.logout(),
                                  )
                                : null,
                          ),
                          const SizedBox(height: AppSpacing.md),
                          const Divider(height: 1),
                          Expanded(
                            child: Center(
                              child: ConstrainedBox(
                                constraints: BoxConstraints(
                                  maxWidth: stageMaxWidth,
                                ),
                                child: centered
                                    ? Column(
                                        mainAxisSize: MainAxisSize.min,
                                        crossAxisAlignment:
                                            CrossAxisAlignment.stretch,
                                        children: [
                                          const SizedBox(height: AppSpacing.xl),
                                          heading,
                                          const SizedBox(height: AppSpacing.xl),
                                          body,
                                        ],
                                      )
                                    : Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.stretch,
                                        children: [
                                          const SizedBox(height: AppSpacing.xl),
                                          heading,
                                          const SizedBox(height: AppSpacing.xl),
                                          Expanded(child: body),
                                        ],
                                      ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        );
      },
    );
  }

  List<Widget> _headingActions(UploadStage stage) {
    if (stage != UploadStage.edit && stage != UploadStage.submitting) {
      return const [];
    }
    return [
      TextButton(
        onPressed: _provider.isSubmitting ? null : _provider.resetDraft,
        child: const Text('重新选择'),
      ),
    ];
  }

  Widget _buildStageBody(UploadStage stage) {
    return switch (stage) {
      UploadStage.restoring => const RestoringPanel(),
      UploadStage.login => LoginPanel(
        busy: _provider.isLoading,
        error: _provider.error,
        initialUsername: _provider.lastUsername,
        rememberMe: _provider.rememberMe,
        onRememberChanged: _provider.setRememberMe,
        onSubmit: _provider.login,
      ),
      UploadStage.drop => UploadPanel(
        busy: _provider.isLoading,
        onPick: _provider.pickFile,
        onFilesDropped: _provider.onFilesDropped,
        error: _provider.error,
        onDismissError: _provider.clearError,
      ),
      UploadStage.edit || UploadStage.submitting => EditorPanel(
        fileName: _provider.fileName,
        content: _provider.parsedFile?.content ?? '',
        titleController: _provider.titleController,
        summaryController: _provider.summaryController,
        status: _provider.status,
        allowComment: _provider.allowComment,
        submitting: _provider.isSubmitting,
        error: _provider.error,
        onDismissError: _provider.clearError,
        onStatusChanged: _provider.setStatus,
        onAllowCommentChanged: _provider.setAllowComment,
        onPublish: () => _provider.publish(),
        onCancel: _provider.resetDraft,
      ),
      UploadStage.done => SuccessPanel(
        title: _provider.publishedTitle,
        onAgain: _provider.startOver,
        onLogout: _provider.logout,
      ),
    };
  }
}

typedef _StageMeta = ({String title, String? subtitle});

_StageMeta _metaFor(UploadStage stage, String? fileName) => switch (stage) {
  UploadStage.restoring => (title: '加载中', subtitle: null),
  UploadStage.login => (title: '登录', subtitle: null),
  UploadStage.drop => (title: '选择文件', subtitle: null),
  UploadStage.edit ||
  UploadStage.submitting => (title: '预览并发布', subtitle: fileName),
  UploadStage.done => (title: '发布完成', subtitle: null),
};
