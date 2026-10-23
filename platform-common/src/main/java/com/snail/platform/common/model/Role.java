package com.snail.platform.common.model;

/** 平台角色。ADMIN 可删除，DEVELOPER 可创建/触发，VIEWER 只读。 */
public enum Role {
    ADMIN,
    DEVELOPER,
    VIEWER
}
