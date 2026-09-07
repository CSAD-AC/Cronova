package com.cronova.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.po.Tag;
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
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .createTime(LocalDateTime.now())
                .build();
        
        tagMapper.insert(tag);
        
        Tag foundTag = tagMapper.selectById(tag.getId());
        
        Assertions.assertNotNull(foundTag);
        Assertions.assertEquals(tag.getName(), foundTag.getName());
    }
    
    @Test
    void testSelectByName() {
        Tag tag = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .createTime(LocalDateTime.now())
                .build();
        
        tagMapper.insert(tag);
        
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Tag::getName, tag.getName());
        Tag foundTag = tagMapper.selectOne(queryWrapper);
        
        Assertions.assertNotNull(foundTag);
        Assertions.assertEquals(tag.getName(), foundTag.getName());
    }
    
    @Test
    void testUpdate() {
        Tag tag = Tag.builder()
                .name("测试标签3-" + UUID.randomUUID().toString().substring(0, 8))
                .description("原始描述")
                .createTime(LocalDateTime.now())
                .build();
        
        tagMapper.insert(tag);
        
        tag.setDescription("更新后的描述");
        tagMapper.updateById(tag);
        
        Tag updatedTag = tagMapper.selectById(tag.getId());
        
        Assertions.assertNotNull(updatedTag);
        Assertions.assertEquals("更新后的描述", updatedTag.getDescription());
    }
    
    @Test
    void testDelete() {
        Tag tag = Tag.builder()
                .name("测试标签4-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .createTime(LocalDateTime.now())
                .build();
        
        tagMapper.insert(tag);
        
        int result = tagMapper.deleteById(tag.getId());
        
        Assertions.assertEquals(1, result);
        
        Tag deletedTag = tagMapper.selectById(tag.getId());
        Assertions.assertNull(deletedTag);
    }
}
