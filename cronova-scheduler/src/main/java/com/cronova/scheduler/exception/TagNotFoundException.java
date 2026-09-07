package com.cronova.scheduler.exception;

import com.cronova.scheduler.common.BizException;
import com.cronova.scheduler.common.ErrorCodeEnum;

public class TagNotFoundException extends BizException {
    public TagNotFoundException() {
        super(ErrorCodeEnum.TAG_NOT_FOUND);
    }
}
