package com.company.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline")
public class PipelineEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long repoId;
    private String name;
    private String branch;
    private String triggerType;
    private String executorType;
    private String definitionJson;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
