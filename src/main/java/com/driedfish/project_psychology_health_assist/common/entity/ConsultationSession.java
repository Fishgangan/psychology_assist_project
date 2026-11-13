package com.driedfish.project_psychology_health_assist.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Data
@TableName("consultation_session")
@Builder
public class ConsultationSession {
    // 会话ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 会话标题
    private String sessionTitle;

    // 开始时间
    private LocalDateTime startedAt;

    // 最后一次情绪分析结果(JSON格式)
    private String lastEmotionAnalysis;

    // 最后一次情绪分析更新时间
    private LocalDateTime lastEmotionUpdatedAt;


}
