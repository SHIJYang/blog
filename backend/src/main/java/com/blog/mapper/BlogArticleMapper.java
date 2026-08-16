package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.BlogArticle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogArticleMapper extends BaseMapper<BlogArticle> {
}