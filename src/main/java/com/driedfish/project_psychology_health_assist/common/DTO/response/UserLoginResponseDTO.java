package com.driedfish.project_psychology_health_assist.common.DTO.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailDTO userInfo;

}
