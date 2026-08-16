package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.common.JsoupUtils;
import com.blog.dto.CommentSaveDto;
import com.blog.entity.BlogComment;
import com.blog.entity.SysUser;
import com.blog.mapper.BlogCommentMapper;
import com.blog.mapper.SysUserMapper;
import com.blog.security.user.LoginUser;
import com.blog.security.utils.SecurityUtils;
import com.blog.service.BlogCommentService;
import com.blog.vo.CommentVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogCommentServiceImpl extends ServiceImpl<BlogCommentMapper, BlogComment> implements BlogCommentService {

    private final BlogCommentMapper blogCommentMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveComment(CommentSaveDto dto, String ipAddress) {
        BlogComment comment = new BlogComment();
        comment.setArticleId(dto.getArticleId());
        comment.setIpAddress(ipAddress);
        comment.setParentId(dto.getParentId());

        // 核心安全：Jsoup 清洗富文本
        String cleanContent = JsoupUtils.cleanHtml(dto.getContent());
        comment.setContent(cleanContent);
        comment.setWebsite(dto.getWebsite());

        // 1. 尝试获取当前登录用户
        LoginUser loginUser = SecurityUtils.getLoginUser();
        boolean isRegisteredUser = false;

        if (loginUser != null) {
            SysUser user = sysUserMapper.selectById(loginUser.getId());
            if (user != null) {
                comment.setUserId(user.getId());
                comment.setNickname(user.getNickname());
                comment.setEmail(user.getEmail());
                isRegisteredUser = true;
            }
        } else {
            // 游客评论：校验并填充临时信息
            if (StringUtils.isBlank(dto.getNickname()) || StringUtils.isBlank(dto.getEmail())) {
                throw new BusinessException(400, "游客发布评论必须填写昵称及有效的电子邮箱");
            }
            comment.setNickname(dto.getNickname());
            comment.setEmail(dto.getEmail());
        }

        // 2. 指针算法：维护两级平铺盖楼的 root_id 指向
        if (dto.getParentId() != null) {
            BlogComment parentComment = blogCommentMapper.selectById(dto.getParentId());
            if (parentComment == null) {
                throw new BusinessException(400, "回复的目标评论已失效或不存在");
            }
            if (parentComment.getRootId() != null) {
                comment.setRootId(parentComment.getRootId());
            } else {
                comment.setRootId(parentComment.getId());
            }
        } else {
            comment.setRootId(null);
        }

        // 3. 简单自动审核
        int auditStatus = performAutoAudit(comment, isRegisteredUser);
        comment.setStatus(auditStatus);

        this.save(comment);
    }

    /**
     * 简单自动审核逻辑
     * @param comment 评论实体
     * @param isRegisteredUser 是否为已登录用户
     * @return 状态码：1-直接通过，0-待人工审核
     */
    private int performAutoAudit(BlogComment comment, boolean isRegisteredUser) {
        // 规则 1：如果是已登录的注册用户，可给予更高的信任度，直接通过审核（或根据需要降低审核标准）
        if (isRegisteredUser) {
            return 1;
        }

        // 模拟敏感词库（实际生产中建议将此词库维护在数据库、Redis或配置文件中，并采用 DFA 算法提高检索效率）
        List<String> sensitiveWords = Arrays.asList("广告推广", "刷单", "代孕", "违禁品", "翻墙");

        String content = comment.getContent();
        String nickname = comment.getNickname();
        String website = comment.getWebsite();

        // 规则 2：敏感词校验（内容与昵称）
        if (StringUtils.isNotBlank(content)) {
            for (String word : sensitiveWords) {
                if (content.contains(word)) {
                    return 0; // 包含敏感词，进入待审核状态
                }
            }
        }
        if (StringUtils.isNotBlank(nickname)) {
            for (String word : sensitiveWords) {
                if (nickname.contains(word)) {
                    return 0; // 昵称违规，进入待审核状态
                }
            }
        }

        // 规则 3：链接过多校验（防垃圾广告推广）
        // 游客在内容中携带多于 1 个链接（http:// 或 https://），自动转入人工审核
        if (StringUtils.isNotBlank(content)) {
            int linkCount = countOccurrences(content, "http://") + countOccurrences(content, "https://");
            if (linkCount > 1) {
                return 0;
            }
        }

        // 规则 4：异常网址格式校验
        if (StringUtils.isNotBlank(website)) {
            String urlPattern = "^(https?://)?([a-zA-Z0-9\\.\\-]+)\\.([a-zA-Z]{2,6})(:[0-9]{1,5})?(/.*)?$";
            if (!Pattern.matches(urlPattern, website)) {
                return 0; // 个人主页链接格式异常，进入待审核状态
            }
        }

        // 校验全部通过，默认直接发布
        return 1;
    }

    /**
     * 辅助方法：计算子字符串在源字符串中出现的次数
     */
    private int countOccurrences(String str, String subStr) {
        if (StringUtils.isEmpty(str) || StringUtils.isEmpty(subStr)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(subStr, idx)) != -1) {
            count++;
            idx += subStr.length();
        }
        return count;
    }

    @Override
    public Page<CommentVo> getCommentPageByArticleId(Long articleId, Integer current, Integer size) {
        // 1. 查询该文章的一级主评论（rootId 为空，时间倒序）
        Page<BlogComment> rootPage = new Page<>(current, size);
        LambdaQueryWrapper<BlogComment> rootQuery = new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getArticleId, articleId)
                .isNull(BlogComment::getRootId)
                .eq(BlogComment::getStatus, 1)
                .orderByDesc(BlogComment::getCreateTime);

        blogCommentMapper.selectPage(rootPage, rootQuery);

        Page<CommentVo> voPage = new Page<>(rootPage.getCurrent(), rootPage.getSize(), rootPage.getTotal());
        List<BlogComment> rootComments = rootPage.getRecords();
        if (rootComments.isEmpty()) {
            return voPage;
        }

        // 2. 批量查出对应一级楼层下属的所有二级评论（时间顺序升序排列）
        List<Long> rootIds = rootComments.stream().map(BlogComment::getId).collect(Collectors.toList());
        List<BlogComment> childComments = blogCommentMapper.selectList(new LambdaQueryWrapper<BlogComment>()
                .in(BlogComment::getRootId, rootIds)
                .eq(BlogComment::getStatus, 1)
                .orderByAsc(BlogComment::getCreateTime));

        // 3. 收集所有相关用户 ID，减少查询次数，高性能装载头像
        List<Long> userIds = new ArrayList<>();
        rootComments.forEach(c -> { if (c.getUserId() != null) userIds.add(c.getUserId()); });
        childComments.forEach(c -> { if (c.getUserId() != null) userIds.add(c.getUserId()); });

        Map<Long, String> userAvatarMap = Map.of();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds.stream().distinct().collect(Collectors.toList()));
            userAvatarMap = users.stream()
                    .filter(u -> StringUtils.isNotBlank(u.getAvatar()))
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getAvatar, (v1, v2) -> v1));
        }

        // 4. 将被回复评论的 ID 与其对应的昵称快速做内存检索（用于拼出：@xxx 昵称）
        Map<Long, String> commentNicknameMap = childComments.stream()
                .collect(Collectors.toMap(BlogComment::getId, BlogComment::getNickname, (v1, v2) -> v1));
        rootComments.forEach(c -> commentNicknameMap.put(c.getId(), c.getNickname()));

        // 5. 将二级子回复按照 rootId（楼层）在 Java 内存中划分分组
        Map<Long, List<BlogComment>> childGroupMap = childComments.stream()
                .collect(Collectors.groupingBy(BlogComment::getRootId));

        final Map<Long, String> finalUserAvatarMap = userAvatarMap;

        // 6. 装配两级 VO 结构
        List<CommentVo> rootVoList = rootComments.stream().map(root -> {
            CommentVo rootVo = convertToVo(root, finalUserAvatarMap, commentNicknameMap);

            List<BlogComment> replies = childGroupMap.getOrDefault(root.getId(), new ArrayList<>());
            List<CommentVo> repliesVo = replies.stream()
                    .map(child -> convertToVo(child, finalUserAvatarMap, commentNicknameMap))
                    .collect(Collectors.toList());

            rootVo.setReplies(repliesVo);
            return rootVo;
        }).collect(Collectors.toList());

        voPage.setRecords(rootVoList);
        return voPage;
    }

    private CommentVo convertToVo(BlogComment comment, Map<Long, String> avatarMap, Map<Long, String> nicknameMap) {
        CommentVo vo = new CommentVo();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        vo.setUserId(comment.getUserId());
        vo.setNickname(comment.getNickname());
        vo.setEmail(comment.getEmail());
        vo.setWebsite(comment.getWebsite());
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setRootId(comment.getRootId());
        vo.setCreateTime(comment.getCreateTime());

        // 设置父回复昵称，用于平铺界面中 @ 展示
        if (comment.getParentId() != null) {
            vo.setParentNickname(nicknameMap.get(comment.getParentId()));
        }

        // 装配注册用户的头像
        if (comment.getUserId() != null) {
            vo.setAvatar(avatarMap.get(comment.getUserId()));
        }

        return vo;
    }
}