package com.sjdt.scheduler.mapper;

import com.sjdt.scheduler.domain.po.Role;
import com.sjdt.scheduler.domain.po.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * RoleMapper测试类，包含用户角色关联操作测试
 */
@SpringBootTest
@Transactional
@Rollback
class RoleMapperTest {

    @Autowired
    private RoleMapper roleMapper;
    
    @Autowired
    private UserMapper userMapper;

    @Test
    void testSelectRolesByUserId() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser5")
                .nickname("测试用户5")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        
        // 为用户添加角色
        roleMapper.addRolesForUser(user.getId(), 1); // 假设角色ID 1 是普通用户角色
        roleMapper.addRolesForUser(user.getId(), 2); // 假设角色ID 2 是管理员角色
        
        // 查询用户角色
        Set<Role> roles = roleMapper.selectRolesByUserId(user.getId());
        
        // 验证查询结果
        Assertions.assertNotNull(roles);
        Assertions.assertTrue(roles.size() >= 2);
        
        // 清除用户角色
        roleMapper.clearUserRoles(user.getId());
        
        // 删除测试用户
        userMapper.deleteById(user.getId());
    }

    @Test
    void testAssignRoles() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser6")
                .nickname("测试用户6")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        
        // 为用户批量添加角色
        List<Integer> roleIds = List.of(1, 2);
        roleMapper.batchAddRolesForUser(user.getId(), roleIds);
        
        // 查询用户角色ID列表
        List<Integer> userRoleIds = roleMapper.selectRoleIdsByUserId(user.getId());
        
        // 验证查询结果
        Assertions.assertNotNull(userRoleIds);
        Assertions.assertEquals(2, userRoleIds.size());
        Assertions.assertTrue(userRoleIds.containsAll(roleIds));
        
        // 清除用户角色
        roleMapper.clearUserRoles(user.getId());
        
        // 验证角色已清除
        List<Integer> clearedRoleIds = roleMapper.selectRoleIdsByUserId(user.getId());
        Assertions.assertTrue(clearedRoleIds.isEmpty());
        
        // 删除测试用户
        userMapper.deleteById(user.getId());
    }
}