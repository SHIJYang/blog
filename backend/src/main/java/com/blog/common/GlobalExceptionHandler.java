package com.blog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("参数校验异常: {}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("参数绑定异常: {}", message);
        return Result.error(400, message);
    }
    /**
     * 捕获 Spring Security 拒绝访问（无权限）异常
     * Spring Security 7.x 的 AuthorizationDeniedException 继承自此异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限校验未通过: {}", e.getMessage());
        return Result.error(403, "没有权限访问该资源");
    }
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}