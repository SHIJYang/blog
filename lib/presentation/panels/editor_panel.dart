import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_tokens.dart';
import '../../domain/entities/article.dart';
import '../components/error_banner.dart';
import '../components/preview_pane.dart';

enum _EditorTab { edit, preview }

/// 编辑面板：宽屏左右分栏（表单 + 预览），窄屏用文字标签切换。
///
/// 纯组件：只接收数据与回调；预览区自带滚动，外层不再包滚动容器。
class EditorPanel extends StatefulWidget {
  const EditorPanel({
    super.key,
    required this.titleController,
    required this.summaryController,
    required this.content,
    required this.status,
    required this.allowComment,
    required this.submitting,
    required this.onStatusChanged,
    required this.onAllowCommentChanged,
    required this.onPublish,
    required this.onCancel,
    this.fileName,
    this.error,
    this.onDismissError,
  });

  final TextEditingController titleController;
  final TextEditingController summaryController;
  final String content;
  final String? fileName;
  final ArticleStatus status;
  final bool allowComment;
  final bool submitting;
  final String? error;
  final VoidCallback onPublish;
  final VoidCallback onCancel;
  final ValueChanged<ArticleStatus> onStatusChanged;
  final ValueChanged<bool> onAllowCommentChanged;
  final VoidCallback? onDismissError;

  @override
  State<EditorPanel> createState() => _EditorPanelState();
}

class _EditorPanelState extends State<EditorPanel> {
  _EditorTab _tab = _EditorTab.edit;

  @override
  Widget build(BuildContext context) {
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.enter, control: true): () {
          if (!widget.submitting) widget.onPublish();
        },
      },
      child: Focus(
        autofocus: true,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= AppLayout.twoPaneBreakpoint;
            return Column(
              children: [
                Expanded(child: wide ? _buildWide() : _buildCompact()),
                _ActionBar(
                  submitting: widget.submitting,
                  onCancel: widget.onCancel,
                  onPublish: widget.onPublish,
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildWide() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          width: AppLayout.formPaneWidth,
          child: _FormBody(panel: widget),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: AppSpacing.xl),
          child: VerticalDivider(width: 1, thickness: 1),
        ),
        Expanded(child: PreviewPane(content: widget.content)),
      ],
    );
  }

  Widget _buildCompact() {
    return Column(
      children: [
        SegmentedButton<_EditorTab>(
          segments: const [
            ButtonSegment(value: _EditorTab.edit, label: Text('编辑')),
            ButtonSegment(value: _EditorTab.preview, label: Text('预览')),
          ],
          selected: {_tab},
          onSelectionChanged: (selection) =>
              setState(() => _tab = selection.first),
          expandedInsets: EdgeInsets.zero,
          showSelectedIcon: false,
        ),
        const SizedBox(height: AppSpacing.lg),
        Expanded(
          child: _tab == _EditorTab.edit
              ? _FormBody(panel: widget)
              : PreviewPane(content: widget.content),
        ),
      ],
    );
  }
}

class _FormBody extends StatelessWidget {
  const _FormBody({required this.panel});

  final EditorPanel panel;

  @override
  Widget build(BuildContext context) {
    final submitting = panel.submitting;
    final errorText = panel.error;

    return SingleChildScrollView(
      padding: const EdgeInsets.only(
        bottom: AppSpacing.lg,
        right: AppSpacing.xs,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: panel.titleController,
            enabled: !submitting,
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(labelText: '标题'),
          ),
          const SizedBox(height: AppSpacing.md + 2),
          TextField(
            controller: panel.summaryController,
            enabled: !submitting,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: '摘要',
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          SegmentedButton<ArticleStatus>(
            segments: const [
              ButtonSegment(value: ArticleStatus.published, label: Text('公开')),
              ButtonSegment(value: ArticleStatus.draft, label: Text('草稿')),
              ButtonSegment(value: ArticleStatus.private, label: Text('私密')),
            ],
            selected: {panel.status},
            onSelectionChanged: submitting
                ? null
                : (selection) => panel.onStatusChanged(selection.first),
            expandedInsets: EdgeInsets.zero,
            showSelectedIcon: false,
          ),
          const SizedBox(height: AppSpacing.xs),
          CheckboxListTile(
            value: panel.allowComment,
            onChanged: submitting
                ? null
                : (value) => panel.onAllowCommentChanged(value ?? true),
            contentPadding: EdgeInsets.zero,
            dense: true,
            controlAffinity: ListTileControlAffinity.leading,
            title: const Text(
              '允许评论',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ),
          if (errorText != null && errorText.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.md),
            ErrorBanner(message: errorText, onDismiss: panel.onDismissError),
          ],
        ],
      ),
    );
  }
}

class _ActionBar extends StatelessWidget {
  const _ActionBar({
    required this.submitting,
    required this.onCancel,
    required this.onPublish,
  });

  final bool submitting;
  final VoidCallback onCancel;
  final VoidCallback onPublish;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.lg),
      child: Row(
        children: [
          const Spacer(),
          OutlinedButton(
            onPressed: submitting ? null : onCancel,
            child: const Text('取消'),
          ),
          const SizedBox(width: AppSpacing.md),
          FilledButton(
            onPressed: submitting ? null : onPublish,
            child: submitting
                ? Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: scheme.onPrimary,
                        ),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      const Text('提交中…'),
                    ],
                  )
                : const Text('发布文章'),
          ),
        ],
      ),
    );
  }
}
