package com.driedfish.project_psychology_health_assist.config;

import cn.hutool.core.text.AntPathMatcher;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.util.JwtAuthenticationFilter;
import com.driedfish.project_psychology_health_assist.util.ResponseUtil;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity



public class SecurityConfig {
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private static final String[] PUBLIC_PATHS = {
        "/",
        "/api/user/login",
        "/api/user/add",
        "/error"
        };
    public static Boolean isPublicPath(String requestURI){
        for(String path : PUBLIC_PATHS){
            if(antPathMatcher.match(requestURI, path)){
                return true;
            }
        }
        return false;
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception{
        //通过httpSecurity配置安全过滤链
        http
                // 关闭csrf保护（因为是无状态的api，所以不需要csrf保护）
                .csrf(AbstractHttpConfigurer::disable)
                //配置会话管理为无状态会话（JWT需要）
                .sessionManagement((session)->{session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);})
                // 配置权限管理
                .authorizeHttpRequests(auth->auth
                // 底层错误请求通过，防止错误请求因无认证被拦截导致403错误
                .dispatcherTypeMatchers(DispatcherType.ERROR,DispatcherType.ASYNC).permitAll()
                // 无需认证
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated() )
                .exceptionHandling((exception)->exception.accessDeniedHandler((request, response, accessDeniedException)->{
                    //客户端断连引发，不报错
                    if(response.isCommitted()){
                        return;
                    }
                    ResponseUtil.writeError(response, ResultCode.AUTHORIZED_ERROR);
                }))
                               // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
