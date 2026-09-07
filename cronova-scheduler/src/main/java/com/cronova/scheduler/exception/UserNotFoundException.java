package com.cronova.scheduler.exception;

import com.cronova.scheduler.common.BizException;
import com.cronova.scheduler.common.ErrorCodeEnum;

public class UserNotFoundException extends BizException {
    public UserNotFoundException() {
        super(ErrorCodeEnum.USER_NOT_FOUND);
    }
}
