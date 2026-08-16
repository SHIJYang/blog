package com.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP 客户端配置
 */
@Configuration
public class RestClientConfig {

    /**
     * 用于调用外部 API（如 Agnes Video）的 RestClient
     * 设置合理的连接/读取超时，避免外部接口挂起拖垮请求线程
     */
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
