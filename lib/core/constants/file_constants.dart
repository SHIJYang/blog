class FileConstants {
  FileConstants._();

  /// 允许上传的文件扩展名（小写，不带点）
  static const List<String> supportedExtensions = ['md', 'markdown', 'txt'];

  /// 单文件最大体积：10 MB
  static const int maxFileSizeBytes = 10 * 1024 * 1024;

  /// 摘要自动截取长度
  static const int summaryLength = 200;
}
