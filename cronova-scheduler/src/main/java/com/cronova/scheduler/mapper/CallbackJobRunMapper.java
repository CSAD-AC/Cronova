package com.cronova.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cronova.scheduler.domain.po.CallbackJobRun;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallbackJobRunMapper extends BaseMapper<CallbackJobRun> {
}
