package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 项目级构建变量/凭据，值为 AES-GCM 密文。 */
@Data
@TableName("project_secret")
public class ProjectSecretEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String valueCipher;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
