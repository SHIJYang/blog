package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.LoginDto;
import com.blog.dto.RegisterDto;
import com.blog.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterDto registerDto) {
        sysUserService.register(registerDto);
        return Result.success("注册成功");
    }

    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginDto loginDto) {
        // 直接获取包含 token 和 role 的 Map 结果
        Map<String, String> loginResult = sysUserService.login(loginDto);
        return Result.success(loginResult);
    }
}