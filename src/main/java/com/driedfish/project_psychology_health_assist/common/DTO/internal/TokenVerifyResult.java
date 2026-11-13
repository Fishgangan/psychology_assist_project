package com.driedfish.project_psychology_health_assist.common.DTO.internal;

import lombok.Getter;
@Getter
public class TokenVerifyResult {
    //token返回结果封装
    public record result(String username,Long userId,Integer roleType,boolean valid){

    }

}
