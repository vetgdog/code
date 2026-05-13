package com.code.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 认证过滤器。
 *
 * <p>该过滤器运行在 Spring Security 过滤链中，每个请求最多执行一次。职责非常聚焦：从 `Authorization` 头提取
 * Bearer Token，校验 token，解析用户名，再把认证对象放入 `SecurityContextHolder`，供后续权限注解和控制器使用。</p>
 *
 * <p>它本身不直接决定“是否拦截请求”，而是负责把认证上下文补齐；真正的访问放行与拒绝由后续的 Spring Security
 * 授权逻辑处理。这种分层符合标准 Security 设计。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JWT 工具类，负责 token 的签发规则反向校验与 subject 解析。
     */
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 从数据库加载用户详情。
     *
     * <p>即使 token 本身有效，系统仍会重新查一次用户，是为了拿到最新角色集合，避免用户角色调整后旧 token 继续携带过期权限。</p>
     */
    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * 处理单次 HTTP 请求的 JWT 认证。
     *
     * <p>当前实现策略较简洁：只要 token 合法，就重新按用户名加载一次用户详情并写入安全上下文。这样做的好处是：
     * 即便 token 里不携带完整角色信息，系统仍能拿到数据库里的最新权限；代价是每个带 token 的请求都会多一次查库。</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 标准 Bearer Token 约定放在 Authorization 请求头中。
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {

            // "Bearer " 前缀长度固定为 7，截取后得到纯 token 内容。
            String token = header.substring(7);
            if (jwtUtil.validateJwtToken(token)) {

                // JWT 里的 subject 被当作系统登录用户名，这个项目里实际就是邮箱地址。
                String username = jwtUtil.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // UsernamePasswordAuthenticationToken 是 Spring Security 最常见的认证载体：
                // principal = 当前用户，credentials = 已认证场景下无需再保存密码，因此传 null，
                // authorities = 后续 @PreAuthorize / hasRole 判断所依赖的角色集合。
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // SecurityContextHolder 使用线程本地上下文保存认证信息，
                // 当前请求线程中的后续控制器、AOP、方法级权限校验都会从这里读取当前登录用户。
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // 无论是否携带 token，都必须继续向后传递过滤链；
        // 是否最终允许访问，由后续授权规则决定，而不是在这里直接终止请求。
        filterChain.doFilter(request, response);
    }
}

