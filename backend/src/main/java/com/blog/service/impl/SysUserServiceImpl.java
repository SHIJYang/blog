package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.dto.LoginDto;
import com.blog.dto.RegisterDto;
import com.blog.entity.SysUser;
import com.blog.mapper.SysUserMapper;
import com.blog.security.utils.JwtUtils;
import com.blog.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Map<String, String> login(LoginDto loginDto) {
        // 1. 使用 Spring Security 的 AuthenticationManager 校验账密
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword());

        Authentication authenticate;
        try {
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (Exception e) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 校验通过，查询用户角色并生成 JWT 返回
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginDto.getUsername()));

        String token = jwtUtils.generateToken(user.getUsername(), user.getRole());

        // 3. 组装 token 和 role 共同返回
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("role", user.getRole());
        return result;
    }

    @Override
    public void register(RegisterDto registerDto) {
        // 1. 校验用户名排他
        long countUsername = this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, registerDto.getUsername()));
        if (countUsername > 0) {
            throw new BusinessException(400, "该用户名已被注册");
        }

        // 2. 校验邮箱排他
        long countEmail = this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, registerDto.getEmail()));
        if (countEmail > 0) {
            throw new BusinessException(400, "该邮箱已被绑定");
        }

        // 3. 构建用户并存储
        SysUser user = new SysUser();
        user.setUsername(registerDto.getUsername());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword())); // 密码强哈希
        user.setNickname(registerDto.getNickname());
        user.setEmail(registerDto.getEmail());
        user.setRole("READER"); // 读者自主注册默认为 READER
        user.setStatus(1);      // 默认启用

        this.save(user);
    }
}