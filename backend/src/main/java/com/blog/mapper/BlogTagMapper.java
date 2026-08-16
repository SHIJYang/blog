package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.BlogTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogTagMapper extends BaseMapper<BlogTag> {
}