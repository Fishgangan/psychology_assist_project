package com.driedfish.project_psychology_health_assist.common.exception;

import com.driedfish.project_psychology_health_assist.common.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final Object data;
    public BusinessException(ResultCode resultCode,Object data) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.data = data;
    }
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.data = null;
    }

}
