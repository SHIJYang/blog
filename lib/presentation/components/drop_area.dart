import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';

import '../../core/constants/file_constants.dart';
import '../../core/theme/app_tokens.dart';

/// 文件投放区。
///
/// 这里直接订阅 desktop_drop 的底层拖放事件，而不是使用它的 `DropTarget`。
/// `DropTarget` 只有在「进入事件按坐标命中自身边界」之后才认为拖拽有效，
/// 一旦坐标或时序对不上，松手时的 performOperation 会被静默丢弃，
/// 表现就是拖入文件完全没反应。直接监听原始事件可以绕开这套状态机。
class DropArea extends StatefulWidget {
  const DropArea({
    super.key,
    required this.busy,
    required this.onPick,
    required this.onFilesDropped,
  });

  final bool busy;
  final VoidCallback onPick;
  final ValueChanged<List<DropItem>> onFilesDropped;

  @override
  State<DropArea> createState() => _DropAreaState();
}

class _DropAreaState extends State<DropArea> {
  bool _dragging = false;

  // 保存稳定的引用，便于在 dispose 时精确注销。
  late final RawDropListener _listener = _handleRawEvent;

  void _handleRawEvent(DropEvent event) {
    if (!mounted) return;

    if (event is DropEnterEvent || event is DropUpdateEvent) {
      if (!_dragging) setState(() => _dragging = true);
      return;
    }
    if (event is DropExitEvent) {
      if (_dragging) setState(() => _dragging = false);
      return;
    }
    if (event is DropDoneEvent) {
      if (_dragging) setState(() => _dragging = false);
      if (event.files.isNotEmpty) {
        widget.onFilesDropped(event.files);
      }
    }
  }

  @override
  void initState() {
    super.initState();
    // init() 负责挂上平台通道回调，重复调用是安全的。
    DesktopDrop.instance.init();
    DesktopDrop.instance.addRawDropEventListener(_listener);
  }

  @override
  void dispose() {
    DesktopDrop.instance.removeRawDropEventListener(_listener);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final extensions = FileConstants.supportedExtensions
        .map((e) => '.$e')
        .join(' / ');
    final hint = widget.busy
        ? '正在读取…'
        : (_dragging ? '松手导入' : '拖入 Markdown 文件');

    return MouseRegion(
      cursor: widget.busy ? SystemMouseCursors.basic : SystemMouseCursors.click,
      child: GestureDetector(
        onTap: widget.busy ? null : widget.onPick,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 140),
          decoration: BoxDecoration(
            color: _dragging
                ? scheme.primary.withValues(alpha: 0.06)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(AppRadius.lg),
            border: Border.all(
              color: _dragging ? scheme.primary : scheme.outlineVariant,
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
                    hint,
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
                    onPressed: widget.busy ? null : widget.onPick,
                    child: widget.busy
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
    );
  }
}
