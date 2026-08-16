package com.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.LinkSaveDto;
import com.blog.entity.BlogLink;

public interface BlogLinkService extends IService<BlogLink> {
    /**
     * 条件分页查询友情链接
     */
    Page<BlogLink> getLinkPage(Integer current, Integer size, String keyword);

    /**
     * 保存友情链接
     */
    void saveLink(LinkSaveDto dto);

    /**
     * 更新友情链接
     */
    void updateLink(LinkSaveDto dto);

    /**
     * 逻辑删除友情链接
     */
    void removeLink(Long id);
}