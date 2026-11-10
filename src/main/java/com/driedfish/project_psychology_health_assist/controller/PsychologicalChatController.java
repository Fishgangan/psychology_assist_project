package com.driedfish.project_psychology_health_assist.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driedfish.project_psychology_health_assist.AIService.PsychologicalSupportService;
import com.driedfish.project_psychology_health_assist.AIService.StructOutPut;
import com.driedfish.project_psychology_health_assist.annotation.CheckSessionOwner;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationStreamDTO;
import com.driedfish.project_psychology_health_assist.common.Result;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import com.driedfish.project_psychology_health_assist.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChatController {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private ConsultationSessionService consultationSessionService;
    //创建会话
    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO){
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        StructOutPut.StreamChatSession session = psychologicalSupportService.startSession(userId,createDTO);

        return Result.success(session);


    }
    //流式返回聊天结果

    @CheckSessionOwner
    @PostMapping(value="/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO){
        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(),streamDTO.getUserMessage())
                .map(fragment->{
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.success(Map.of("content",fragment,"type","normal")))).build();
                })
                //ai中途处理异常
                .onErrorResume(error->{
                    //打印异常信息
                    log.error("ai中途处理异常",error);
                    return Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(JSONUtil.toJsonStr(Result.error(ResultCode.SYSTEM_ERROR)))
                        .build());})
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()))
                .delayElements(Duration.ofMillis(50));//添加延迟时间，避免流过快

}   
    //删除对话
    @CheckSessionOwner
    @DeleteMapping("/sessions/{sessionId}")
    public Result<String> deleteSession(@PathVariable String sessionId){
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        psychologicalSupportService.deleteSession(sessionId);
        return Result.success("对话删除成功");
    }


    //分页获取对话列表
    @GetMapping("/sessions")
    public Result<StructOutPut.StreamChatSessionList> getSessionList(@RequestParam int pageNum, @RequestParam int pageSize){
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        //获取会话列表
        StructOutPut.StreamChatSessionList sessionList = consultationSessionService.getSessionList(pageNum,pageSize,userId);
        //转换为分页结果
        return Result.success(sessionList);
    }

    //获取对话消息列表
    @CheckSessionOwner
    @GetMapping("/session/{sessionId}/messages")
    public Result<StructOutPut.StreamChatMessageList> getMessages(@PathVariable String sessionId,@RequestParam int pageNum, @RequestParam int pageSize){
        //获取当前用户
        Long userId = SecurityUtils.getUserId();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(consultationSessionService.getMessages(sessionId,pageNum,pageSize));
    }
}
