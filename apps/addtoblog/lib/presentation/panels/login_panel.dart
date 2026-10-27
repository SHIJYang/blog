import 'package:flutter/material.dart';

import '../../core/theme/app_tokens.dart';
import '../components/error_banner.dart';

/// 登录面板：两个输入框 + 保持登录开关 + 按钮，仅此而已。
class LoginPanel extends StatefulWidget {
  const LoginPanel({
    super.key,
    required this.busy,
    required this.onSubmit,
    this.error,
    this.initialUsername,
    this.rememberMe = true,
    this.onRememberChanged,
  });

  final bool busy;
  final String? error;
  final String? initialUsername;
  final bool rememberMe;
  final ValueChanged<bool>? onRememberChanged;
  final void Function(String username, String password) onSubmit;

  @override
  State<LoginPanel> createState() => _LoginPanelState();
}

class _LoginPanelState extends State<LoginPanel> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _passwordFocus = FocusNode();
  bool _obscure = true;
  String? _localError;

  @override
  void initState() {
    super.initState();
    _usernameController.text = widget.initialUsername ?? '';
  }

  @override
  void didUpdateWidget(covariant LoginPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 恢复本地登录信息后才拿到上次的用户名，此时补填到输入框。
    if (_usernameController.text.isEmpty &&
        widget.initialUsername != null &&
        widget.initialUsername!.isNotEmpty) {
      _usernameController.text = widget.initialUsername!;
    }
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    _passwordFocus.dispose();
    super.dispose();
  }

  void _submit() {
    final username = _usernameController.text.trim();
    final password = _passwordController.text;

    if (username.isEmpty) {
      setState(() => _localError = '请输入用户名');
      return;
    }
    if (password.isEmpty) {
      setState(() => _localError = '请输入密码');
      return;
    }

    setState(() => _localError = null);
    widget.onSubmit(username, password);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final errorText = _localError ?? widget.error;

    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _usernameController,
              enabled: !widget.busy,
              autofocus: true,
              textInputAction: TextInputAction.next,
              onSubmitted: (_) => _passwordFocus.requestFocus(),
              decoration: const InputDecoration(labelText: '用户名'),
            ),
            const SizedBox(height: AppSpacing.md + 2),
            TextField(
              controller: _passwordController,
              focusNode: _passwordFocus,
              enabled: !widget.busy,
              obscureText: _obscure,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _submit(),
              decoration: InputDecoration(
                labelText: '密码',
                suffixIcon: IconButton(
                  tooltip: _obscure ? '显示密码' : '隐藏密码',
                  icon: Icon(
                    _obscure
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    size: 19,
                  ),
                  onPressed: () => setState(() => _obscure = !_obscure),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            SwitchListTile.adaptive(
              value: widget.rememberMe,
              onChanged: widget.busy ? null : widget.onRememberChanged,
              contentPadding: EdgeInsets.zero,
              dense: true,
              visualDensity: VisualDensity.compact,
              title: const Text(
                '保持登录',
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
              ),
            ),
            if (errorText != null && errorText.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              ErrorBanner(message: errorText),
            ],
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              onPressed: widget.busy ? null : _submit,
              child: widget.busy
                  ? SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: scheme.onPrimary,
                      ),
                    )
                  : const Text('登录'),
            ),
          ],
        ),
      ),
    );
  }
}
