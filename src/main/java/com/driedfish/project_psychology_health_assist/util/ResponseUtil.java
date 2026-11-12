package com.driedfish.project_psychology_health_assist.util;

import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.driedfish.project_psychology_health_assist.common.Result;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseUtil {
    //过滤器的异常响应
    public static void writeError(HttpServletResponse response, ResultCode resultCode) {
        // 响应已提交，直接返回,否则若客户端断连，继续处理的话，会导致响应内容继续写入，导致异常
        if(response.isCommitted()){
            return;
        }
        int httpCode = switch (resultCode) {
            case UNAUTHORIZED,ACCESS_UNAUTHORIZED,TOKEN_INVALID,TOKEN_EXPIRED,TOKEN_BLOCKED-> HttpStatus.HTTP_UNAUTHORIZED;
            case TOKEN_ACCESS_FORBIDDEN -> HttpStatus.HTTP_FORBIDDEN;
            default -> HttpStatus.HTTP_BAD_REQUEST;
        };
        response.setStatus(httpCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        //改掉try-with-resources，因为response.getWriter()会自动关闭writer，导致writer无法使用，需要手动关闭
        try{PrintWriter writer =response.getWriter();
            String jsonResponse = JSONUtil.toJsonStr(Result.error(resultCode));
            writer.print(jsonResponse);
            writer.flush();//确保响应内容写入输出流
        }
        catch (IOException e){
            log.warn("响应异常"+e.getMessage());
        }

    }
}
