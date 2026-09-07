package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sjdt.scheduler.domain.po.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    Set<Role> selectRolesByUserId(String userId);

    Integer addRolesForUser(String userId, Integer roleId);

    /**
     * 清除用户的所有角色
     * @param userId 用户ID
     * @return 影响行数
     */
    Integer clearUserRoles(String userId);

    /**
     * 批量添加角色给用户
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 影响行数
     */
    Integer batchAddRolesForUser(@Param("userId") String userId, @Param("roleIds") List<Integer> roleIds);

    /**
     * 获取用户的角色ID列表
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Integer> selectRoleIdsByUserId(String userId);
}
