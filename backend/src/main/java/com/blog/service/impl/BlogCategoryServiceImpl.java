package com.blog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.entity.BlogCategory;
import com.blog.mapper.BlogCategoryMapper;
import com.blog.service.BlogCategoryService;
import org.springframework.stereotype.Service;

@Service
public class BlogCategoryServiceImpl extends ServiceImpl<BlogCategoryMapper, BlogCategory> implements BlogCategoryService {
}