class AuthSession {
  final String token;
  final String? username;
  final String? role;

  const AuthSession({required this.token, this.username, this.role});

  bool get isAuthorOrAdmin => role == 'AUTHOR' || role == 'ADMIN';
}
