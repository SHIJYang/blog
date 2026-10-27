import 'package:flutter/material.dart';
import 'package:markdown_widget/markdown_widget.dart';

import '../../core/theme/app_tokens.dart';

/// Markdown 预览区：一块浅色底，没有标题栏和字数统计。
///
/// MarkdownWidget 内部就是 ListView（自带滚动），必须给有界高度，
/// 外面不能再套 SingleChildScrollView。
class PreviewPane extends StatelessWidget {
  const PreviewPane({super.key, required this.content});

  final String content;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasContent = content.trim().isNotEmpty;

    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: hasContent
          ? MarkdownWidget(
              data: content,
              padding: const EdgeInsets.all(AppSpacing.xl),
              config: isDark
                  ? MarkdownConfig.darkConfig
                  : MarkdownConfig.defaultConfig,
            )
          : Center(
              child: Text(
                '暂无内容',
                style: TextStyle(
                  fontSize: 12.5,
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ),
    );
  }
}
