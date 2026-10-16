import 'dart:typed_data';

import 'package:desktop_drop/desktop_drop.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../core/constants/file_constants.dart';
import '../../core/utils/file_parser.dart';
import '../../data/repositories/article_repository.dart';
import '../../data/repositories/auth_repository.dart';
import '../../domain/entities/article.dart';

enum UploadStage { login, drop, edit, submitting, done }

class BlogUploadProvider extends ChangeNotifier {
  final AuthRepository _authRepository;
  final ArticleRepository _articleRepository;

  BlogUploadProvider({
    required AuthRepository authRepository,
    required ArticleRepository articleRepository,
  }) : _authRepository = authRepository,
       _articleRepository = articleRepository;

  // ---------- 状态 ----------
  UploadStage _stage = UploadStage.login;
  bool _isDragging = false;
  bool _isLoading = false;
  String? _error;

  ParsedFile? _parsedFile;
  String? _fileName;

  ArticleStatus _status = ArticleStatus.published;
  bool _allowComment = true;

  final TextEditingController titleController = TextEditingController();
  final TextEditingController summaryController = TextEditingController();

  // ---------- Getter ----------
  UploadStage get stage => _stage;
  bool get isDragging => _isDragging;
  bool get isLoading => _isLoading;
  String? get error => _error;
  ParsedFile? get parsedFile => _parsedFile;
  String? get fileName => _fileName;
  ArticleStatus get status => _status;
  bool get allowComment => _allowComment;
  bool get isSubmitting => _stage == UploadStage.submitting;

  // ---------- 登录 ----------
  Future<void> login(String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    final result = await _authRepository.login(username, password);

    _isLoading = false;
    if (result.isSuccess) {
      _stage = UploadStage.drop;
    } else {
      _error = result.message.isEmpty ? '登录失败' : result.message;
    }
    notifyListeners();
  }

  void logout() {
    _authRepository.logout();
    _stage = UploadStage.login;
    _error = null;
    _resetDraft(notify: false);
    notifyListeners();
  }

  // ---------- 拖拽 ----------
  void onDragEntered() {
    _isDragging = true;
    notifyListeners();
  }

  void onDragExited() {
    _isDragging = false;
    notifyListeners();
  }

  Future<void> onFilesDropped(List<DropItem> files) async {
    _isDragging = false;
    if (files.isEmpty) {
      notifyListeners();
      return;
    }
    final item = files.first;
    try {
      final bytes = await item.readAsBytes();
      await _processFile(item.name, bytes);
    } catch (e) {
      _setError('读取文件失败：$e');
      return;
    }
  }

  // ---------- 点击选择（file_picker 13.x API） ----------
  Future<void> pickFile() async {
    try {
      final List<PlatformFile> result = await FilePicker.pickFiles(
        type: FileType.custom,
        allowedExtensions: FileConstants.supportedExtensions,
      );

      if (result.isEmpty) {
        return;
      }

      final file = result.first;

      final Uint8List bytes = await file.readAsBytes();
      await _processFile(file.name, bytes);
    } catch (e) {
      _setError('选择文件失败：$e');
      return;
    }
  }

  Future<void> _processFile(String name, Uint8List bytes) async {
    if (!FileParser.isSupported(name)) {
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
      _stage = UploadStage.edit;
      notifyListeners();
    } catch (e) {
      _setError('解析文件失败：$e');
      return;
    }
  }

  // ---------- 编辑 ----------
  void setStatus(ArticleStatus status) {
    _status = status;
    notifyListeners();
  }

  void setAllowComment(bool value) {
    _allowComment = value;
    notifyListeners();
  }

  // ---------- 发布 ----------
  Future<bool> publish() async {
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
    notifyListeners();

    final summaryText = summaryController.text.trim();
    final article = Article(
      title: title,
      content: _parsedFile!.content,
      summary: summaryText.isEmpty ? null : summaryText,
      status: _status,
      allowComment: _allowComment,
    );

    final result = await _articleRepository.save(article);

    if (result.isSuccess) {
      _stage = UploadStage.done;
      notifyListeners();
      return true;
    } else {
      _stage = UploadStage.edit;
      _error = result.message.isEmpty ? '发布失败' : result.message;
      notifyListeners();
      return false;
    }
  }

  // ---------- 重置 ----------
  void resetDraft() {
    _resetDraft();
    notifyListeners();
  }

  void _resetDraft({bool notify = false}) {
    _parsedFile = null;
    _fileName = null;
    titleController.clear();
    summaryController.clear();
    _status = ArticleStatus.published;
    _allowComment = true;
    _error = null;
    if (_stage != UploadStage.login && _stage != UploadStage.done) {
      _stage = UploadStage.drop;
    }
    if (notify) notifyListeners();
  }

  void _setError(String message) {
    _error = message;
    notifyListeners();
  }

  @override
  void dispose() {
    titleController.dispose();
    summaryController.dispose();
    super.dispose();
  }
}
