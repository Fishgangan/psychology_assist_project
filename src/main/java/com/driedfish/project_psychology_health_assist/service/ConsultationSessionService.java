package com.driedfish.project_psychology_health_assist.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationSessionMapper;
import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        //验证用户是否存在
        User user = userMapper.selectById(userId);
        if (user != null) {
            //创建会话
            ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            //无标题
            if(StrUtil.isBlank(createDTO.getSessionTitle())){
                session.setSessionTitle("AI助手 - "+ DateUtil.format(LocalDateTime.now(),"MM-dd HH:mm"));
            }
            //插入会话到数据库
            consultationSessionMapper.insert(session);

            return session;
        } else {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

}}
