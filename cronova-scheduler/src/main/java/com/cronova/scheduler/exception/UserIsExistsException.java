package com.cronova.scheduler.exception;

import com.cronova.scheduler.common.BizException;
import com.cronova.scheduler.common.ErrorCodeEnum;

public class UserIsExistsException extends BizException {
    public UserIsExistsException() {
        super(ErrorCodeEnum.USER_IS_EXISTS);
    }
}
