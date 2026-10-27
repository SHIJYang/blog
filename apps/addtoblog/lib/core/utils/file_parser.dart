import 'dart:convert';
import 'dart:typed_data';

import '../constants/file_constants.dart';

class ParsedFile {
  final String title;
  final String content;
  final String? summary;

  const ParsedFile({required this.title, required this.content, this.summary});
}

class FileParser {
  FileParser._();

  static bool isSupported(String fileName) {
    final dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length - 1) return false;
    final ext = fileName.substring(dot + 1).toLowerCase();
    return FileConstants.supportedExtensions.contains(ext);
  }

  static Future<ParsedFile> parse(String fileName, Uint8List bytes) async {
    if (!isSupported(fileName)) {
      throw UnsupportedError(
        '仅支持 ${FileConstants.supportedExtensions.map((e) => '.$e').join(' / ')} 文件',
      );
    }
    if (bytes.length > FileConstants.maxFileSizeBytes) {
      throw StateError('文件大小超过限制（最大 10 MB）');
    }

    String content;
    try {
      content = utf8.decode(bytes);
    } catch (_) {
      content = utf8.decode(bytes, allowMalformed: true);
    }

    return ParsedFile(
      title: _extractTitle(fileName, content),
      content: content,
      summary: _extractSummary(content),
    );
  }

  static String _extractTitle(String fileName, String content) {
    for (final line in const LineSplitter().convert(content)) {
      final t = line.trim();
      if (t.startsWith('# ')) return t.substring(2).trim();
      if (t.isNotEmpty) break;
    }
    return fileName.replaceAll(RegExp(r'\.[^.]+$'), '');
  }

  static String? _extractSummary(String content) {
    final text = content
        .replaceAll(RegExp(r'^#+\s+', multiLine: true), '')
        .replaceAll(RegExp(r'!\[.*?\]\(.*?\)'), '')
        .replaceAll(RegExp(r'\[(.*?)\]\(.*?\)'), r'$1')
        .replaceAll(RegExp(r'```[\s\S]*?```'), '')
        .replaceAll(RegExp(r'[*_`>~]'), '')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    if (text.isEmpty) return null;
    return text.length > FileConstants.summaryLength
        ? '${text.substring(0, FileConstants.summaryLength)}...'
        : text;
  }
}
