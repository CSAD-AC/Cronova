package com.sjdt.scheduler.exception;

import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;

public class InvalidEnumValueException extends BizException {
    public InvalidEnumValueException() {
        super(ErrorCodeEnum.INVALID_ENUM_VALUE);
    }
}
