package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.UserUpdateDTO;
import com.cronova.scheduler.domain.po.User;
import com.cronova.scheduler.domain.vo.UserVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UserService测试类
 */
@SpringBootTest
@Transactional
@Rollback
class UserServiceTest {

    @Autowired
    private IUserService userService;

    @Test
    void testGetUserList() {
        // 测试分页查询用户列表
        Result<IPage<UserVO>> result = userService.getUserList(1, 10, null);
        
        // 验证查询结果
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
    }

    @Test
    void testCreateAndGetUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // 创建测试用户
        User user = User.builder()
                .username("testuser7-" + uniqueId)
                .nickname("测试用户7-" + uniqueId)
                .password("password123")
                .build();
        
        // 调用createUser方法
        Result<UserVO> createResult = userService.createUser(user);
        
        // 验证创建结果
        Assertions.assertNotNull(createResult);
        Assertions.assertEquals(200, createResult.getCode());
        Assertions.assertNotNull(createResult.getData());
        
        String userId = createResult.getData().getId();
        
        // 测试根据ID查询用户
        Result<UserVO> getResult = userService.getUserById(userId);
        
        // 验证查询结果
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(200, getResult.getCode());
        Assertions.assertNotNull(getResult.getData());
        Assertions.assertEquals("testuser7-" + uniqueId, getResult.getData().getUsername());
    }

    @Test
    void testUpdateUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // 创建测试用户
        User user = User.builder()
                .username("testuser8-" + uniqueId)
                .nickname("测试用户8-" + uniqueId)
                .password("password123")
                .build();
        
        Result<UserVO> createResult = userService.createUser(user);
        Assertions.assertEquals(200, createResult.getCode());
        
        String userId = createResult.getData().getId();
        
        // 测试更新用户信息
        UserUpdateDTO updateDTO = new UserUpdateDTO();
        updateDTO.setNickname("更新后的测试用户8-" + uniqueId);
        updateDTO.setPassword("newpassword123");
        
        Result<UserVO> updateResult = userService.updateUser(userId, updateDTO);
        
        // 验证更新结果
        Assertions.assertNotNull(updateResult);
        Assertions.assertEquals(200, updateResult.getCode());
        Assertions.assertNotNull(updateResult.getData());
    }

    @Test
    void testAssignAndGetRoles() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // 创建测试用户
        User user = User.builder()
                .username("testuser9-" + uniqueId)
                .nickname("测试用户9-" + uniqueId)
                .password("password123")
                .build();
        
        Result<UserVO> createResult = userService.createUser(user);
        Assertions.assertEquals(200, createResult.getCode());
        
        String userId = createResult.getData().getId();
        
        // 测试为用户分配角色
        List<Integer> roleIds = List.of(1, 2); // 假设角色ID 1和2是有效的角色
        Result<Void> assignResult = userService.assignRoles(userId, roleIds);
        
        // 验证角色分配结果
        Assertions.assertNotNull(assignResult);
        Assertions.assertEquals(200, assignResult.getCode());
        
        // 测试获取用户角色
        Result<List<Integer>> getRolesResult = userService.getUserRoles(userId);
        
        // 验证获取角色结果
        Assertions.assertNotNull(getRolesResult);
        Assertions.assertEquals(200, getRolesResult.getCode());
        Assertions.assertNotNull(getRolesResult.getData());
        Assertions.assertEquals(2, getRolesResult.getData().size());
    }

    @Test
    void testDeleteUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        // 创建测试用户
        User user = User.builder()
                .username("testuser10-" + uniqueId)
                .nickname("测试用户10-" + uniqueId)
                .password("password123")
                .build();
        
        Result<UserVO> createResult = userService.createUser(user);
        Assertions.assertEquals(200, createResult.getCode());
        
        String userId = createResult.getData().getId();
        
        // 测试删除用户
        Result<Void> deleteResult = userService.deleteUser(userId);
        
        // 验证删除结果
        Assertions.assertNotNull(deleteResult);
        Assertions.assertEquals(200, deleteResult.getCode());
        
        // 验证用户已被删除
        Result<UserVO> getResult = userService.getUserById(userId);
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(1003, getResult.getCode());
    }
}