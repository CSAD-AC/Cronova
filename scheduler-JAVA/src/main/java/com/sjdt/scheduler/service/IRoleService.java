package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Role;

/**
 * 角色服务接口
 */
public interface IRoleService extends IService<Role> {
    
    /**
     * 分页查询角色列表
     * @param page 页码
     * @param size 每页大小
     * @param name 角色名称模糊查询
     * @param sortBy 排序字段，支持id/name/createTime
     * @param sortDirection 排序方向 asc/desc
     * @return 角色分页列表
     */
    Result<IPage<Role>> getRoleList(int page, int size, String name, String sortBy, String sortDirection);
}
