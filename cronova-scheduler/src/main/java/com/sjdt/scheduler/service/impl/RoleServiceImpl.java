package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Role;
import com.sjdt.scheduler.mapper.RoleMapper;
import com.sjdt.scheduler.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 角色服务实现类
 */
@Service
@Slf4j
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {
    
    @Override
    public Result<IPage<Role>> getRoleList(int page, int size, String name, String sortBy, String sortDirection) {
        LambdaQueryWrapper<Role> queryWrapper = new LambdaQueryWrapper<>();
        
        if (name != null && !name.isEmpty()) {
            queryWrapper.like(Role::getName, name);
        }
        
        String validSortBy = (sortBy == null || sortBy.isEmpty()) ? "createTime" : sortBy;
        boolean isAsc = "asc".equalsIgnoreCase(sortDirection);
        
        if ("id".equals(validSortBy)) {
            queryWrapper.orderByAsc(Role::getId);
        } else if ("name".equals(validSortBy)) {
            queryWrapper.orderByAsc(Role::getName);
        } else {
            queryWrapper.orderByDesc(Role::getCreateTime);
        }
        
        if (isAsc && !"createTime".equals(validSortBy)) {
            queryWrapper.orderByAsc(Role::getCreateTime);
        } else if (!isAsc && !"createTime".equals(validSortBy)) {
            queryWrapper.orderByDesc(Role::getCreateTime);
        }
        
        IPage<Role> rolePage = this.page(new Page<>(page, size), queryWrapper);
        
        return Result.success(rolePage);
    }
}
