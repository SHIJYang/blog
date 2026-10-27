class ApiResult<T> {
  final int code;
  final String message;
  final T? data;

  const ApiResult({required this.code, required this.message, this.data});

  bool get isSuccess => code == 200;

  factory ApiResult.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic)? fromData,
  ) {
    return ApiResult(
      code: (json['code'] as num?)?.toInt() ?? 0,
      message: json['message']?.toString() ?? '',
      data: (fromData != null && json['data'] != null)
          ? fromData(json['data'])
          : json['data'] as T?,
    );
  }

  static ApiResult<T> failure<T>(String message, {int code = -1}) =>
      ApiResult<T>(code: code, message: message);
}
