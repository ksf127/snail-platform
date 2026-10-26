package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("deployment")
public class DeploymentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long runId;
    private String environment;
    /** 发布时固化的 K8s namespace，来自项目环境配置。 */
    private String namespace;
    private String status;
    private String image;
    private Integer replicas;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
