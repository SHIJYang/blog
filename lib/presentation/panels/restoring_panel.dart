import 'package:flutter/material.dart';

/// 启动时读取本地登录信息的过渡态：只留一个进度圈。
class RestoringPanel extends StatelessWidget {
  const RestoringPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(strokeWidth: 2.2),
      ),
    );
  }
}
