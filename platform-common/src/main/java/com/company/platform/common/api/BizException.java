package com.company.platform.common.api;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BizException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, 40000, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, 40100, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, 40300, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, 40400, message);
    }

    public static BizException conflict(String message) {
        return new BizException(409, 40900, message);
    }

    public static BizException unavailable(String message) {
        return new BizException(503, 50300, message);
    }
}
