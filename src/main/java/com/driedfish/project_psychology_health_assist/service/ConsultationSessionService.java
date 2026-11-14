package com.driedfish.project_psychology_health_assist.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driedfish.project_psychology_health_assist.AIService.PsychologicalSupportService;
import com.driedfish.project_psychology_health_assist.AIService.StructOutPut;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.response.SessionMessageCountDTO;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationSession;
import com.driedfish.project_psychology_health_assist.common.entity.User;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationMessageMapper;
import com.driedfish.project_psychology_health_assist.mapper.ConsultationSessionMapper;
import com.driedfish.project_psychology_health_assist.mapper.UserMapper;
import com.driedfish.project_psychology_health_assist.util.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsultationSessionService {
    private final UserMapper userMapper;
    private final ConsultationSessionMapper consultationSessionMapper;
    private final ConsultationMessageMapper consultationMessageMapper;
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

    }
    //删除会话
    public void deleteSession(Long sessionId) {
        consultationSessionMapper.deleteById(sessionId);
    }
    //根据会话ID查询会话
    public ConsultationSession getSessionById(Long sessionId) {
        return consultationSessionMapper.selectById(sessionId);
    }
    //查询所有会话
    public StructOutPut.StreamChatSessionList getSessionList(int pageNum, int pageSize, Long userId) {


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
        queryWrapper.eq(ConsultationSession::getUserId, userId)
                .orderByAsc(ConsultationSession::getStartedAt);
        Page<ConsultationSession> resultPage = consultationSessionMapper.selectPage(page, queryWrapper);
        //返回分页结果
        List<ConsultationSession> sessionList = resultPage.getRecords();
        //如果会话列表为空，直接空返回,避免sql in 语句空值问题
        if(sessionList.isEmpty()){
            return new StructOutPut.StreamChatSessionList(
                    List.of(),
                    0L);
        }
        //批量查询会话的消息数量
        List<Long> sessionIds=sessionList
                .stream()
                .map(ConsultationSession::getId)
                .filter(Objects::nonNull)
                .toList();
        List<SessionMessageCountDTO> msgCountList=consultationMessageMapper.getSessionMessageCounts(sessionIds);
        //利用Map根据会话ID查询消息数量
        Map<Long,Long> msgCountMap=msgCountList.stream()
                .collect(Collectors.toMap(
                        SessionMessageCountDTO::getSessionId,
                        SessionMessageCountDTO::getCnt,
                        //如果会话ID重复，取第一个值(防止脏数据问题)
                        (a,b)->a));
        List<StructOutPut.StreamChatSession> volist = sessionList.stream()
                .map(session -> {
                    //N+1查询优化
                    //Long msgCount=consultationMessageMapper.selectCount(new LambdaQueryWrapper<ConsultationMessage>().eq(ConsultationMessage::getSessionId, session.getId()));
                    return new StructOutPut.StreamChatSession(
                        "session_"+session.getId().toString(),
                        userId,
                        session.getSessionTitle(),
                        //转换为东八区时间戳
                        session.getStartedAt().toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                        session.getStartedAt().plusDays(1).toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                        msgCountMap.getOrDefault(session.getId(),0L),
                        "active")
                ;}).toList();
        return new StructOutPut.StreamChatSessionList(
                volist,
                resultPage.getTotal());

    }
    //查询会话所有消息
    public StructOutPut.StreamChatMessageList getMessages(String sessionId,int pageNum, int pageSize, Long userId) {
        //分页查询
        if(pageSize>100){
            pageSize=100;
        }
        if(pageNum<1){
            pageNum=1;
        }
        Page<ConsultationMessage> page = new Page<>(pageNum, pageSize);
        //根据会话ID查询消息
        Long sessionIdLong = SessionUtils.extractSessionId(sessionId);
        //检查会话是否属于当前用户
        checkSessionOwner(sessionIdLong,userId);
        LambdaQueryWrapper<ConsultationMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId, sessionIdLong);
        //根据时间排序(旧的在前)
        queryWrapper.orderByAsc(ConsultationMessage::getCreatedAt);
        Page<ConsultationMessage> resultPage = consultationMessageMapper.selectPage(page, queryWrapper);
        return new StructOutPut.StreamChatMessageList(
                resultPage.getRecords(),
                resultPage.getTotal());
    }
    //检查会话是否属于当前用户
    public void checkSessionOwner(Long sessionId, Long userId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if(session==null){
            throw new BusinessException(ResultCode.SESSION_NOT_EXIST);
        }
        if(!session.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.SESSION_NOT_PERMISSION);
        }
    }}
