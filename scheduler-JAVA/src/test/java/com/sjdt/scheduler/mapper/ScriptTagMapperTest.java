package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.ScriptTag;
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
 * ScriptTagMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class ScriptTagMapperTest {

    @Autowired
    private ScriptTagMapper scriptTagMapper;

    @Autowired
    private ScriptMapper scriptMapper;

    @Autowired
    private TagMapper tagMapper;

    @Test
    void testAddTagToScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);

        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);

        // 为脚本添加标签
        int result = scriptTagMapper.addTagToScript(script.getId(), tag.getId());

        // 验证添加结果
        Assertions.assertEquals(1, result);

        // 验证关联关系是否创建
        ScriptTag scriptTag = scriptTagMapper.selectOne(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getScriptId, script.getId())
                        .eq(ScriptTag::getTagId, tag.getId())
        );
        Assertions.assertNotNull(scriptTag);
    }

    @Test
    void testBatchAddTagsToScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);

        // 创建测试标签
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag1);

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag2);

        // 批量添加标签
        List<Integer> tagIds = List.of(tag1.getId(), tag2.getId());
        int result = scriptTagMapper.batchAddTagsToScript(script.getId(), tagIds);

        // 验证添加结果
        Assertions.assertEquals(2, result);

        // 验证关联关系是否创建
        List<Integer> scriptTagCount = scriptTagMapper.selectObjs(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getScriptId, script.getId())
        ).stream().map(obj -> (Integer) obj).toList();
        Assertions.assertEquals(2, scriptTagCount.size());
    }

    @Test
    void testSelectTagIdsByScriptId() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);

        // 创建测试标签
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag1);

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag2);

        // 为脚本添加标签
        scriptTagMapper.addTagToScript(script.getId(), tag1.getId());
        scriptTagMapper.addTagToScript(script.getId(), tag2.getId());

        // 根据脚本ID查询标签ID列表
        List<Integer> tagIds = scriptTagMapper.selectTagIdsByScriptId(script.getId());

        // 验证查询结果
        Assertions.assertNotNull(tagIds);
        Assertions.assertEquals(2, tagIds.size());
        Assertions.assertTrue(tagIds.contains(tag1.getId()));
        Assertions.assertTrue(tagIds.contains(tag2.getId()));
    }

    @Test
    void testRemoveTagFromScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);

        // 创建测试标签
        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag);

        // 为脚本添加标签
        scriptTagMapper.addTagToScript(script.getId(), tag.getId());

        // 移除标签
        int result = scriptTagMapper.removeTagFromScript(script.getId(), tag.getId());

        // 验证移除结果
        Assertions.assertEquals(1, result);

        // 验证关联关系是否移除
        ScriptTag scriptTag = scriptTagMapper.selectOne(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getScriptId, script.getId())
                        .eq(ScriptTag::getTagId, tag.getId())
        );
        Assertions.assertNull(scriptTag);
    }

    @Test
    void testClearTagsFromScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptMapper.insert(script);

        // 创建测试标签
        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("1.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag1);

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .operator("==")
                .version("2.0")
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(tag2);

        // 为脚本添加标签
        scriptTagMapper.addTagToScript(script.getId(), tag1.getId());
        scriptTagMapper.addTagToScript(script.getId(), tag2.getId());

        // 清空标签
        int result = scriptTagMapper.clearTagsFromScript(script.getId());

        // 验证清空结果
        Assertions.assertEquals(2, result);

        // 验证所有关联关系是否移除
        List<ScriptTag> scriptTags = scriptTagMapper.selectList(
                new LambdaQueryWrapper<ScriptTag>()
                        .eq(ScriptTag::getScriptId, script.getId())
        );
        Assertions.assertTrue(scriptTags.isEmpty());
    }
}
