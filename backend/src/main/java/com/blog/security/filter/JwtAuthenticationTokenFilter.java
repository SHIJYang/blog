package com.blog.security.filter;

import com.blog.security.user.LoginUser;
import com.blog.security.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String tokenPrefix = "Bearer ";

        if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith(tokenPrefix)) {
            String token = authHeader.substring(tokenPrefix.length());
            try {
                String username = jwtUtils.getUsernameFromToken(token);
                if (StringUtils.isNotBlank(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
                    LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);
                    if (jwtUtils.validateToken(token, loginUser.getUsername())) {
                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    }
                }
            } catch (Exception e) {
                // Token 解析失败或过期时清理上下文
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}