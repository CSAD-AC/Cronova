package com.cronova.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cronova.scheduler.domain.po.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签Mapper接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
