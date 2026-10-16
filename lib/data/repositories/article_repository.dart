import '../../domain/entities/article.dart';
import '../models/api_result.dart';
import '../models/article_save_request.dart';
import '../services/article_api_service.dart';

class ArticleRepository {
  final ArticleApiService _service;
  ArticleRepository(this._service);

  Future<ApiResult<void>> save(Article article) {
    final request = ArticleSaveRequest.fromEntity(article);
    return _service.save(request);
  }
}
