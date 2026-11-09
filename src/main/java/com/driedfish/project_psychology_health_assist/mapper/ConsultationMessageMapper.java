package com.driedfish.project_psychology_health_assist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driedfish.project_psychology_health_assist.common.DTO.response.SessionMessageCountDTO;
import com.driedfish.project_psychology_health_assist.common.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {
    //查询会话所有消息数量
    //改进原有的N+1问题
    /**
     * 批量查询会话的消息数量。
     * 注意：IDEA 可能会在 <foreach> 标签处报 "Unescaped xml character" 的红线。
     * 原因是 IDEA 用纯 XML 的规则去检查了动态 SQL，绝不能点 "Escape '<'"，
     * 否则会变成 '&lt;' 导致 MyBatis 解析失败。此处直接无视或禁用检查即可。
     */
    @Select("<script> " +
            "SELECT session_id AS sessionId,COUNT(*) AS cnt "+
            "FROM consultation_message WHERE session_id IN "+
            "<foreach collection='sessionIds' item='id' open='(' close=')' separator=','> " +
            "#{id} " +
            "</foreach> "+
            "GROUP BY session_id " +
            "</script>")
    List<SessionMessageCountDTO> getSessionMessageCounts(@Param("sessionIds") List<Long> sessionIds);


}
