package com.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.entity.BlogTag;
import com.blog.mapper.BlogTagMapper;
import com.blog.service.BlogTagService;
import org.springframework.stereotype.Service;

@Service
public class BlogTagServiceImpl extends ServiceImpl<BlogTagMapper, BlogTag> implements BlogTagService {
}