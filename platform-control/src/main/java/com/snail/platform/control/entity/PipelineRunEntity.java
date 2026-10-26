package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_run")
public class PipelineRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Integer buildNo;
    private String status;
    private String commitSha;
    /** 触发时解析出的流水线定义快照：配置在仓库时，保证执行内容与触发时一致。 */
    private String resolvedDefinition;
    private String branch;
    private String triggerType;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String logObject;
    private String errorMessage;
    private LocalDateTime createdAt;
}
