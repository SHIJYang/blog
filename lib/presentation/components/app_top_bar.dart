import 'package:flutter/material.dart';

/// 顶栏：左侧应用名，右侧账号区。扁平、无内边距，由页面统一控制留白。
class AppTopBar extends StatelessWidget {
  const AppTopBar({super.key, required this.title, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;

    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: text.titleSmall?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: 0.2,
            ),
          ),
        ),
        ?trailing,
      ],
    );
  }
}
