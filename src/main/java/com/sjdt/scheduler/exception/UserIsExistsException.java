package com.sjdt.scheduler.exception;

import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;

public class UserIsExistsException extends BizException {
    public UserIsExistsException() {
        super(ErrorCodeEnum.USER_IS_EXISTS);
    }
}
