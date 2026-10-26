package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 项目分支归属：分支由哪个团队/负责人维护。 */
@Data
@TableName("project_branch")
public class ProjectBranchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String branch;
    private String team;
    private Long ownerId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
