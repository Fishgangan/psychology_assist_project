package com.driedfish.project_psychology_health_assist.common;

import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import reactor.core.Exceptions;

import java.io.IOException;
import java.net.SocketException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e){
        String msg =  e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR,msg);
    }
    //json解析异常
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleMessageNotReadableException(HttpMessageNotReadableException e){
        String msg = "请求参数格式错误";
        if(e.getCause() instanceof InvalidFormatException invalidFormatException){
            if(!invalidFormatException.getPath().isEmpty()){
                String fieldName = invalidFormatException.getPath().get(0).getFieldName();
                Object value = invalidFormatException.getValue();
                msg=String.format("字段 %s ,您输入值 %s 格式错误",fieldName,value);
            }
        }
        return Result.error(ResultCode.PARAM_ERROR,msg);
    }
    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e){
        //异常携带额外信息
        if(e.getData() != null){
            return Result.error(e.getCode(),e.getMessage(), e.getData());
        }else{
            return Result.error(e.getCode(),e.getMessage(), null);
        }
    }
    //IO异常处理
    @ExceptionHandler(IOException.class)
    public void handleIOException(HttpServletResponse response, IOException e){
        // 靠字符串，错误类型判断是否是网络断开异常不通用
//        //找底层Cause
//        Throwable rootCause = Exceptions.unwrap(e);
//        String msg=rootCause.getMessage()!=null?rootCause.getMessage().toLowerCase():"";
//        //根据msg判断是否是网络断开异常
//        boolean isNetWorkDisconnect =
//                rootCause instanceof ClientAbortException ||
//                rootCause instanceof SocketException||
//                msg.contains("broken pipe")        ||
//                msg.contains("connection reset")||
//                msg.contains("connection abort")||
//                msg.contains("forcibly closed");
//        if(isNetWorkDisconnect){
//            log.info("[SSE] 客户端主动断开连接，停止推送。{}",msg);
//            return;}

        if(response.isCommitted()){
            log.info("[SSE] 客户端主动断开连接，停止推送。{}",e.getMessage());
            return;
        }

            log.error("出现未知IO异常",e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

    }
    //兜底异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        log.error("出现未知异常",e);
        return Result.error(ResultCode.SYSTEM_ERROR);    }
}
