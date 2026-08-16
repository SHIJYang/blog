package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.common.JsoupUtils;
import com.blog.dto.ConfigSaveDto;
import com.blog.entity.BlogConfig;
import com.blog.mapper.BlogConfigMapper;
import com.blog.service.BlogConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlogConfigServiceImpl extends ServiceImpl<BlogConfigMapper, BlogConfig> implements BlogConfigService {

    @Override
    public List<BlogConfig> getAllConfig() {
        LambdaQueryWrapper<BlogConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogConfig::getIsDeleted, 0).orderByAsc(BlogConfig::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public String getConfigValue(String key) {
        BlogConfig config = this.getOne(new LambdaQueryWrapper<BlogConfig>()
                .eq(BlogConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public Page<BlogConfig> getConfigPage(Integer current, Integer size, String keyword) {
        Page<BlogConfig> page = new Page<>(current, size);
        LambdaQueryWrapper<BlogConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(BlogConfig::getConfigKey, keyword)
                    .or().like(BlogConfig::getConfigName, keyword);
        }
        wrapper.orderByDesc(BlogConfig::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(ConfigSaveDto dto) {
        // 检查 key 是否重复
        long count = this.count(new LambdaQueryWrapper<BlogConfig>()
                .eq(BlogConfig::getConfigKey, dto.getConfigKey()));
        if (count > 0) {
            throw new BusinessException(400, "配置键已存在");
        }

        BlogConfig config = new BlogConfig();
        config.setConfigKey(dto.getConfigKey());
        // *** 在 Service 层进行 HTML 清洗 ***
        config.setConfigValue(JsoupUtils.cleanHtml(dto.getConfigValue()));
        config.setConfigName(dto.getConfigName());
        config.setIsPublic(dto.getIsPublic());
        this.save(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(ConfigSaveDto dto) {
        BlogConfig existingConfig = this.getOne(new LambdaQueryWrapper<BlogConfig>()
                .eq(BlogConfig::getConfigKey, dto.getConfigKey()));
        if (existingConfig == null) {
            throw new BusinessException(404, "配置键不存在");
        }
        // 检查 key 是否重复 (排除自身)
        if (!existingConfig.getConfigKey().equals(dto.getConfigKey())) { // 理论上 configKey 是不能改的
            throw new BusinessException(400, "配置键不可修改");
        }

        existingConfig.setConfigName(dto.getConfigName());
        // *** 在 Service 层进行 HTML 清洗 ***
        existingConfig.setConfigValue(JsoupUtils.cleanHtml(dto.getConfigValue()));
        existingConfig.setIsPublic(dto.getIsPublic());
        this.updateById(existingConfig);
    }

    @Override
    public void removeConfig(Long id) {
        this.removeById(id);
    }
}