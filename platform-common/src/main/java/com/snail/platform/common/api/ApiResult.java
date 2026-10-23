package com.snail.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一返回结构。code == 0 表示成功，其余为业务/权限错误码。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResult<T>(int code, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "OK", data);
    }

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(0, "OK", null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
