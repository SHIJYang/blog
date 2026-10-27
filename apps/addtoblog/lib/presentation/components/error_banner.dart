import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';

/// 统一错误提示条：错误色容器 + 图标 + 可选关闭。
class ErrorBanner extends StatelessWidget {
  const ErrorBanner({super.key, required this.message, this.onDismiss});

  final String message;
  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Container(
      padding: EdgeInsets.fromLTRB(
        AppSpacing.md,
        AppSpacing.sm,
        onDismiss == null ? AppSpacing.md : AppSpacing.xs,
        AppSpacing.sm,
      ),
      decoration: BoxDecoration(
        color: scheme.errorContainer.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: scheme.error.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline_rounded, size: 18, color: scheme.error),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              message,
              style: TextStyle(
                fontSize: 12.5,
                height: 1.4,
                color: scheme.onErrorContainer,
              ),
            ),
          ),
          if (onDismiss != null)
            IconButton(
              onPressed: onDismiss,
              icon: const Icon(Icons.close_rounded, size: 16),
              tooltip: '关闭',
              visualDensity: VisualDensity.compact,
              style: IconButton.styleFrom(
                foregroundColor: scheme.onErrorContainer,
              ),
            ),
        ],
      ),
    );
  }
}
