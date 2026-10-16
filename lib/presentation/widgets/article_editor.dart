import 'package:flutter/material.dart';
import 'package:markdown_widget/markdown_widget.dart';

import '../../domain/entities/article.dart';
import '../../presentation/providers/blog_upload_provider.dart';

class ArticleEditor extends StatelessWidget {
  final BlogUploadProvider provider;

  const ArticleEditor({super.key, required this.provider});

  @override
  Widget build(BuildContext context) {
    final submitting = provider.isSubmitting;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Expanded(
              child: Text(
                '预览并发布',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
            ),
            TextButton(
              onPressed: submitting ? null : provider.resetDraft,
              child: const Text('重新选择'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          '文件：${provider.fileName ?? ''}',
          style: const TextStyle(fontSize: 12, color: Colors.grey),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: provider.titleController,
          enabled: !submitting,
          decoration: const InputDecoration(labelText: '标题 *'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: provider.summaryController,
          enabled: !submitting,
          maxLines: 2,
          decoration: const InputDecoration(labelText: '摘要'),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            const Text('状态：'),
            const SizedBox(width: 8),
            DropdownButton<ArticleStatus>(
              value: provider.status,
              onChanged: submitting
                  ? null
                  : (v) => v == null ? null : provider.setStatus(v),
              items: const [
                DropdownMenuItem(
                  value: ArticleStatus.published,
                  child: Text('公开'),
                ),
                DropdownMenuItem(value: ArticleStatus.draft, child: Text('草稿')),
                DropdownMenuItem(
                  value: ArticleStatus.private,
                  child: Text('私密'),
                ),
              ],
            ),
            const SizedBox(width: 16),
            Checkbox(
              value: provider.allowComment,
              onChanged: submitting
                  ? null
                  : (v) => provider.setAllowComment(v ?? true),
            ),
            const Text('允许评论'),
          ],
        ),
        const SizedBox(height: 16),
        const Text(
          'Markdown 预览',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        Container(
          height: 300,
          width: double.infinity,
          decoration: BoxDecoration(
            border: Border.all(color: Colors.grey.shade300),
            borderRadius: BorderRadius.circular(8),
          ),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(12),
            child: MarkdownWidget(data: provider.parsedFile?.content ?? ''),
          ),
        ),
        if (provider.error != null) ...[
          const SizedBox(height: 12),
          Text(provider.error!, style: const TextStyle(color: Colors.red)),
        ],
        const SizedBox(height: 16),
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            OutlinedButton(
              onPressed: submitting ? null : provider.resetDraft,
              child: const Text('取消'),
            ),
            const SizedBox(width: 12),
            FilledButton.icon(
              onPressed: submitting ? null : () => provider.publish(),
              icon: submitting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.send),
              label: Text(submitting ? '提交中...' : '发布文章'),
            ),
          ],
        ),
      ],
    );
  }
}
