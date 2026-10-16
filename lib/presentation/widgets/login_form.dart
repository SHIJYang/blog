import 'package:flutter/material.dart';

class LoginForm extends StatefulWidget {
  final bool loading;
  final String? error;
  final Future<void> Function(String username, String password) onSubmit;

  const LoginForm({
    super.key,
    required this.loading,
    required this.onSubmit,
    this.error,
  });

  @override
  State<LoginForm> createState() => _LoginFormState();
}

class _LoginFormState extends State<LoginForm> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _submit() {
    final u = _usernameController.text.trim();
    final p = _passwordController.text;
    if (u.isEmpty || p.isEmpty) return;
    widget.onSubmit(u, p);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text(
          '登录',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 4),
        const Text(
          '请使用作者账号登录后发布文章',
          style: TextStyle(fontSize: 12, color: Colors.grey),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _usernameController,
          enabled: !widget.loading,
          decoration: const InputDecoration(labelText: '用户名'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _passwordController,
          enabled: !widget.loading,
          obscureText: _obscure,
          onSubmitted: (_) => _submit(),
          decoration: InputDecoration(
            labelText: '密码',
            suffixIcon: IconButton(
              icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _obscure = !_obscure),
            ),
          ),
        ),
        if (widget.error != null) ...[
          const SizedBox(height: 12),
          Text(widget.error!, style: const TextStyle(color: Colors.red)),
        ],
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: widget.loading ? null : _submit,
            child: widget.loading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('登录'),
          ),
        ),
      ],
    );
  }
}
