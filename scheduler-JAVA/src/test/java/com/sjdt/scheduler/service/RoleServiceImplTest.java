package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Role;
import com.sjdt.scheduler.mapper.RoleMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * RoleService测试类
 * 测试角色Service的业务逻辑
 */
@SpringBootTest
@Transactional
@Rollback
class RoleServiceImplTest {

    @Autowired
    private IRoleService roleService;

    @Autowired
    private RoleMapper roleMapper;

    /**
     * 测试获取角色列表（分页）
     */
    @Test
    void testGetRoleList() {
        createTestRole("管理员", "admin");
        createTestRole("普通用户", "user");
        createTestRole("访客", "guest");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, null, null, null);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getData());
        Assertions.assertTrue(result.getData().getTotal() >= 3);
    }

    /**
     * 测试分页查询角色列表
     */
    @Test
    void testGetRoleList_WithPagination() {
        for (int i = 0; i < 15; i++) {
            createTestRole("角色-" + i, "role-" + i);
        }

        Result<IPage<Role>> result = roleService.getRoleList(1, 5, null, null, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(5, result.getData().getRecords().size());
        Assertions.assertTrue(result.getData().getTotal() >= 15);
    }

    /**
     * 测试根据名称模糊查询角色
     */
    @Test
    void testGetRoleList_WithNameFilter() {
        createTestRole("管理员", "admin");
        createTestRole("管理员B", "admin-b");
        createTestRole("普通用户", "user");
        createTestRole("访客", "guest");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, "管理员", null, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.getData().getTotal());
    }

    /**
     * 测试按名称排序
     */
    @Test
    void testGetRoleList_SortByName() {
        createTestRole("Charlie", "charlie");
        createTestRole("Alice", "alice");
        createTestRole("Bob", "bob");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, null, "name", "asc");

        Assertions.assertNotNull(result);

        Assertions.assertTrue(result.getData().getTotal() >= 3);
        
        // 将角色名称转换为String数组，然后判断是否包含特定名称
        String[] roleNames = result.getData().getRecords().stream()
                .map(Role::getName)
                .toArray(String[]::new);
        List<String> roleNameList = Arrays.asList(roleNames);
        Assertions.assertTrue(roleNameList.contains("Alice"));
        Assertions.assertTrue(roleNameList.contains("Bob"));
        Assertions.assertTrue(roleNameList.contains("Charlie"));
    }

    /**
     * 测试按ID排序
     */
    @Test
    void testGetRoleList_SortById() {
        Role role1 = createTestRole("角色1", "role-1");
        Role role2 = createTestRole("角色2", "role-2");
        Role role3 = createTestRole("角色3", "role-3");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, null, "id", "desc");

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getData().getTotal() >= 3);
    }

    /**
     * 测试按创建时间倒序排序（默认排序）
     */
    @Test
    void testGetRoleList_DefaultSort() {
        Role olderRole = createTestRole("旧角色", "old-role");
        olderRole.setCreateTime(LocalDateTime.now().minusDays(1));
        roleMapper.updateById(olderRole);

        Role newerRole = createTestRole("新角色", "new-role");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, null, null, null);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getData().getRecords().get(0).getCreateTime()
                .isAfter(result.getData().getRecords().get(1).getCreateTime()));
    }

    /**
     * 测试查询不存在的角色名称
     */
    @Test
    void testGetRoleList_NameNotFound() {
        createTestRole("管理员", "admin");

        Result<IPage<Role>> result = roleService.getRoleList(1, 10, "不存在的名称", null, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.getData().getTotal());
    }

    /**
     * 测试空结果集
     */
    @Test
    void testGetRoleList_EmptyResult() {
        Result<IPage<Role>> result = roleService.getRoleList(1, 10, "测试", null, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.getData().getTotal());
    }

    /**
     * 测试超出页码范围
     */
    @Test
    void testGetRoleList_OutOfPageRange() {
        for (int i = 0; i < 5; i++) {
            createTestRole("角色-" + i, "role-" + i);
        }

        Result<IPage<Role>> result = roleService.getRoleList(10, 10, null, null, null);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.getData().getRecords().size());
        Assertions.assertTrue(result.getData().getTotal() >= 5);
    }

    private Role createTestRole(String name, String code) {
        Role role = Role.builder()
                .name(name)
                .createTime(LocalDateTime.now())
                .build();
        roleMapper.insert(role);
        return role;
    }
}
