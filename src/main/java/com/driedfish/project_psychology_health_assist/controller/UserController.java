package com.driedfish.project_psychology_health_assist.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.driedfish.project_psychology_health_assist.common.DTO.command.LoginCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.Result;
import com.driedfish.project_psychology_health_assist.service.UserService;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user")
public class UserController {
    @Resource
    private UserService userService;
    //用户登录
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody LoginCommandDTO dto) {
        UserLoginResponseDTO userLoginResponseDTO = userService.login(dto);
        return Result.success(userLoginResponseDTO);
    }
    //用户注册
    @PostMapping("/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody RegisterCommandDTO dto) {
        UserLoginResponseDTO.UserDetailResponseDTO userDetailResponseDTO = userService.register(dto);
        return Result.success(userDetailResponseDTO);
    }
    //获取当前用户信息
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        UserLoginResponseDTO.UserDetailResponseDTO userDetailResponseDTO = userService.getUserById(userId);
        return Result.success(userDetailResponseDTO);
    }
    //用户登出
    @PostMapping("/logout")
    public Result<String> logout() {
        String token = JwtTokenUtil.getCurrentToken();
        userService.logout(token);
        return Result.success("登出成功");
    }
}
