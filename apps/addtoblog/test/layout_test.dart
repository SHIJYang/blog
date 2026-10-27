import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:addtoblog/app.dart';
import 'package:addtoblog/app_dependencies.dart';
import 'package:addtoblog/data/local/auth_local_store.dart';
import 'package:addtoblog/presentation/panels/login_panel.dart';
import 'package:addtoblog/presentation/panels/upload_panel.dart';

const List<Size> _sizes = [
  Size(360, 640),
  Size(480, 820),
  Size(768, 1024),
  Size(1024, 720),
  Size(1280, 800),
  Size(1600, 1000),
];

Future<void> _pumpApp(
  WidgetTester tester,
  Size size,
  AuthLocalStore store,
) async {
  addTearDown(tester.view.reset);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1.0;

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

void main() {
  group('响应式布局不溢出', () {
    for (final size in _sizes) {
      final label = '${size.width.toInt()}x${size.height.toInt()}';

      testWidgets('登录页 $label', (tester) async {
        await _pumpApp(tester, size, MemoryAuthLocalStore());

        expect(tester.takeException(), isNull);
        expect(find.byType(LoginPanel), findsOneWidget);
        expect(find.text('保持登录'), findsOneWidget);
      });

      testWidgets('上传页 $label', (tester) async {
        final store = MemoryAuthLocalStore();
        await store.saveSession(
          const SavedAuthSession(token: 't', username: 'alice', role: 'AUTHOR'),
        );

        await _pumpApp(tester, size, store);

        expect(tester.takeException(), isNull);
        expect(find.byType(UploadPanel), findsOneWidget);
        expect(find.text('拖入 Markdown 文件'), findsOneWidget);
      });
    }
  });

  testWidgets('窗口从窄变宽再变窄不会出现布局异常', (tester) async {
    final store = MemoryAuthLocalStore();
    await store.saveSession(
      const SavedAuthSession(token: 't', username: 'alice', role: 'AUTHOR'),
    );

    addTearDown(tester.view.reset);
    tester.view.devicePixelRatio = 1.0;

    for (final size in const [
      Size(420, 760),
      Size(1440, 900),
      Size(420, 760),
    ]) {
      tester.view.physicalSize = size;
      if (!tester.binding.hasScheduledFrame) {
        await tester.pump();
      }
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull, reason: '尺寸 $size 下出现布局异常');
    }
  });

  testWidgets('深色模式下登录页与上传页都不出现布局异常', (tester) async {
    tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
    addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);

    await _pumpApp(tester, const Size(1200, 820), MemoryAuthLocalStore());
    expect(tester.takeException(), isNull);
    expect(find.byType(LoginPanel), findsOneWidget);
    expect(find.text('保持登录'), findsOneWidget);

    final store = MemoryAuthLocalStore();
    await store.saveSession(
      const SavedAuthSession(token: 't', username: 'alice', role: 'AUTHOR'),
    );
    // 先卸载旧页面，确保新会话会被重新读取
    await tester.pumpWidget(const SizedBox.shrink());
    await _pumpApp(tester, const Size(1200, 820), store);
    expect(tester.takeException(), isNull);
    expect(find.byType(UploadPanel), findsOneWidget);
  });
}
