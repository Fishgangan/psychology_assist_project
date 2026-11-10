package com.driedfish.project_psychology_health_assist.util;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static Long getUserId() {
        //从spring security上下文获取当前认证对象
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null||!authentication.isAuthenticated()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
               }
        //从认证对象中获取用户id
        return  (Long) authentication.getPrincipal();
    }
}
