package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UserMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testSelectById() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser")
                .nickname("测试用户")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 插入用户
        userMapper.insert(user);
        
        // 根据ID查询
        User foundUser = userMapper.selectById(user.getId());
        
        // 验证查询结果
        Assertions.assertNotNull(foundUser);
        Assertions.assertEquals(user.getUsername(), foundUser.getUsername());
        
        // 删除测试用户
        userMapper.deleteById(user.getId());
    }

    @Test
    void testSelectByUsername() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser2")
                .nickname("测试用户2")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 插入用户
        userMapper.insert(user);
        
        // 根据用户名查询
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, "testuser2");
        User foundUser = userMapper.selectOne(queryWrapper);
        
        // 验证查询结果
        Assertions.assertNotNull(foundUser);
        Assertions.assertEquals(user.getUsername(), foundUser.getUsername());
        
        // 删除测试用户
        userMapper.deleteById(user.getId());
    }

    @Test
    void testUpdate() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser3")
                .nickname("测试用户3")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 插入用户
        userMapper.insert(user);
        
        // 更新用户信息
        user.setNickname("更新后的测试用户");
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 查询更新后的用户
        User updatedUser = userMapper.selectById(user.getId());
        
        // 验证更新结果
        Assertions.assertNotNull(updatedUser);
        Assertions.assertEquals("更新后的测试用户", updatedUser.getNickname());
        
        // 删除测试用户
        userMapper.deleteById(user.getId());
    }

    @Test
    void testDelete() {
        // 创建测试用户
        User user = User.builder()
                .username("testuser4")
                .nickname("测试用户4")
                .password("password123")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 插入用户
        userMapper.insert(user);
        
        // 删除用户
        int result = userMapper.deleteById(user.getId());
        
        // 验证删除结果
        Assertions.assertEquals(1, result);
        
        // 验证用户已被删除
        User deletedUser = userMapper.selectById(user.getId());
        Assertions.assertNull(deletedUser);
    }
}