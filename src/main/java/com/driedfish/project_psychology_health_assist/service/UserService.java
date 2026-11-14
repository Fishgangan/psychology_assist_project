package com.driedfish.project_psychology_health_assist.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driedfish.project_psychology_health_assist.common.DTO.command.LoginCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.PasswordUpdateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.RegisterCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.UserUpdateCommandDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserDetailDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.UserLoginResponseDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.enumClass.UserStatus;
import com.driedfish.project_psychology_health_assist.enumClass.UserType;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;

import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import com.driedfish.project_psychology_health_assist.service.convert.UserConvert;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import com.driedfish.project_psychology_health_assist.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {
    private final JwtTokenUtil jwtTokenUtil;
    private final UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    //用户登录
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
        String inputPassword = dto.getPassword();
        if (!passwordEncoder.matches(inputPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);}
        if(!UserStatus.NORMAL.getCode().equals(user.getStatus())){
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        //生成token
        String token = jwtTokenUtil.generateJwtToken(user.getId(), user.getUsername(), user.getUserType());
        UserDetailDTO userInfo = UserConvert.entityToDetailResponse(user);
        return UserConvert.entityToLoginResponse(token, userInfo);
    }
    //用户注册
    public UserDetailDTO register(RegisterCommandDTO dto) {
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
        //创建用户
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        User user = UserConvert.registerCommandToEntity(dto, encodedPassword);
        //默认用户类型为普通
        user.setUserType(UserType.USER.getCode());
        //默认用户状态为普通
        user.setStatus(UserStatus.NORMAL.getCode());
        userMapper.insert(user);

        return UserConvert.entityToDetailResponse(user);
    }
    //根据用户ID查询用户详情
    public UserDetailDTO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        UserDetailDTO userInfo = UserConvert.entityToDetailResponse(user);

        return userInfo;
    }
    //更新用户信息
    public UserDetailDTO updateUser(Long userId,UserUpdateCommandDTO dto) {
        User user =new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        user.setGender(dto.getGender());
        user.setBirthday(dto.getBirthday());
        userMapper.updateById(user);
        User updatedUser = userMapper.selectById(userId);
        return UserConvert.entityToDetailResponse(updatedUser);
    }
    //更新用户密码
    public void updatePassword(Long userId, PasswordUpdateDTO dto) {
        //查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        //验证新密码是否一致
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_REPEAT);
        }
        //验证旧密码是否正确
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        //更新密码
        String encodedPassword = passwordEncoder.encode(dto.getNewPassword());
        user.setPassword(encodedPassword);
        userMapper.updateById(user);
        //将旧token失效
        String token = jwtTokenUtil.getCurrentToken();
        if (StringUtils.hasText(token)) {
            jwtTokenUtil.invalidateToken(token);
        }
    }
       //用户登出
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.TOKEN_NOT_BLANKED);
        }
        jwtTokenUtil.invalidateToken(token);
    }

}
