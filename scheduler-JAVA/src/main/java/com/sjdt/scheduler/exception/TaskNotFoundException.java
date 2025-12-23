package com.sjdt.scheduler.exception;

import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;

public class TaskNotFoundException extends BizException {
    public TaskNotFoundException() {
        super(ErrorCodeEnum.USER_NOT_FOUND);
    }
}