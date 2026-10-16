import '../../domain/entities/article.dart';

/// 与后端 ArticleSaveDto 一一对应
class ArticleSaveRequest {
  final int? id;
  final String title;
  final String content;
  final String? summary;
  final String? coverImage;
  final int status;
  final int allowComment;
  final List<int> categoryIds;
  final List<int> tagIds;

  const ArticleSaveRequest({
    this.id,
    required this.title,
    required this.content,
    this.summary,
    this.coverImage,
    required this.status,
    required this.allowComment,
    required this.categoryIds,
    required this.tagIds,
  });

  factory ArticleSaveRequest.fromEntity(Article article) {
    return ArticleSaveRequest(
      id: article.id,
      title: article.title,
      content: article.content,
      summary: article.summary,
      coverImage: article.coverImage,
      status: article.status.code,
      allowComment: article.allowComment ? 1 : 0,
      categoryIds: article.categoryIds,
      tagIds: article.tagIds,
    );
  }

  Map<String, dynamic> toJson() => {
    if (id != null) 'id': id,
    'title': title,
    'content': content,
    if (summary != null && summary!.isNotEmpty) 'summary': summary,
    if (coverImage != null && coverImage!.isNotEmpty) 'coverImage': coverImage,
    'status': status,
    'allowComment': allowComment,
    'categoryIds': categoryIds,
    'tagIds': tagIds,
  };
}
