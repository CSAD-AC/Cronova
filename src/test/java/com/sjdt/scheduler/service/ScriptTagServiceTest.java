package com.sjdt.scheduler.service;

import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ScriptTagService测试类
 */
@SpringBootTest
@Transactional
@Rollback
class ScriptTagServiceTest {

    @Autowired
    private IScriptTagService scriptTagService;

    @Autowired
    private IScriptService scriptService;

    @Autowired
    private ITagService tagService;

    @Test
    void testAddTagToScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .build();
        Result<Tag> tagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, tagResult.getCode());
        Assertions.assertNotNull(tagResult.getData());

        // 为脚本添加标签
        Result<Void> addResult = scriptTagService.addTagToScript(script.getId(), tagResult.getData().getId());

        // 验证添加结果
        Assertions.assertEquals(200, addResult.getCode());
    }

    @Test
    void testBatchAddTagsToScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());
        Assertions.assertNotNull(tag1Result.getData());

        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());
        Assertions.assertNotNull(tag2Result.getData());

        // 批量添加标签
        List<Integer> tagIds = List.of(tag1Result.getData().getId(), tag2Result.getData().getId());
        Result<Void> addResult = scriptTagService.batchAddTagsToScript(script.getId(), tagIds);

        // 验证添加结果
        Assertions.assertEquals(200, addResult.getCode());
    }

    @Test
    void testGetTagIdsByScriptId() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());
        Assertions.assertNotNull(tag1Result.getData());

        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());
        Assertions.assertNotNull(tag2Result.getData());

        // 为脚本添加标签
        scriptTagService.addTagToScript(script.getId(), tag1Result.getData().getId());
        scriptTagService.addTagToScript(script.getId(), tag2Result.getData().getId());

        // 查询脚本标签ID列表
        Result<List<Integer>> tagIdsResult = scriptTagService.getTagIdsByScriptId(script.getId());

        // 验证查询结果
        Assertions.assertEquals(200, tagIdsResult.getCode());
        Assertions.assertNotNull(tagIdsResult.getData());
        Assertions.assertEquals(2, tagIdsResult.getData().size());
        Assertions.assertTrue(tagIdsResult.getData().contains(tag1Result.getData().getId()));
        Assertions.assertTrue(tagIdsResult.getData().contains(tag2Result.getData().getId()));
    }

    @Test
    void testRemoveTagFromScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .build();
        Result<Tag> tagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, tagResult.getCode());
        Assertions.assertNotNull(tagResult.getData());

        // 为脚本添加标签
        scriptTagService.addTagToScript(script.getId(), tagResult.getData().getId());

        // 移除标签
        Result<Void> removeResult = scriptTagService.removeTagFromScript(script.getId(), tagResult.getData().getId());

        // 验证移除结果
        Assertions.assertEquals(200, removeResult.getCode());
    }

    @Test
    void testClearTagsFromScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        // 创建测试标签1
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());
        Assertions.assertNotNull(tag1Result.getData());

        // 创建测试标签2
        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());
        Assertions.assertNotNull(tag2Result.getData());

        // 为脚本添加标签
        scriptTagService.addTagToScript(script.getId(), tag1Result.getData().getId());
        scriptTagService.addTagToScript(script.getId(), tag2Result.getData().getId());

        // 清空标签
        Result<Void> clearResult = scriptTagService.clearTagsFromScript(script.getId());

        // 验证清空结果
        Assertions.assertEquals(200, clearResult.getCode());

        // 验证标签已清空
        Result<List<Integer>> tagIdsResult = scriptTagService.getTagIdsByScriptId(script.getId());
        Assertions.assertEquals(200, tagIdsResult.getCode());
        Assertions.assertNotNull(tagIdsResult.getData());
        Assertions.assertTrue(tagIdsResult.getData().isEmpty());
    }
}
