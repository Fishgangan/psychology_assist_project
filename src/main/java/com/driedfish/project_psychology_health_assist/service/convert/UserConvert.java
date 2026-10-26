package com.driedfish.project_psychology_health_assist.service.convert;

import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;

import java.time.LocalDateTime;

public class UserConvert {
    //用户登录响应DTO

    public static UserLoginResponseDTO.UserDetailResponseDTO entityToDetailResponse(User user){
        return UserLoginResponseDTO.UserDetailResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .gender(user.getGender())
                .genderDisplayName(getGenderDisplayName(user.getGender()))
                .birthday(user.getBirthday())
                .userType(user.getUserType())
                .userTypeDisplayName(user.getUserTypeDisplayName())
                .status(user.getStatus())
                .statusDisplayName(user.getStatusDisplayName())
                .displayName(user.getDisplayName())
                .createdAT(user.getCreatedAt())
                .updatedAT(user.getUpdatedAt())
                .build();
    }
    public static UserLoginResponseDTO entityToLoginResponse(String token, UserLoginResponseDTO.UserDetailResponseDTO userInfo){
        return UserLoginResponseDTO.builder()
                .token(token)
                .roleType(userInfo.getUserType().toString())
                .userInfo(userInfo)
                .build();
    }
    public static User registerCommandToEntity(RegisterCommandDTO dto, String encodedPassword){
        return User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .nickname(dto.getNickname())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .birthday(dto.getBirthday())
                .userType(dto.getUserType())
                .password(encodedPassword)
                .status(UserStatus.NORMAL.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

    }
    private static String getGenderDisplayName(Integer gender){
        if(gender == null){
            return "未知";
        }
        switch (gender){
            case 1:
                return "男";
            case 2:
                return "女";
            default:
                return "未知";
        }
    }


}
