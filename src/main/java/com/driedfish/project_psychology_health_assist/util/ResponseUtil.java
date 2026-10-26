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


public class ResponseUtil {
    //过滤器的异常响应
    public static void writeError(HttpServletResponse response, ResultCode resultCode) {
        int httpCode = switch (resultCode) {
            case UNAUTHORIZED,ACCESS_UNAUTHORIZED,TOKEN_INVALID,TOKEN_EXPIRED,TOKEN_BLOCKED-> HttpStatus.HTTP_UNAUTHORIZED;
            case TOKEN_ACCESS_FORBIDDEN -> HttpStatus.HTTP_FORBIDDEN;
            default -> HttpStatus.HTTP_BAD_REQUEST;
        };
        response.setStatus(httpCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try(PrintWriter writer =response.getWriter()){
            String jsonResponse = JSONUtil.toJsonStr(Result.error(resultCode.getCode(),resultCode.getMsg(),null));
            writer.print(jsonResponse);
            writer.flush();//确保响应内容写入输出流
        }
        catch (IOException e){
            System.out.println("响应异常"+e.getMessage());
        }

    }
}
