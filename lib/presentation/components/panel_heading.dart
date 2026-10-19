import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';

/// 阶段标题：一句话说明当前在做什么，右侧可放操作。
///
/// 不再有图标徽章与副标题堆叠，副标题只用于承载真实信息（如文件名）。
class PanelHeading extends StatelessWidget {
  const PanelHeading({
    super.key,
    required this.title,
    this.subtitle,
    this.actions = const [],
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final subtitleText = subtitle;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                title,
                style: text.titleLarge?.copyWith(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.2,
                ),
              ),
              if (subtitleText != null && subtitleText.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  subtitleText,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: text.bodySmall?.copyWith(
                    fontSize: 12.5,
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
        ),
        if (actions.isNotEmpty) ...[
          const SizedBox(width: AppSpacing.md),
          Wrap(spacing: AppSpacing.sm, children: actions),
        ],
      ],
    );
  }
}
