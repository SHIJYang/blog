import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:addtoblog/app.dart';
import 'package:addtoblog/app_dependencies.dart';
import 'package:addtoblog/data/local/auth_local_store.dart';
import 'package:addtoblog/presentation/panels/editor_panel.dart';
import 'package:addtoblog/presentation/panels/upload_panel.dart';

/// 直接向 desktop_drop 通道投递原生事件，验证拖放链路：
/// 拖动进入 → 高亮；松手 → 读取文件 → 进入编辑页。
Future<void> sendDragEvent(String method, [Object? arguments]) async {
  final message = const StandardMethodCodec().encodeMethodCall(
    MethodCall(method, arguments),
  );
  await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .handlePlatformMessage('desktop_drop', message, (_) {});
}

void main() {
  late Directory tempDir;
  late String markdownPath;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('addtoblog_drop_test');
    markdownPath = '${tempDir.path}${Platform.pathSeparator}drag.md';
    File(markdownPath).writeAsStringSync('# 拖入的标题\n\n拖入的正文内容。');
  });

  tearDown(() {
    try {
      if (tempDir.existsSync()) {
        tempDir.deleteSync(recursive: true);
      }
    } on FileSystemException {
      // Windows 上偶发句柄未释放，忽略清理失败即可。
    }
  });

  Future<void> pumpSignedInApp(WidgetTester tester) async {
    final store = MemoryAuthLocalStore();
    await store.saveSession(
      const SavedAuthSession(token: 't', username: 'alice', role: 'AUTHOR'),
    );
    await tester.pumpWidget(
      App(
        dependencies: AppDependencies.production(
          baseUrl: 'https://example.test',
          authStore: store,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('拖入文件后进入编辑页并解析出标题', (tester) async {
    await pumpSignedInApp(tester);
    expect(find.byType(UploadPanel), findsOneWidget);
    expect(find.text('拖入 Markdown 文件'), findsOneWidget);

    await sendDragEvent('entered', <double>[600, 400]);
    await tester.pump();
    expect(find.text('松手导入'), findsOneWidget);

    await tester.runAsync(() async {
      await sendDragEvent('performOperation', <String>[markdownPath]);
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    await tester.pumpAndSettle();

    expect(find.byType(EditorPanel), findsOneWidget);
    expect(find.text('拖入的标题'), findsOneWidget);
  });

  testWidgets('拖出后取消高亮', (tester) async {
    await pumpSignedInApp(tester);

    await sendDragEvent('entered', <double>[600, 400]);
    await tester.pump();
    expect(find.text('松手导入'), findsOneWidget);

    await sendDragEvent('exited');
    await tester.pump();
    expect(find.text('拖入 Markdown 文件'), findsOneWidget);
  });

  testWidgets('不支持的文件类型给出提示而不是跳转', (tester) async {
    await pumpSignedInApp(tester);

    final otherPath = '${tempDir.path}${Platform.pathSeparator}image.png';
    File(otherPath).writeAsBytesSync([1, 2, 3]);

    await sendDragEvent('entered', <double>[600, 400]);
    await tester.pump();
    await tester.runAsync(() async {
      await sendDragEvent('performOperation', <String>[otherPath]);
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    await tester.pumpAndSettle();

    expect(find.byType(UploadPanel), findsOneWidget);
    expect(find.byType(EditorPanel), findsNothing);
    expect(find.textContaining('仅支持'), findsOneWidget);
  });
}
