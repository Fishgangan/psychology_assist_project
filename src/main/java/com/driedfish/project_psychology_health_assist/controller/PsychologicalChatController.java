package com.driedfish.project_psychology_health_assist.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driedfish.project_psychology_health_assist.AIService.PsychologicalSupportService;
import com.driedfish.project_psychology_health_assist.AIService.StructOutPut;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationSessionCreateDTO;
import com.driedfish.project_psychology_health_assist.common.DTO.command.ConsultationStreamDTO;
import com.driedfish.project_psychology_health_assist.common.Result;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.service.ConsultationSessionService;
import com.driedfish.project_psychology_health_assist.util.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChatController {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private ConsultationSessionService consultationSessionService;

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO){
        //获取当前用户
        String token= JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        StructOutPut.StreamChatSession session = psychologicalSupportService.startSession(userId,createDTO);

        return Result.success(session);


    }
    //流式返回聊天结果
    @PostMapping(value="/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO){
        //获取当前用户
        String token= JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        if(userId==null){
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED)))
                    .build());
        }
        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(),streamDTO.getUserMessage())
                .map(Fragment->{
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.success(Map.of("content",Fragment,"type","normal")))).build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()))
                .delayElements(Duration.ofMillis(50));//添加延迟时间，避免流过快

}   
    //删除对话
    @DeleteMapping("/sessions/{sessionId}")
    public Result<String> deleteSession(@PathVariable String sessionId){
        //获取当前用户
        String token= JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        psychologicalSupportService.deleteSession(sessionId,userId);
        return Result.success("对话删除成功");
    }


    //分页获取对话列表
    @GetMapping("/sessions")
    public Result<StructOutPut.StreamChatSessionList> getSessionList(@RequestParam int pageNum, @RequestParam int pageSize){
        String token= JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        //获取会话列表
        StructOutPut.StreamChatSessionList sessionList = consultationSessionService.getSessionList(pageNum,pageSize,userId);
        //转换为分页结果
        return Result.success(sessionList);
    }

    //获取对话消息列表
    @GetMapping("/session/{sessionId}/messages")
    public Result<StructOutPut.StreamChatMessageList> getMessages(@PathVariable String sessionId,@RequestParam int pageNum, @RequestParam int pageSize){
        //获取当前用户
        String token= JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        if(userId==null){
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(consultationSessionService.getMessages(sessionId,userId,pageNum,pageSize));
    }
}
