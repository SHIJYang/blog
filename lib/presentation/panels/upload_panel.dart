import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';
import '../components/drop_area.dart';
import '../components/error_banner.dart';

/// 选择文件面板：一块投放区占满可用空间。
class UploadPanel extends StatelessWidget {
  const UploadPanel({
    super.key,
    required this.dragging,
    required this.busy,
    required this.onPick,
    required this.onDragEnter,
    required this.onDragLeave,
    required this.onDragDone,
    this.error,
    this.onDismissError,
  });

  final bool dragging;
  final bool busy;
  final VoidCallback onPick;
  final VoidCallback onDragEnter;
  final VoidCallback onDragLeave;
  final ValueChanged<List<DropItem>> onDragDone;
  final String? error;
  final VoidCallback? onDismissError;

  @override
  Widget build(BuildContext context) {
    final errorText = error;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (errorText != null && errorText.isNotEmpty) ...[
          ErrorBanner(message: errorText, onDismiss: onDismissError),
          const SizedBox(height: AppSpacing.lg),
        ],
        Expanded(
          child: DropArea(
            dragging: dragging,
            busy: busy,
            onPick: onPick,
            onDragEnter: onDragEnter,
            onDragLeave: onDragLeave,
            onDragDone: onDragDone,
          ),
        ),
      ],
    );
  }
}
