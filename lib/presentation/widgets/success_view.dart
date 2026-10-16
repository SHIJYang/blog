import 'package:flutter/material.dart';

class SuccessView extends StatelessWidget {
  final VoidCallback onAgain;
  const SuccessView({super.key, required this.onAgain});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
        const SizedBox(height: 16),
        const Text(
          '文章保存成功',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 24),
        FilledButton(onPressed: onAgain, child: const Text('再发一篇')),
      ],
    );
  }
}
