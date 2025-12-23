package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sjdt.scheduler.domain.po.AKSK;

/**
 * AKSK Mapper 接口
 * 提供 AKSK 认证信息的 CRUD 操作
 */
public interface AKSKMapper extends BaseMapper<AKSK> {

    /**
     * 根据 Access Key 查询 AKSK 信息
     * @param accessKey Access Key
     * @return AKSK 实体
     */
    AKSK selectByAccessKey(String accessKey);

}