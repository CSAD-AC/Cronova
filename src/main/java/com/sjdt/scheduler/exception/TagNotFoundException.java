package com.sjdt.scheduler.exception;

import com.sjdt.scheduler.common.BizException;
import com.sjdt.scheduler.common.ErrorCodeEnum;

public class TagNotFoundException extends BizException {
    public TagNotFoundException() {
        super(ErrorCodeEnum.TAG_NOT_FOUND);
    }
}
