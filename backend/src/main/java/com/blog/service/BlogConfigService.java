package com.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.common.JsoupUtils; // 引入 Jsoup 工具类
import com.blog.dto.ConfigSaveDto;
import com.blog.entity.BlogConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BlogConfigService extends IService<BlogConfig> {
    /**
     * 获取所有系统配置 (用于缓存或前端展示)
     */
    List<BlogConfig> getAllConfig();

    /**
     * 根据 key 获取配置值
     */
    String getConfigValue(String key);

    /**
     * 条件分页查询系统配置
     */
    Page<BlogConfig> getConfigPage(Integer current, Integer size, String keyword);

    /**
     * 保存系统配置 (key, value, name)
     */
    void saveConfig(ConfigSaveDto dto);

    /**
     * 更新系统配置 (key, value, name)
     */
    void updateConfig(ConfigSaveDto dto);

    /**
     * 逻辑删除配置
     */
    void removeConfig(Long id);
}
