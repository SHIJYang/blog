package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.dto.LinkSaveDto;
import com.blog.entity.BlogLink;
import com.blog.mapper.BlogLinkMapper;
import com.blog.service.BlogLinkService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlogLinkServiceImpl extends ServiceImpl<BlogLinkMapper, BlogLink> implements BlogLinkService {

    @Override
    public Page<BlogLink> getLinkPage(Integer current, Integer size, String keyword) {
        Page<BlogLink> page = new Page<>(current, size);
        LambdaQueryWrapper<BlogLink> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(BlogLink::getName, keyword)
                    .or().like(BlogLink::getUrl, keyword);
        }
        wrapper.orderByAsc(BlogLink::getSort).orderByDesc(BlogLink::getCreateTime); // 优先按排序值，再按创建时间
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLink(LinkSaveDto dto) {
        // 检查名称是否重复
        long count = this.count(new LambdaQueryWrapper<BlogLink>()
                .eq(BlogLink::getName, dto.getName()));
        if (count > 0) {
            throw new BusinessException(400, "友情链接名称已存在");
        }

        BlogLink link = new BlogLink();
        link.setName(dto.getName());
        link.setUrl(dto.getUrl());
        link.setLogo(dto.getLogo());
        link.setDescription(dto.getDescription());
        link.setSort(dto.getSort());
        link.setStatus(dto.getStatus());

        this.save(link);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLink(LinkSaveDto dto) {
        BlogLink existingLink = this.getById(dto.getId());
        if (existingLink == null) {
            throw new BusinessException(404, "友情链接不存在");
        }
        // 检查名称是否重复 (排除自身)
        long count = this.count(new LambdaQueryWrapper<BlogLink>()
                .eq(BlogLink::getName, dto.getName())
                .ne(BlogLink::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException(400, "友情链接名称已存在");
        }

        existingLink.setName(dto.getName());
        existingLink.setUrl(dto.getUrl());
        existingLink.setLogo(dto.getLogo());
        existingLink.setDescription(dto.getDescription());
        existingLink.setSort(dto.getSort());
        existingLink.setStatus(dto.getStatus());

        this.updateById(existingLink);
    }

    @Override
    public void removeLink(Long id) {
        this.removeById(id);
    }
}