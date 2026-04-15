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
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/v1/auth/**", "/ws/**").permitAll()
                .antMatchers("/api/v1/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                .antMatchers("/api/v1/orders/**").hasAnyRole("ADMIN", "SALES_MANAGER", "WAREHOUSE_MANAGER", "PRODUCTION_MANAGER")
                .antMatchers("/api/v1/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "SALES_MANAGER")
                .antMatchers("/api/v1/production/**").hasAnyRole("ADMIN", "PRODUCTION_MANAGER", "WAREHOUSE_MANAGER")
                .antMatchers("/api/v1/procurement/**").hasAnyRole("SUPPLIER", "PROCUREMENT_MANAGER", "WAREHOUSE_MANAGER", "ADMIN")
                .antMatchers("/api/v1/quality/**").hasAnyRole("QUALITY_INSPECTOR", "ADMIN")
                .anyRequest().authenticated();

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}

