import 'dart:typed_data';

import 'package:desktop_drop/desktop_drop.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../core/constants/file_constants.dart';
import '../../core/utils/file_parser.dart';
import '../../data/repositories/article_repository.dart';
import '../../data/repositories/auth_repository.dart';
import '../../domain/entities/article.dart';

enum UploadStage {
  /// 启动时读取本地登录信息
  restoring,
  login,
  drop,
  edit,
  submitting,
  done,
}

/// 页面状态机：登录 → 选择文件 → 编辑发布 → 完成。
class BlogUploadProvider extends ChangeNotifier {
  final AuthRepository authRepository;
  final ArticleRepository articleRepository;

  BlogUploadProvider({
    required this.authRepository,
    required this.articleRepository,
    String? initialUsername,
  }) : _lastUsername = initialUsername;

  UploadStage _stage = UploadStage.restoring;
  bool _isLoading = false;
  bool _rememberMe = true;
  bool _disposed = false;
  String? _error;
  String? _lastUsername;
  String? _username;
  String? _publishedTitle;

  ParsedFile? _parsedFile;
  String? _fileName;

  ArticleStatus _status = ArticleStatus.published;
  bool _allowComment = true;

  final TextEditingController titleController = TextEditingController();
  final TextEditingController summaryController = TextEditingController();

  // ---------- Getter ----------
  UploadStage get stage => _stage;
  bool get isLoading => _isLoading;
  bool get isSubmitting => _stage == UploadStage.submitting;
  bool get rememberMe => _rememberMe;
  String? get error => _error;

  /// 最近一次登录用的用户名（退出后仍保留，用于登录页预填）。
  String? get lastUsername => _lastUsername;

  /// 当前登录用户名（来自会话）。
  String? get username => _username;

  ParsedFile? get parsedFile => _parsedFile;
  String? get fileName => _fileName;
  String? get publishedTitle => _publishedTitle;
  ArticleStatus get status => _status;
  bool get allowComment => _allowComment;

  // ---------- 初始化 ----------
  /// 启动时恢复本地登录状态：有 token 直接进入上传，否则回登录页。
  Future<void> initialize() async {
    _stage = UploadStage.restoring;
    _notify();

    final session = await authRepository.restore();
    _username = session?.username ?? _lastUsername;
    _stage = session == null ? UploadStage.login : UploadStage.drop;
    _notify();
  }

  // ---------- 登录 ----------
  Future<void> login(String username, String password) async {
    if (_isLoading) return;

    _isLoading = true;
    _error = null;
    _notify();

    final result = await authRepository.login(
      username,
      password,
      remember: _rememberMe,
    );

    _isLoading = false;
    if (result.isSuccess) {
      _username = result.data?.username ?? username;
      _lastUsername = _username;
      _stage = UploadStage.drop;
    } else {
      _error = result.message.isEmpty ? '登录失败' : result.message;
    }
    _notify();
  }

  void setRememberMe(bool value) {
    if (_rememberMe == value) return;
    _rememberMe = value;
    _notify();
  }

  Future<void> logout() async {
    _username = null;
    _publishedTitle = null;
    await authRepository.logout();
    _clearDraft();
    _stage = UploadStage.login;
    _notify();
  }

  void clearError() {
    if (_error == null) return;
    _error = null;
    _notify();
  }

  // ---------- 拖入文件 ----------
  Future<void> onFilesDropped(List<DropItem> files) async {
    if (files.isEmpty) return;
    final item = files.first;
    try {
      final bytes = await item.readAsBytes();
      await _processFile(item.name, bytes);
    } catch (e) {
      _setError('读取文件失败：$e');
    }
  }

  // ---------- 点击选择 ----------
  Future<void> pickFile() async {
    if (_isLoading) return;
    try {
      final file = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: FileConstants.supportedExtensions,
        dialogTitle: '选择 Markdown 文件',
      );
      if (file == null) return;

      _isLoading = true;
      _error = null;
      _notify();

      final bytes = await file.readAsBytes();
      await _processFile(file.name, bytes);
    } catch (e) {
      _isLoading = false;
      _setError('选择文件失败：$e');
    }
  }

  Future<void> _processFile(String name, Uint8List bytes) async {
    if (!FileParser.isSupported(name)) {
      _isLoading = false;
      _setError(
        '仅支持 ${FileConstants.supportedExtensions.map((e) => '.$e').join(' / ')} 文件',
      );
      return;
    }

    try {
      final parsed = await FileParser.parse(name, bytes);
      _parsedFile = parsed;
      _fileName = name;
      titleController.text = parsed.title;
      summaryController.text = parsed.summary ?? '';
      _status = ArticleStatus.published;
      _allowComment = true;
      _error = null;
      _isLoading = false;
      _stage = UploadStage.edit;
      _notify();
    } catch (e) {
      _isLoading = false;
      _setError('解析文件失败：$e');
    }
  }

  // ---------- 编辑 ----------
  void setStatus(ArticleStatus status) {
    if (_status == status) return;
    _status = status;
    _notify();
  }

  void setAllowComment(bool value) {
    if (_allowComment == value) return;
    _allowComment = value;
    _notify();
  }

  // ---------- 发布 ----------
  Future<bool> publish() async {
    if (isSubmitting) return false;

    final title = titleController.text.trim();
    if (title.isEmpty) {
      _setError('标题不能为空');
      return false;
    }
    if (_parsedFile == null) {
      _setError('尚未解析文件');
      return false;
    }

    _stage = UploadStage.submitting;
    _error = null;
    _notify();

    final summaryText = summaryController.text.trim();
    final article = Article(
      title: title,
      content: _parsedFile!.content,
      summary: summaryText.isEmpty ? null : summaryText,
      status: _status,
      allowComment: _allowComment,
    );

    final result = await articleRepository.save(article);

    if (result.isSuccess) {
      _publishedTitle = title;
      _stage = UploadStage.done;
      _notify();
      return true;
    }

    _stage = UploadStage.edit;
    _error = result.message.isEmpty ? '发布失败' : result.message;
    _notify();
    return false;
  }

  // ---------- 重置 ----------
  /// 放弃当前草稿，回到「选择文件」。
  void resetDraft() {
    _clearDraft();
    _stage = UploadStage.drop;
    _notify();
  }

  /// 发布完成后「再发一篇」。
  void startOver() {
    _publishedTitle = null;
    _clearDraft();
    _stage = UploadStage.drop;
    _notify();
  }

  void _clearDraft() {
    _parsedFile = null;
    _fileName = null;
    titleController.clear();
    summaryController.clear();
    _status = ArticleStatus.published;
    _allowComment = true;
    _error = null;
  }

  void _setError(String message) {
    _error = message;
    _isLoading = false;
    _notify();
  }

  void _notify() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    titleController.dispose();
    summaryController.dispose();
    super.dispose();
  }
}
