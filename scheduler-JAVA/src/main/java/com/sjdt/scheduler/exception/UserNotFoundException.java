package com.sjdt.scheduler.exception;

import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;

public class UserNotFoundException extends BizException {
    public UserNotFoundException() {
        super(ErrorCodeEnum.USER_NOT_FOUND);
    }
}
