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
        response.setStatus(HttpStatus.HTTP_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        //改掉try-with-resources，因为出{}时这种语法会自动关闭writer，但writer是由tomcat管理的，导致writer无法继续使用，自己flush()一下即可
        try{PrintWriter writer =response.getWriter();
            String jsonResponse = JSONUtil.toJsonStr(Result.error(resultCode));
            writer.print(jsonResponse);
            writer.flush();
        }
        catch (IOException e){
            log.warn("响应异常"+e.getMessage());
        }

    }
}
