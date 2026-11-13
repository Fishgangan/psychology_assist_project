package com.driedfish.project_psychology_health_assist.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {
    // 主键id
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户名
    private String username;

    // 邮箱
    private String email;

    // 手机号
    private String phone;

    // 密码
    private String password;

    // 昵称
    private String nickname;

    // 头像
    private String avatar;

    // 性别
    private Integer gender;

    // 生日
    private LocalDate birthday;

    // 用户类型 1:普通用户 2:管理员
    private Integer userType;

    // 状态 0:禁用 1:正常
    private Integer status;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}

