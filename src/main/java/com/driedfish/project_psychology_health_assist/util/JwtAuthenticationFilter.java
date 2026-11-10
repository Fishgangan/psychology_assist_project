package com.driedfish.project_psychology_health_assist.util;

import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.config.SecurityConfig;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Resource
    private UserService userService;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String requestURI = request.getRequestURI();
        return SecurityConfig.isPublicPath(requestURI);

    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        //获取请求头中的token
        String token = JwtTokenUtil.extractTokenFromRequest(request);
        if(StringUtils.hasText(token)){
            //已注销的token直接拒绝
            if(JwtTokenUtil.isTokenBlocked(token)){
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_BLOCKED);
                return;
            }
            JwtTokenUtil.TokenVerifyResult resultJwtTokenUtil=JwtTokenUtil.validateToken(token);
            if(resultJwtTokenUtil.isValid()){
                //查询用户状态
                UserLoginResponseDTO.UserDetailResponseDTO user=userService.getUserById(resultJwtTokenUtil.getUserId());
                if(user!=null&& UserStatus.NORMAL.getCode().equals(user.getStatus())){
                   //创建spring Security认证对象
                    List<SimpleGrantedAuthority> authorities=Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+resultJwtTokenUtil.getRoleType()));
                    //创建UsernamePasswordAuthenticationToken对象(认证凭证)（principal,credentials,authorities）
                    UsernamePasswordAuthenticationToken authenticationToken =new UsernamePasswordAuthenticationToken(resultJwtTokenUtil.getUserId(), null, authorities);
                    //设置认证信息到Spring Security上下文对象
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    //token存储到请求属性中
                    request.setAttribute("jwtToken",token);

                }
                else{
                    clearSecurityContext();
                    ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
                    return;
                }

            }else {
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
                return;

            }
        }else {
            //清理上下文
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
            return;
        }

    //继续过滤器链
    chain.doFilter(request, response);}
    //清理spring security上下文
    private void clearSecurityContext(){
        SecurityContextHolder.clearContext();
    }
}