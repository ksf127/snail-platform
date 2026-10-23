package com.company.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repo")
public class RepoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String provider;
    private String externalId;
    private String cloneUrl;
    private String defaultBranch;
    private String webhookSecret;
    private LocalDateTime createdAt;
}
