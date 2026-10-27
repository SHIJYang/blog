import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';

/// 发布完成面板：一句结果说明 + 两个操作，不放图标。
class SuccessPanel extends StatelessWidget {
  const SuccessPanel({
    super.key,
    required this.onAgain,
    required this.onLogout,
    this.title,
  });

  final VoidCallback onAgain;
  final VoidCallback onLogout;
  final String? title;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final articleTitle = title;

    return Center(
      child: SingleChildScrollView(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                articleTitle == null || articleTitle.isEmpty
                    ? '文章已提交到博客后台'
                    : '《$articleTitle》已提交到博客后台',
                textAlign: TextAlign.center,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 13, color: scheme.onSurfaceVariant),
              ),
              const SizedBox(height: AppSpacing.xl),
              Wrap(
                alignment: WrapAlignment.center,
                spacing: AppSpacing.md,
                runSpacing: AppSpacing.md,
                children: [
                  FilledButton(onPressed: onAgain, child: const Text('再发一篇')),
                  TextButton(onPressed: onLogout, child: const Text('退出登录')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
