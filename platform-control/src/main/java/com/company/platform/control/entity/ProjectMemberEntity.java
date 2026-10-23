package com.company.platform.control.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("project_member")
public class ProjectMemberEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    private String role;
}
