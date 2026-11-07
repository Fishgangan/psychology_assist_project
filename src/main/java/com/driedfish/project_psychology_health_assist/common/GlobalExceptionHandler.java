package com.driedfish.project_psychology_health_assist.common;

import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
        return Result.error(ResultCode.PARAM_ERROR);
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
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        log.error("出现未知异常",e);
        return Result.error(ResultCode.SYSTEM_ERROR);    }
}
