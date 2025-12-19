package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sjdt.scheduler.domain.po.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签Mapper接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
