import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';

import '../../core/constants/file_constants.dart';
import '../../core/theme/app_tokens.dart';

/// 拖拽 / 点击选择文件的投放区。
///
/// 只有一句说明 + 一个按钮，不加图标；拖入时整块高亮。
class DropArea extends StatelessWidget {
  const DropArea({
    super.key,
    required this.dragging,
    required this.busy,
    required this.onPick,
    required this.onDragEnter,
    required this.onDragLeave,
    required this.onDragDone,
  });

  final bool dragging;
  final bool busy;
  final VoidCallback onPick;
  final VoidCallback onDragEnter;
  final VoidCallback onDragLeave;
  final ValueChanged<List<DropItem>> onDragDone;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final extensions = FileConstants.supportedExtensions
        .map((e) => '.$e')
        .join(' / ');

    return DropTarget(
      onDragEntered: (_) => onDragEnter(),
      onDragExited: (_) => onDragLeave(),
      onDragDone: (details) => onDragDone(details.files),
      child: MouseRegion(
        cursor: busy ? SystemMouseCursors.basic : SystemMouseCursors.click,
        child: GestureDetector(
          onTap: busy ? null : onPick,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 140),
            decoration: BoxDecoration(
              color: dragging
                  ? scheme.primary.withValues(alpha: 0.06)
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(AppRadius.lg),
              border: Border.all(
                color: dragging ? scheme.primary : scheme.outlineVariant,
                width: 1.2,
              ),
            ),
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      dragging ? '松手导入' : '拖入 Markdown 文件',
                      style: text.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Text(
                      extensions,
                      style: text.bodySmall?.copyWith(
                        fontSize: 12,
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.xl),
                    OutlinedButton(
                      onPressed: busy ? null : onPick,
                      child: busy
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('选择文件'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
