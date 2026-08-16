package com.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 记忆图片静态资源映射
 * 将 /uploads/memory/** 映射到本地磁盘 uploads/memory 目录
 */
@Configuration
public class MemoryWebConfig implements WebMvcConfigurer {

    @Value("${memory.upload-dir:uploads/memory}")
    private String uploadDir;

    @Value("${memory.access-prefix:/uploads/memory}")
    private String accessPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(accessPrefix + "/**")
                .addResourceLocations(location);
    }
}
