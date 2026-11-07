package com.driedfish.project_psychology_health_assist.common;

import lombok.Data;

@Data
public class Result<T> {
    private String code;
    private String msg;
    private T data;
    private Result(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);

    }
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }
    public static <T> Result<T> error() {
        return new Result<>(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg(), null);
    }
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);

    }
    public static <T> Result<T> error(ResultCode resultCode, T data) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), data);

    }
    public static <T> Result<T> error(String code, String msg, T data) {
        return new Result<>(code, msg, data);

    }
    public static <T> Result<T> error(String code, String msg) {
        return new Result<>(code, msg, null);

    }
}
