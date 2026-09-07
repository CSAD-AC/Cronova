package com.cronova.scheduler.service;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.po.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

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
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("这是一个测试标签")
                .build();
        Result<Tag> tagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, tagResult.getCode());
        Assertions.assertNotNull(tagResult.getData());

        Result<Void> addResult = scriptTagService.addTagToScript(script.getId(), tagResult.getData().getId());
        Assertions.assertEquals(200, addResult.getCode());
    }

    @Test
    void testBatchAddTagsToScript() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());
        Assertions.assertNotNull(tag1Result.getData());

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());
        Assertions.assertNotNull(tag2Result.getData());

        List<Integer> tagIds = List.of(tag1Result.getData().getId(), tag2Result.getData().getId());
        Result<Void> addResult = scriptTagService.batchAddTagsToScript(script.getId(), tagIds);
        Assertions.assertEquals(200, addResult.getCode());
    }

    @Test
    void testGetTagIdsByScriptId() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());
        Assertions.assertNotNull(tag1Result.getData());

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());
        Assertions.assertNotNull(tag2Result.getData());

        scriptTagService.addTagToScript(script.getId(), tag1Result.getData().getId());
        scriptTagService.addTagToScript(script.getId(), tag2Result.getData().getId());

        Result<List<Integer>> tagIdsResult = scriptTagService.getTagIdsByScriptId(script.getId());
        Assertions.assertEquals(200, tagIdsResult.getCode());
        Assertions.assertNotNull(tagIdsResult.getData());
        Assertions.assertEquals(2, tagIdsResult.getData().size());
    }

    @Test
    void testRemoveTagFromScript() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签")
                .build();
        Result<Tag> tagResult = tagService.createTag(tag);
        Assertions.assertEquals(200, tagResult.getCode());
        Assertions.assertNotNull(tagResult.getData());

        scriptTagService.addTagToScript(script.getId(), tagResult.getData().getId());
        Result<Void> removeResult = scriptTagService.removeTagFromScript(script.getId(), tagResult.getData().getId());
        Assertions.assertEquals(200, removeResult.getCode());
    }

    @Test
    void testClearTagsFromScript() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptService.save(script);

        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        Result<Tag> tag1Result = tagService.createTag(tag1);
        Assertions.assertEquals(200, tag1Result.getCode());

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        Result<Tag> tag2Result = tagService.createTag(tag2);
        Assertions.assertEquals(200, tag2Result.getCode());

        scriptTagService.addTagToScript(script.getId(), tag1Result.getData().getId());
        scriptTagService.addTagToScript(script.getId(), tag2Result.getData().getId());

        Result<Void> clearResult = scriptTagService.clearTagsFromScript(script.getId());
        Assertions.assertEquals(200, clearResult.getCode());

        Result<List<Integer>> tagIdsResult = scriptTagService.getTagIdsByScriptId(script.getId());
        Assertions.assertEquals(200, tagIdsResult.getCode());
        Assertions.assertNotNull(tagIdsResult.getData());
        Assertions.assertTrue(tagIdsResult.getData().isEmpty());
    }
}
