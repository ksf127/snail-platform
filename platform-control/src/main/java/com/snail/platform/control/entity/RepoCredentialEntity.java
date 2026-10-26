package com.snail.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 仓库访问凭据，token 为 AES-GCM 密文。 */
@Data
@TableName("repo_credential")
public class RepoCredentialEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    private String username;
    private String tokenCipher;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
