enum ArticleStatus {
  draft(0),
  published(1),
  private(2);

  final int code;
  const ArticleStatus(this.code);

  static ArticleStatus fromCode(int code) => ArticleStatus.values.firstWhere(
    (e) => e.code == code,
    orElse: () => ArticleStatus.published,
  );
}

class Article {
  final int? id;
  final String title;
  final String content;
  final String? summary;
  final String? coverImage;
  final ArticleStatus status;
  final bool allowComment;
  final List<int> categoryIds;
  final List<int> tagIds;

  const Article({
    this.id,
    required this.title,
    required this.content,
    this.summary,
    this.coverImage,
    this.status = ArticleStatus.published,
    this.allowComment = true,
    this.categoryIds = const [],
    this.tagIds = const [],
  });

  Article copyWith({
    String? title,
    String? content,
    String? summary,
    ArticleStatus? status,
    bool? allowComment,
  }) {
    return Article(
      id: id,
      title: title ?? this.title,
      content: content ?? this.content,
      summary: summary ?? this.summary,
      coverImage: coverImage,
      status: status ?? this.status,
      allowComment: allowComment ?? this.allowComment,
      categoryIds: categoryIds,
      tagIds: tagIds,
    );
  }
}
