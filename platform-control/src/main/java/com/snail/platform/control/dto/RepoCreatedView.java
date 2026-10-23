package com.snail.platform.control.dto;

import com.snail.platform.control.entity.RepoEntity;

/** 创建/绑定时返回一次 webhook secret，方便写入 Gitea；列表接口不返回 secret。 */
public record RepoCreatedView(RepoEntity repo, String webhookSecret) {
}
