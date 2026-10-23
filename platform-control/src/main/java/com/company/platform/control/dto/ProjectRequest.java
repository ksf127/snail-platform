package com.company.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "项目 key 不能为空")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,31}", message = "项目 key 需以字母开头，长度 2-32，仅含字母数字_-")
        String key,
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 64, message = "项目名称最长 64 字符")
        String name,
        @Size(max = 500, message = "项目描述最长 500 字符")
        String description
) {
}
