package com.driedfish.project_psychology_health_assist.util;

import com.driedfish.project_psychology_health_assist.common.DTO.internal.TokenVerifyResult;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserDetailDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.config.SecurityConfig;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String requestURI = request.getRequestURI();
        return SecurityConfig.isPublicPath(requestURI);

    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        //获取请求头中的token
        String token = jwtTokenUtil.extractTokenFromRequest(request);
        if(!StringUtils.hasText(token)){
                //清理上下文
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
                return;

        }
            //已注销的token直接拒绝
            //redis连不上时的降级处理
        try{
            if(jwtTokenUtil.isTokenBlocked(token)){
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_BLOCKED);
                return;
            }}
        catch (Exception e){
                log.warn("redis连接异常,无法进行token黑名单校验，进行降级处理，暂时放行请求",e.getMessage(),e);
            }
        TokenVerifyResult.result resultJwtTokenUtil=jwtTokenUtil.validateToken(token);
        if(!resultJwtTokenUtil.valid()){
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            return;}
                //查询用户状态
        UserDetailDTO user=userService.getUserById(resultJwtTokenUtil.userId());
        if(user==null||!UserStatus.NORMAL.getCode().equals(user.getStatus())){
                clearSecurityContext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
                return;
        }
        //创建spring Security认证对象
        List<SimpleGrantedAuthority> authorities=Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+resultJwtTokenUtil.roleType()));
        //创建UsernamePasswordAuthenticationToken对象(认证凭证)（principal,credentials,authorities）
        UsernamePasswordAuthenticationToken authenticationToken =new UsernamePasswordAuthenticationToken(resultJwtTokenUtil.userId(), null, authorities);
        //设置认证信息到Spring Security上下文对象
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        //token存储到请求属性中
        request.setAttribute("jwtToken",token);




        //继续过滤器链
        chain.doFilter(request, response);}
        //清理spring security上下文
        private void clearSecurityContext(){
            SecurityContextHolder.clearContext();
        }
}