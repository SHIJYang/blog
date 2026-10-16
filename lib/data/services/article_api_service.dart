import '../models/api_result.dart';
import '../models/article_save_request.dart';
import 'api_client.dart';

class ArticleApiService {
  final ApiClient _client;
  ArticleApiService(this._client);

  /// 调用 POST /api/admin/articles
  Future<ApiResult<void>> save(ArticleSaveRequest request) {
    return _client.post<void>('/api/admin/articles', request.toJson());
  }
}
