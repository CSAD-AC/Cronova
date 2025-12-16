package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sjdt.scheduler.domain.po.Script;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScriptMapper extends BaseMapper<Script> {
}