package com.blog.config;

import com.blog.service.ai.AiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动期初始化任务。
 * <p>
 * 原先这段逻辑挂在 {@code AiProviderController#init()} 的 {@code @PostConstruct} 上，
 * 实测在空库首启会直接崩：{@code @PostConstruct} 在 Bean 初始化阶段执行，
 * 与 Flyway 的 {@code FlywayMigrationInitializer} 之间没有顺序保证，
 * 于是先跑了 {@code SELECT COUNT(*) FROM ai_provider} → {@code relation "ai_provider" does not exist}。
 * <p>
 * {@link ApplicationRunner} 在上下文 refresh 完成之后才执行，此时 Flyway 一定已经迁移完毕。
 * 另外这里刻意 catch 掉异常：初始化默认供应商只是「顺手补数据」，不该有权把整个应用拦在门外。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StartupTasksConfig {

    private final AiProviderService aiProviderService;

    @Bean
    public ApplicationRunner seedAiProviders() {
        return args -> {
            try {
                aiProviderService.ensureDefaults();
            } catch (Exception e) {
                log.warn("初始化默认 AI 供应商失败（不阻断启动）: {}", e.getMessage());
            }
        };
    }
}
