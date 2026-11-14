package com.driedfish.project_psychology_health_assist.common.DTO.command;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserUpdateCommandDTO {
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    @Size(max = 255, message = "头像路径长度不能超过255个字符")
    private String avatar;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate birthday;
    private Integer gender;
}
