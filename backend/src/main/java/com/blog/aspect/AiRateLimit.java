package com.blog.aspect;

import java.lang.annotation.*;

/**
 * AI 接口限流注解
 * 标注在需要限流的 Controller 方法上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiRateLimit {
}
