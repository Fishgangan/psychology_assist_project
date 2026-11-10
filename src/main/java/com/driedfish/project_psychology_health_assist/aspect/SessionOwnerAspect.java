package com.driedfish.project_psychology_health_assist.aspect;

import com.driedfish.project_psychology_health_assist.annotation.CheckSessionOwner;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationSessionMapper;
import com.driedfish.project_psychology_health_assist.util.SecurityUtils;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
//构造器注入代替字段注入
@RequiredArgsConstructor
public class SessionOwnerAspect {
    //构造器注入代替字段注入
    private final ConsultationSessionMapper consultationSessionMapper;
    @Before("@annotation(checkSessionOwner)")
    public void checkSessionOwner(JoinPoint joinPoint, CheckSessionOwner checkSessionOwner){
        Object[] args = joinPoint.getArgs();
        Object sessionArg = args[checkSessionOwner.sessionIdIndex()];
        String rawSessionId;
        if(sessionArg instanceof String){
            rawSessionId = sessionArg.toString();
        }else if(sessionArg!=null){
            //用反射获取sessionIdStr
            Object invokeResult;
            try{
                Method getSessionIdMethod = sessionArg.getClass().getMethod("getSessionId");
                //调用getSessionId方法获取sessionIdStr
                invokeResult=getSessionIdMethod.invoke(sessionArg);}
            catch (Exception e){
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            if(invokeResult==null){
                throw new BusinessException(ResultCode.SESSION_ID_FORMAT_ERROR);}
            rawSessionId = invokeResult.toString();
        }
        else{
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        Long userId = SecurityUtils.getUserId();
        Long sessionId = SessionUtils.extractSessionId(rawSessionId);
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if(session == null){
            throw new BusinessException(ResultCode.SESSION_NOT_EXIST);
        }
        if(!session.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.SESSION_NOT_PERMISSION);
        }
    }
}
