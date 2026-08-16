package com.blog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.entity.SysUser;
import com.blog.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 仅限具有管理员（ADMIN）角色的用户可操作该接口
public class UserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 条件分页查询用户列表
     */
    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {

        Page<SysUser> page = new Page<>(current, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword));
        }

        wrapper.orderByDesc(SysUser::getCreateTime);
        sysUserService.page(page, wrapper);

        // 安全考虑，返回前端时将密码抹去
        page.getRecords().forEach(user -> user.setPassword(null));
        return Result.success(page);
    }

    /**
     * 根据 ID 获取单个用户详情
     */
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 管理员手动创建新用户
     */
    @PostMapping
    public Result<?> create(@RequestBody SysUser user) {
        if (StringUtils.isNotBlank(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        sysUserService.save(user);
        return Result.success("用户创建成功");
    }

    /**
     * 修改用户信息 (不修改密码)
     */
    @PutMapping
    public Result<?> update(@RequestBody SysUser user) {
        // 更新普通信息时强行设密码为 null，不覆盖数据库已有密码（MyBatis-Plus默认忽略更新null字段）
        user.setPassword(null);
        user.setUsername(null); // 用户名不允许随意更改
        sysUserService.updateById(user);
        return Result.success("用户信息更新成功");
    }

    /**
     * 删除用户 (触发 MyBatis Plus 逻辑删除)
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.success("用户删除成功");
    }
}