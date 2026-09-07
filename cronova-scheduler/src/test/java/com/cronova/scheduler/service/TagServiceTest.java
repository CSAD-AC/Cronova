package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Tag;
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
        Result<IPage<Tag>> result = tagService.getTagList(1, 10, null);
        
        Assertions.assertNotNull(result);
        Assertions.assertEquals(200, result.getCode());
        Assertions.assertNotNull(result.getData());
    }
    
    @Test
    void testCreateAndGetTag() {
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("这是一个测试标签")
                .build();
        
        Result<Tag> createResult = tagService.createTag(tag);
        
        Assertions.assertNotNull(createResult);
        Assertions.assertEquals(200, createResult.getCode());
        Assertions.assertNotNull(createResult.getData());
        
        Integer tagId = createResult.getData().getId();
        
        Result<Tag> getResult = tagService.getTagById(tagId);
        
        Assertions.assertNotNull(getResult);
        Assertions.assertEquals(200, getResult.getCode());
        Assertions.assertNotNull(getResult.getData());
        Assertions.assertEquals(tag.getName(), getResult.getData().getName());
    }
    
    @Test
    void testUpdateTag() {
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("原始描述")
                .build();
        
        Result<Tag> createResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createResult.getCode());
        
        Integer tagId = createResult.getData().getId();
        
        Tag updateTag = new Tag();
        updateTag.setName("更新后的标签-" + UUID.randomUUID().toString().substring(0, 8));
        updateTag.setDescription("更新后的描述");
        
        Result<Tag> updateResult = tagService.updateTag(tagId, updateTag);
        
        Assertions.assertNotNull(updateResult);
        Assertions.assertEquals(200, updateResult.getCode());
        Assertions.assertNotNull(updateResult.getData());
        Assertions.assertEquals(updateTag.getName(), updateResult.getData().getName());
        Assertions.assertEquals("更新后的描述", updateResult.getData().getDescription());
    }
    
    @Test
    void testDeleteTag() {
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("删除测试")
                .build();
        
        Result<Tag> createResult = tagService.createTag(tag);
        Assertions.assertEquals(200, createResult.getCode());
        
        Integer tagId = createResult.getData().getId();
        
        Result<Void> deleteResult = tagService.deleteTag(tagId);
        
        Assertions.assertNotNull(deleteResult);
        Assertions.assertEquals(200, deleteResult.getCode());
        
        Result<Tag> getResult = tagService.getTagById(tagId);
        Assertions.assertNotNull(getResult);
        Assertions.assertNotEquals(200, getResult.getCode());
    }
}
