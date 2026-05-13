package com.code.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
/*
 * 安全配置总入口。
 *
 * <p>该类属于系统的认证授权基础设施层，核心职责是：
 * 1. 定义 HTTP 请求在进入业务 Controller 前的访问控制规则；
 * 2. 声明系统使用 JWT 无状态认证，而不是传统 Session；
 * 3. 注册密码加密器与 AuthenticationManager，供登录流程和 Spring Security 使用；
 * 4. 将自定义的 JWT 过滤器插入 Spring Security 过滤器链，实现在每次请求中解析令牌并恢复登录态。</p>
 *
 * <p>从企业级设计角度看，这个类相当于“统一安全网关配置”，
 * 把 URL 访问控制、认证方式、过滤器顺序集中管理，避免权限逻辑散落在各个 Controller 中难以维护。</p>
 */
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    /*
     * 构建 Spring Security 的核心过滤器链。
     *
     * <p>执行流程大致为：
     * 1. 关闭 CSRF；
     * 2. 设置 Session 为 STATELESS，表示服务端不保存登录会话；
     * 3. 配置各业务模块的 URL 访问权限；
     * 4. 将自定义 JWT 过滤器插入用户名密码过滤器之前；
     * 5. 返回最终可执行的安全过滤链。</p>
     *
     * <p>这里之所以使用无状态会话，是因为前后端分离系统更适合通过 Token 在每次请求中自描述身份，
     * 可以降低服务端 Session 存储压力，也更适合后续扩展为多实例部署。</p>
     */
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT 场景下，通常不依赖 Cookie 自动提交认证信息，
                // 因此可以关闭 Spring Security 默认的 CSRF 防护。
                // 如果后续系统改为基于 Cookie 的会话认证，这里需要重新评估是否开启。
                .csrf().disable()

                // 明确声明系统不创建 HttpSession。
                // 这意味着每个请求都必须自己携带 JWT，由过滤器完成身份恢复。
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()

                // 登录、注册、WebSocket 握手端点允许匿名访问，
                // 否则用户在未登录前无法获取 Token，也无法建立实时通知通道。
                .antMatchers("/api/v1/auth/**", "/ws/**").permitAll()

                // 以下是按业务模块划分的粗粒度 URL 权限控制。
                // 这种配置负责第一层访问拦截，Controller/Service 上的 @PreAuthorize
                // 则用于补充更细粒度的角色或方法级校验。
                .antMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .antMatchers("/api/v1/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                .antMatchers("/api/v1/orders/**").hasAnyRole("ADMIN", "SALES_MANAGER", "WAREHOUSE_MANAGER", "PRODUCTION_MANAGER")
                .antMatchers("/api/v1/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                .antMatchers("/api/v1/production/**").hasAnyRole("ADMIN", "PRODUCTION_MANAGER", "WAREHOUSE_MANAGER")
                .antMatchers("/api/v1/procurement/**").hasAnyRole("SUPPLIER", "PROCUREMENT_MANAGER", "WAREHOUSE_MANAGER", "ADMIN")
                .antMatchers("/api/v1/quality/**").hasAnyRole("QUALITY_INSPECTOR", "ADMIN")

                // 任何未显式放行的请求都必须先通过认证，防止遗漏的接口“裸奔”。
                .anyRequest().authenticated();

        // 将 JWT 过滤器放在 UsernamePasswordAuthenticationFilter 之前，
        // 目的是让系统先尝试从请求头解析 Bearer Token，并在进入后续鉴权流程前
        // 把用户身份写入 SecurityContext。
        // 这是 Spring Security 典型的 Token 认证接入方式。
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    /*
     * 密码编码器。
     *
     * <p>使用 BCrypt 的原因：
     * 1. 它是业界成熟的单向哈希算法；
     * 2. 自带随机盐，能有效抵抗彩虹表攻击；
     * 3. 可通过强度参数逐步提升计算成本，适应硬件演进。</p>
     */
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    /*
     * 暴露 AuthenticationManager 给登录接口使用。
     *
     * <p>Spring Security 在内部会根据 UserDetailsService + PasswordEncoder
     * 组装出完整的认证管理器。这里将其作为 Bean 暴露，
     * 方便 AuthController 在登录时执行标准认证流程，而不是自己手写密码比对逻辑。</p>
     */
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}

