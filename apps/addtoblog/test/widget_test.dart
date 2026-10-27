import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:addtoblog/app.dart';
import 'package:addtoblog/app_dependencies.dart';
import 'package:addtoblog/data/local/auth_local_store.dart';
import 'package:addtoblog/presentation/panels/login_panel.dart';
import 'package:addtoblog/presentation/panels/upload_panel.dart';

App buildApp(AuthLocalStore store) => App(
  dependencies: AppDependencies.production(
    baseUrl: 'https://example.test',
    authStore: store,
  ),
);

void main() {
  testWidgets('本机没有登录信息时，进入登录面板', (tester) async {
    await tester.pumpWidget(buildApp(MemoryAuthLocalStore()));
    await tester.pumpAndSettle();

    expect(find.byType(LoginPanel), findsOneWidget);
    expect(find.text('保持登录'), findsOneWidget);
  });

  testWidgets('本机有登录信息时，自动跳过登录进入上传面板', (tester) async {
    final store = MemoryAuthLocalStore();
    await store.saveSession(
      const SavedAuthSession(
        token: 'saved-token',
        username: 'alice',
        role: 'AUTHOR',
      ),
    );

    await tester.pumpWidget(buildApp(store));
    await tester.pumpAndSettle();

    expect(find.byType(UploadPanel), findsOneWidget);
    expect(find.byType(LoginPanel), findsNothing);
    expect(find.text('alice'), findsOneWidget);
  });

  testWidgets('「保持登录」开关可以切换', (tester) async {
    await tester.pumpWidget(buildApp(MemoryAuthLocalStore()));
    await tester.pumpAndSettle();

    final switchFinder = find.byType(Switch);
    expect(tester.widget<Switch>(switchFinder).value, isTrue);

    await tester.tap(find.text('保持登录'));
    await tester.pumpAndSettle();

    expect(tester.widget<Switch>(switchFinder).value, isFalse);
  });
}
