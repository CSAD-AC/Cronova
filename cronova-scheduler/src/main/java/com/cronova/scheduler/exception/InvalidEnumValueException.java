package com.cronova.scheduler.exception;

import com.cronova.scheduler.common.BizException;
import com.cronova.scheduler.common.ErrorCodeEnum;

public class InvalidEnumValueException extends BizException {
    public InvalidEnumValueException() {
        super(ErrorCodeEnum.INVALID_ENUM_VALUE);
    }
}
