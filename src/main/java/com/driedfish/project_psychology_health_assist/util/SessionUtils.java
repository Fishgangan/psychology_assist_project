package com.driedfish.project_psychology_health_assist.util;

import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;

public class SessionUtils {
    //获取sessionId
    public static Long extractSessionId(String sessionId) {
        if(sessionId==null||!sessionId.startsWith("session_")){
            throw new BusinessException(ResultCode.SESSION_ID_FORMAT_ERROR);
        }
        String sessionIdStr = sessionId.substring("session_".length());
        //验证sessionId是否为数字
        if(!sessionIdStr.matches("\\d+")){
            throw new BusinessException(ResultCode.SESSION_ID_FORMAT_ERROR);
        }
        return Long.parseLong(sessionIdStr);


    }
}
