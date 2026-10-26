package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    private String side;
    private String definitionJson;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 分支归属团队，来自 project_branch，仅用于展示与筛选，不落库。 */
    @TableField(exist = false)
    private String team;
}
