package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * TagService测试类
 */
@SpringBootTest
@Transactional
@Rollback
class TagServiceTest {
    
    @Autowired
    private ITagService tagService;
    
    @Test
    void testGetTagList() {
        // 测试分页查询标签列表
        Result<IPage<Tag>> result = tagService.getTagList(1, 10, null);
        
        // 验证查询结果
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
    }
    
    @Test
    void testCreateAndGetTag() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签11-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        
        // 调用createTag方法
        Result<Tag> createResult = tagService.createTag(tag);
        
        // 验证创建结果
        Assertions.assertNotNull(createResult);
        Assertions.assertEquals(200, createResult.getCode());
        Assertions.assertNotNull(createResult.getData());
        
        Integer tagId = createResult.getData().getId();
        
        // 测试根据ID查询标签
        Result<Tag> getResult = tagService.getTagById(tagId);
        
        // 验证查询结果
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(200, getResult.getCode());
        Assertions.assertNotNull(getResult.getData());
        Assertions.assertEquals(tag.getName(), getResult.getData().getName());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testUpdateTag() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签12-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        
        Result<Tag> createResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createResult.getCode());
        
        Integer tagId = createResult.getData().getId();
        
        // 测试更新标签信息
        Tag updateTag = new Tag();
        updateTag.setName("更新后的测试标签12-" + UUID.randomUUID().toString().substring(0, 8));
        updateTag.setOperator(">=");
        updateTag.setVersion("2.0");
        
        Result<Tag> updateResult = tagService.updateTag(tagId, updateTag);
        
        // 验证更新结果
        Assertions.assertNotNull(updateResult);
        Assertions.assertEquals(200, updateResult.getCode());
        Assertions.assertNotNull(updateResult.getData());
        Assertions.assertEquals(updateTag.getName(), updateResult.getData().getName());
        Assertions.assertEquals(">=", updateResult.getData().getOperator());
        Assertions.assertEquals("2.0", updateResult.getData().getVersion());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
    
    @Test
    void testDeleteTag() {
        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签13-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("=")
                .version("1.0")
                .build();
        
        Result<Tag> createResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createResult.getCode());
        
        Integer tagId = createResult.getData().getId();
        
        // 测试删除标签
        Result<Void> deleteResult = tagService.deleteTag(tagId);
        
        // 验证删除结果
        Assertions.assertNotNull(deleteResult);
        Assertions.assertEquals(200, deleteResult.getCode());
        
        // 验证标签已被删除
        Result<Tag> getResult = tagService.getTagById(tagId);
        Assertions.assertNotNull(getResult);
        Assertions.assertNotEquals(200, getResult.getCode());
        
        // 不再需要手动清理测试数据，由@Rollback自动回滚
    }
}