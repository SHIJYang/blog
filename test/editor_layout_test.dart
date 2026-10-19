import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:addtoblog/core/theme/app_theme.dart';
import 'package:addtoblog/domain/entities/article.dart';
import 'package:addtoblog/presentation/components/preview_pane.dart';
import 'package:addtoblog/presentation/panels/editor_panel.dart';

Widget buildEditor({
  required double width,
  double height = 700,
  bool submitting = false,
  ArticleStatus status = ArticleStatus.published,
  String content = '# 示例标题\n\n这是一段正文，用来验证预览区的排版与滚动。',
}) {
  return MaterialApp(
    theme: AppTheme.light,
    home: Scaffold(
      body: Center(
        child: SizedBox(
          width: width,
          height: height,
          child: EditorPanel(
            titleController: TextEditingController(text: '示例文章'),
            summaryController: TextEditingController(text: '一段摘要'),
            content: content,
            fileName: 'demo.md',
            status: status,
            allowComment: true,
            submitting: submitting,
            onStatusChanged: (_) {},
            onAllowCommentChanged: (_) {},
            onPublish: () {},
            onCancel: () {},
          ),
        ),
      ),
    ),
  );
}

void main() {
  /// MarkdownWidget 内部的 visibility_detector 会在 paint 后挂一个 500ms 定时器，
  /// 测试结束前先卸载组件再推进时钟，避免 “A Timer is still pending”。
  Future<void> unmountAndFlushTimers(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  }

  testWidgets('宽屏左右分栏：表单和预览同屏', (tester) async {
    addTearDown(tester.view.reset);
    tester.view.physicalSize = const Size(1000, 700);
    tester.view.devicePixelRatio = 1.0;

    await tester.pumpWidget(buildEditor(width: 1000, height: 700));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.byType(PreviewPane), findsOneWidget);
    // 宽屏不需要「编辑 / 预览」切换
    expect(find.text('编辑'), findsNothing);
    expect(find.text('发布文章'), findsOneWidget);

    await unmountAndFlushTimers(tester);
  });

  testWidgets('窄屏默认只显示表单，切到预览标签才显示预览', (tester) async {
    addTearDown(tester.view.reset);
    tester.view.physicalSize = const Size(600, 700);
    tester.view.devicePixelRatio = 1.0;

    await tester.pumpWidget(buildEditor(width: 600, height: 700));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.byType(PreviewPane), findsNothing);
    expect(find.text('编辑'), findsWidgets);

    await tester.tap(find.text('预览'));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.byType(PreviewPane), findsOneWidget);

    await unmountAndFlushTimers(tester);
  });

  testWidgets('极窄宽度下不溢出', (tester) async {
    addTearDown(tester.view.reset);
    tester.view.physicalSize = const Size(380, 640);
    tester.view.devicePixelRatio = 1.0;

    await tester.pumpWidget(buildEditor(width: 380, height: 640));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.text('发布文章'), findsOneWidget);
  });

  testWidgets('提交中按钮禁用并显示进度', (tester) async {
    addTearDown(tester.view.reset);
    tester.view.physicalSize = const Size(1000, 700);
    tester.view.devicePixelRatio = 1.0;

    await tester.pumpWidget(
      buildEditor(width: 1000, height: 700, submitting: true),
    );
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.text('提交中…'), findsOneWidget);

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '提交中…'),
    );
    expect(button.onPressed, isNull);

    await unmountAndFlushTimers(tester);
  });
}
