import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/api_result.dart';

class ApiClient {
  final String baseUrl;
  String? _token;

  ApiClient({required this.baseUrl});

  String? get token => _token;
  bool get hasToken => _token != null && _token!.isNotEmpty;

  void setToken(String? token) => _token = token;
  void clearToken() => _token = null;

  Map<String, String> _headers() => {
    'Content-Type': 'application/json; charset=utf-8',
    if (hasToken) 'Authorization': 'Bearer $_token',
  };

  Future<ApiResult<T>> post<T>(
    String path,
    Map<String, dynamic> body, [
    T Function(dynamic)? fromData,
  ]) async {
    try {
      final resp = await http.post(
        Uri.parse('$baseUrl$path'),
        headers: _headers(),
        body: jsonEncode(body),
      );
      return _decode<T>(resp, fromData);
    } catch (e) {
      return ApiResult.failure('网络请求失败：$e');
    }
  }

  ApiResult<T> _decode<T>(http.Response resp, T Function(dynamic)? fromData) {
    final raw = utf8.decode(resp.bodyBytes, allowMalformed: true);
    if (raw.isEmpty) {
      return ApiResult(
        code: resp.statusCode,
        message: resp.statusCode == 200 ? '操作成功' : 'HTTP ${resp.statusCode}',
      );
    }
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) {
        return ApiResult.failure('响应格式异常', code: resp.statusCode);
      }
      return ApiResult.fromJson(decoded, fromData);
    } catch (e) {
      return ApiResult.failure('解析响应失败：$e', code: resp.statusCode);
    }
  }
}
