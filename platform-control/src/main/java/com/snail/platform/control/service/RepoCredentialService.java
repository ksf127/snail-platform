package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.control.entity.RepoCredentialEntity;
import com.snail.platform.control.mapper.RepoCredentialMapper;
import com.snail.platform.control.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 仓库访问凭据：token 加密存储，只在下发构建上下文时解密。 */
@Service
@RequiredArgsConstructor
public class RepoCredentialService {

    private final RepoCredentialMapper credentialMapper;
    private final SecretCipher secretCipher;

    public boolean exists(Long repoId) {
        if (repoId == null) {
            return false;
        }
        Long count = credentialMapper.selectCount(new LambdaQueryWrapper<RepoCredentialEntity>()
                .eq(RepoCredentialEntity::getRepoId, repoId));
        return count != null && count > 0;
    }

    /** 新增或覆盖仓库凭据；token 为空时抛错，避免静默清空。 */
    public void save(Long repoId, String username, String token) {
        if (!StringUtils.hasText(token)) {
            throw com.snail.platform.common.api.BizException.badRequest("仓库令牌不能为空");
        }
        RepoCredentialEntity entity = credentialMapper.selectOne(new LambdaQueryWrapper<RepoCredentialEntity>()
                .eq(RepoCredentialEntity::getRepoId, repoId).last("limit 1"));
        boolean created = entity == null;
        if (created) {
            entity = new RepoCredentialEntity();
            entity.setRepoId(repoId);
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUsername(StringUtils.hasText(username) ? username.trim() : null);
        entity.setTokenCipher(secretCipher.encrypt(token.trim()));
        entity.setUpdatedAt(LocalDateTime.now());
        if (created) {
            credentialMapper.insert(entity);
        } else {
            credentialMapper.updateById(entity);
        }
    }

    public void saveIfAbsent(Long repoId, String username, String token) {
        if (repoId == null || !StringUtils.hasText(token) || exists(repoId)) {
            return;
        }
        save(repoId, username, token);
    }

    public void delete(Long repoId) {
        credentialMapper.delete(new LambdaQueryWrapper<RepoCredentialEntity>()
                .eq(RepoCredentialEntity::getRepoId, repoId));
    }

    /** 供内部构建上下文与仓库文件读取使用，返回明文。 */
    public Credential find(Long repoId) {
        if (repoId == null) {
            return null;
        }
        RepoCredentialEntity entity = credentialMapper.selectOne(new LambdaQueryWrapper<RepoCredentialEntity>()
                .eq(RepoCredentialEntity::getRepoId, repoId).last("limit 1"));
        if (entity == null) {
            return null;
        }
        return new Credential(entity.getUsername(), secretCipher.decrypt(entity.getTokenCipher()));
    }

    public record Credential(String username, String token) {
    }
}
