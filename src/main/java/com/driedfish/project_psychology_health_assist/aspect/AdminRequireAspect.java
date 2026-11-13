package com.driedfish.project_psychology_health_assist.aspect;

import com.driedfish.project_psychology_health_assist.annotation.RequireAdmin;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;
import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import com.driedfish.project_psychology_health_assist.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AdminRequireAspect {
    private final UserMapper userMapper;
    @Before("@annotation(requireAdmin)")
    public void checkAdmin(RequireAdmin requireAdmin) {
        // 检查用户是否为管理员
        Long userId= SecurityUtils.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!UserType.ADMIN.getCode().equals(user.getUserType())) {
            throw new BusinessException(ResultCode.USER_NOT_ADMIN);
        }
    }
}
