package com.cronova.scheduler.common;

import lombok.Getter;

/**
 * 自定义业务异常基类
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCodeEnum errorCodeEnum) {
        super(errorCodeEnum.getMessage());
        this.code = errorCodeEnum.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
