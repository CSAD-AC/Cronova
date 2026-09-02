package com.sjdt.scheduler.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.sjdt.scheduler.domain.po.Role;
import com.sjdt.scheduler.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private RoleMapper roleMapper;

    /** 权限统一放行（返回 "*"）：当前仅依赖角色做鉴权 */
    @Override
    public List<String> getPermissionList(Object o, String s) {
        return List.of("*");
    }

    /** 返回该账号拥有的“角色”列表 */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Set<Role> roles = roleMapper.selectRolesByUserId((String) loginId);
        return roles.stream().map(Role::getName).collect(Collectors.toList());
    }
}