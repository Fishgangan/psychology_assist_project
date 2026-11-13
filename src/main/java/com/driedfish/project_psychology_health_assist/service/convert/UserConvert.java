package com.driedfish.project_psychology_health_assist.service.convert;

import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.UserUpdateCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserDetailDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;

import java.time.LocalDateTime;

public class UserConvert {
    //用户详情响应DTO
    public static UserDetailDTO entityToDetailResponse(User user){
        return UserDetailDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .userType(user.getUserType())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
    //用户登录响应DTO
    public static UserLoginResponseDTO entityToLoginResponse(String token, UserDetailDTO userInfo){
        return UserLoginResponseDTO.builder()
                .token(token)
                .roleType(userInfo.getUserType().toString())
                .userInfo(userInfo)
                .build();
    }
    //用户注册命令DTO转换为User实体
    public static User registerCommandToEntity(RegisterCommandDTO dto, String encodedPassword){
        return User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .nickname(dto.getNickname())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .birthday(dto.getBirthday())
                .password(encodedPassword)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

    }


}
