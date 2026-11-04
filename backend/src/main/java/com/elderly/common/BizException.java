package com.elderly.common;

import lombok.Getter;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转换为 Result(code=500)
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码，默认 500 */
    private final Integer code;

    public BizException(String message) {
        this(500, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
