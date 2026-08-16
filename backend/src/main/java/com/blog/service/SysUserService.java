package com.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.LoginDto;
import com.blog.dto.RegisterDto;
import com.blog.entity.SysUser;

import java.util.Map;

public interface SysUserService extends IService<SysUser> {
    /**
     * 用户登录，成功后颁发 JWT 令牌
     */
    Map<String, String> login(LoginDto loginDto);

    /**
     * 用户自助注册
     */
    void register(RegisterDto registerDto);
}