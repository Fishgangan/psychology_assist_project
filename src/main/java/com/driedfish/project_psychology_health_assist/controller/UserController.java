package com.driedfish.project_psychology_health_assist.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.driedfish.project_psychology_health_assist.common.DTO.command.LoginCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.PasswordUpdateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.UserUpdateCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserDetailDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.Result;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.service.UserService;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import com.driedfish.project_psychology_health_assist.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    //用户登录
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody LoginCommandDTO dto) {
        UserLoginResponseDTO userLoginResponseDTO = userService.login(dto);
        return Result.success(userLoginResponseDTO);
    }
    //用户注册
    @PostMapping("/add")
    public Result<UserDetailDTO> register(@Valid @RequestBody RegisterCommandDTO dto) {
        UserDetailDTO userDetailResponseDTO = userService.register(dto);
        return Result.success(userDetailResponseDTO);
    }
    //获取当前用户信息
    @GetMapping("/current")
    public Result<UserDetailDTO> getCurrentUser() {
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        UserDetailDTO userDetailResponseDTO = userService.getUserById(userId);
        return Result.success(userDetailResponseDTO);
    }
    //更新用户信息
    @PostMapping("/update")
    public Result<UserDetailDTO> updateUser(@Valid @RequestBody UserUpdateCommandDTO dto) {
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        UserDetailDTO userDetailResponseDTO = userService.updateUser(userId,dto);
        return Result.success(userDetailResponseDTO);
    }
    //更新用户密码
    @PostMapping("/updatePassword")
    public Result<String> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        userService.updatePassword(userId,dto);
        return Result.success("密码更新成功");
    }
    //用户登出
    @PostMapping("/logout")
    public Result<String> logout() {
        String token = jwtTokenUtil.getCurrentToken();
        userService.logout(token);
        return Result.success("登出成功");
    }
    
}
