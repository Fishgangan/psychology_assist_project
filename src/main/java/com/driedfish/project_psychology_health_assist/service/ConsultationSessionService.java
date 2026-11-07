package com.driedfish.project_psychology_health_assist.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driedfish.project_psychology_health_assist.AIService.PsychologicalSupportService;
import com.driedfish.project_psychology_health_assist.AIService.StructOutPut;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationMessageMapper;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationSessionMapper;
import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;
    //创建会话
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


}public void deleteSession(Long sessionId,Long userId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_EXIST);
        }
        if(!session.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.SESSION_NOT_PERMISSION);}
        consultationSessionMapper.deleteById(sessionId);
    }
public StructOutPut.StreamChatSessionList getSessionList(int pageNum, int pageSize, Long userId) {
        //查询所有会话

        //分页查询
        if(pageSize>100){
            pageSize=100;
        }
        if(pageNum<1){
            pageNum=1;
        }
        Page<ConsultationSession> page = new Page<>(pageNum, pageSize);
        //查询会话
        //根据用户ID查询会话
        LambdaQueryWrapper<ConsultationSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationSession::getUserId, userId);
        Page<ConsultationSession> resultPage = consultationSessionMapper.selectPage(page, queryWrapper);
        //返回分页结果
        List<ConsultationSession> sessionList = resultPage.getRecords();
        List<StructOutPut.StreamChatSession> volist = sessionList.stream()
                .map(session -> {
                    Long msgCount=consultationMessageMapper.selectCount(new LambdaQueryWrapper<ConsultationMessage>().eq(ConsultationMessage::getSessionId, session.getId()));
                    return new StructOutPut.StreamChatSession(
                        "session_"+session.getId().toString(),
                        userId,
                        session.getSessionTitle(),
                        //转换为东八区时间戳
                        session.getStartedAt().toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                        session.getStartedAt().plusDays(1).toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                        msgCount.intValue(),
                        "active")
                ;}).toList();
        return new StructOutPut.StreamChatSessionList(
                volist,
                resultPage.getTotal());

    }
    public StructOutPut.StreamChatMessageList getMessages(String sessionId, Long userId,int pageNum, int pageSize) {
        Long sessionIdLong = extractSessionId(sessionId);
        //验证会话是否存在
        ConsultationSession session = consultationSessionMapper.selectById(sessionIdLong);
        if(session==null){
            throw new BusinessException(ResultCode.SESSION_NOT_EXIST);
        }
        if(!session.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.SESSION_NOT_PERMISSION);
        }
        //分页查询
        if(pageSize>100){
            pageSize=100;
        }
        if(pageNum<1){
            pageNum=1;
        }
        Page<ConsultationMessage> page = new Page<>(pageNum, pageSize);
        //根据会话ID查询消息
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionIdLong);
        //根据时间排序(旧的在前)
        queryWrapper.orderByAsc(ConsultationMessage::getCreatedAt);
        Page<ConsultationMessage> resultPage = consultationMessageMapper.selectPage(page, queryWrapper);
        return new StructOutPut.StreamChatMessageList(
                resultPage.getRecords(),
                resultPage.getTotal());
    }
    //获取sessionId
    public Long extractSessionId(String sessionId) {
        if(sessionId==null||!sessionId.startsWith("session_")){
            throw new BusinessException(ResultCode.SESSION_ID_FORMAT_ERROR);
        }
            String sessionIdStr = sessionId.substring("session_".length());
            //验证sessionId是否为数字
            if(!sessionIdStr.matches("\\d+")){
                throw new BusinessException(ResultCode.SESSION_ID_FORMAT_ERROR);
            }
            return Long.parseLong(sessionIdStr);


    }}
