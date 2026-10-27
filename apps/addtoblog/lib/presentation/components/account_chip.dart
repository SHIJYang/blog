import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';

/// 账号区：用户名 + 退出登录。纯文字，不带头像与图标。
class AccountChip extends StatelessWidget {
  const AccountChip({super.key, this.username, this.role, this.onLogout});

  final String? username;
  final String? role;
  final VoidCallback? onLogout;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final name = username?.trim();

    if (name == null || name.isEmpty) {
      return Text(
        '未登录',
        style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
      );
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          name,
          style: text.bodyMedium?.copyWith(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: scheme.onSurfaceVariant,
          ),
        ),
        if (onLogout != null) ...[
          const SizedBox(width: AppSpacing.xs),
          TextButton(
            onPressed: onLogout,
            style: TextButton.styleFrom(
              minimumSize: const Size(0, 32),
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
            ),
            child: const Text('退出登录'),
          ),
        ],
      ],
    );
  }
}
