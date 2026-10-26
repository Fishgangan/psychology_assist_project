package com.driedfish.project_psychology_health_assist.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driedfish.project_psychology_health_assist.common.DTO.command.LoginCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;

import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import com.driedfish.project_psychology_health_assist.service.convert.UserConvert;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    public UserLoginResponseDTO login(LoginCommandDTO dto) {
        //构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, dto.getUsername())
                .or()
                .eq(User::getEmail, dto.getUsername());
        //调mybatis-plus api查询
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        String inputPassword = dto.getPassword().trim();
        if (!passwordEncoder.matches(inputPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);}
        if(!user.isActive()){
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        //生成token
        String token = JwtTokenUtil.generateJwtToken(user.getId(), user.getUsername(), user.getUserType());
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvert.entityToDetailResponse(user);
        return UserConvert.entityToLoginResponse(token, userInfo);
    }
    public UserLoginResponseDTO.UserDetailResponseDTO register(RegisterCommandDTO dto) {
        //验证密码是否一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_REPEAT);
        }
        //验证用户名或邮箱是否已存在
        LambdaQueryWrapper<User> nameQueryWrapper = new LambdaQueryWrapper<>();
        nameQueryWrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(nameQueryWrapper)> 0) {
            throw new BusinessException(ResultCode.USER_SAME);
        }
        LambdaQueryWrapper<User> emailQueryWrapper = new LambdaQueryWrapper<>();
        emailQueryWrapper.eq(User::getEmail, dto.getEmail());
        if (userMapper.selectCount(emailQueryWrapper)> 0) {
            throw new BusinessException(ResultCode.EMAIL_SAME);
        }
        //验证用户类型
        if(!UserType.isValidCode(dto.getUserType())){
            throw new BusinessException(ResultCode.USER_TYPE_INVALID);
        }
        //创建用户
        String encodedPassword = passwordEncoder.encode(dto.getPassword().trim());
        User user = UserConvert.registerCommandToEntity(dto, encodedPassword);
        userMapper.insert(user);

        return UserConvert.entityToDetailResponse(user);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long id) {
//        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(User::getId, id);
//        User user = userMapper.selectOne(queryWrapper);
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvert.entityToDetailResponse(user);

        return userInfo;
    }
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.TOKEN_NOT_BLANKED);
        }
        JwtTokenUtil.invalidateToken(token);
    }
}
