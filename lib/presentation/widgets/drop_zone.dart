import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';

import '../../core/constants/file_constants.dart';

class DropZone extends StatelessWidget {
  final bool dragging;
  final bool loading;
  final VoidCallback onLogout;
  final ValueChanged<List<DropItem>> onDragDone;
  final ValueChanged<bool> onDragEntered;
  final VoidCallback onDragExited;
  final VoidCallback onPickFile;
  final String? error;

  const DropZone({
    super.key,
    required this.dragging,
    required this.loading,
    required this.onLogout,
    required this.onDragDone,
    required this.onDragEntered,
    required this.onDragExited,
    required this.onPickFile,
    this.error,
  });

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Expanded(
              child: Text(
                '上传 Markdown 文件',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
            ),
            TextButton(onPressed: onLogout, child: const Text('退出登录')),
          ],
        ),
        const SizedBox(height: 12),
        DropTarget(
          onDragDone: (details) => onDragDone(details.files),
          onDragEntered: (_) => onDragEntered(true),
          onDragExited: (_) => onDragExited(),
          child: Container(
            width: double.infinity,
            height: 200,
            decoration: BoxDecoration(
              border: Border.all(
                color: dragging ? primary : Colors.grey,
                width: 2,
              ),
              borderRadius: BorderRadius.circular(8),
              color: dragging ? primary.withValues(alpha: 0.05) : null,
            ),
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.cloud_upload_outlined,
                    size: 48,
                    color: dragging ? primary : Colors.grey,
                  ),
                  const SizedBox(height: 12),
                  Text(dragging ? '松开鼠标上传' : '拖拽文件到此处，或'),
                  TextButton.icon(
                    onPressed: loading ? null : onPickFile,
                    icon: const Icon(Icons.folder_open),
                    label: const Text('点击选择文件'),
                  ),
                  Text(
                    '支持 ${FileConstants.supportedExtensions.map((e) => '.$e').join(' / ')}',
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
            ),
          ),
        ),
        if (error != null) ...[
          const SizedBox(height: 12),
          Text(error!, style: const TextStyle(color: Colors.red)),
        ],
      ],
    );
  }
}
