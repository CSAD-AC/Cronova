package com.cronova.scheduler.exception;

import com.cronova.scheduler.common.BizException;
import com.cronova.scheduler.common.ErrorCodeEnum;

public class TaskNotFoundException extends BizException {
    public TaskNotFoundException() {
        super(ErrorCodeEnum.USER_NOT_FOUND);
    }
}