package com.company.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("build_artifact")
public class BuildArtifactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private String fileName;
    private String objectKey;
    private Long size;
    private String sha256;
    private String contentType;
    private LocalDateTime createdAt;
}
