package com.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.CommentSaveDto;
import com.blog.entity.BlogComment;
import com.blog.vo.CommentVo;

public interface BlogCommentService extends IService<BlogComment> {
    /**
     * 发表评论（支持登录用户提取会话信息、游客自行填报信息）
     */
    void saveComment(CommentSaveDto dto, String ipAddress);

    /**
     * 获取指定文章的分页评论（通过批量查询并归并为两级盖楼平铺格式）
     */
    Page<CommentVo> getCommentPageByArticleId(Long articleId, Integer current, Integer size);
}