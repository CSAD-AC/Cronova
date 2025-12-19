package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TagMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class TagMapperTest {
    
    @Autowired
    private TagMapper tagMapper;
    
    @Test
    void testSelectById() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        
        // 插入标签
        tagMapper.insert(tag);
        
        // 根据ID查询
        Tag foundTag = tagMapper.selectById(tag.getId());
        
        // 验证查询结果
        Assertions.assertNotNull(foundTag);
        Assertions.assertEquals(tag.getName(), foundTag.getName());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testSelectByName() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        
        // 插入标签
        tagMapper.insert(tag);
        
        // 根据名称查询
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Tag::getName, tag.getName());
        Tag foundTag = tagMapper.selectOne(queryWrapper);
        
        // 验证查询结果
        Assertions.assertNotNull(foundTag);
        Assertions.assertEquals(tag.getName(), foundTag.getName());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testUpdate() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签3-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        
        // 插入标签
        tagMapper.insert(tag);
        
        // 更新标签信息
        tag.setVersion("2.0");
        tagMapper.updateById(tag);
        
        // 查询更新后的标签
        Tag updatedTag = tagMapper.selectById(tag.getId());
        
        // 验证更新结果
        Assertions.assertNotNull(updatedTag);
        Assertions.assertEquals("2.0", updatedTag.getVersion());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testDelete() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签4-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        
        // 插入标签
        tagMapper.insert(tag);
        
        // 删除标签
        int result = tagMapper.deleteById(tag.getId());
        
        // 验证删除结果
        Assertions.assertEquals(1, result);
        
        // 验证标签已被删除
        Tag deletedTag = tagMapper.selectById(tag.getId());
        Assertions.assertNull(deletedTag);
    }
}