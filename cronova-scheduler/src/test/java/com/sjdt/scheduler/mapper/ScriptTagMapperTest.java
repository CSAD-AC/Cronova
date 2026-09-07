package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.ScriptTag;
import com.sjdt.scheduler.domain.po.Tag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

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
    void testInsert() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptMapper.insert(script);

        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .build();
        tagMapper.insert(tag);

        ScriptTag scriptTag = ScriptTag.builder()
                .scriptId(script.getId())
                .tagId(tag.getId())
                .createTime(LocalDateTime.now())
                .build();

        int result = scriptTagMapper.insert(scriptTag);

        Assertions.assertEquals(1, result);
    }

    @Test
    void testSelectTagIdsByScriptId() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptMapper.insert(script);

        Tag tag1 = Tag.builder()
                .name("测试标签1-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签1描述")
                .build();
        tagMapper.insert(tag1);

        Tag tag2 = Tag.builder()
                .name("测试标签2-" + UUID.randomUUID().toString().substring(0, 8))
                .description("标签2描述")
                .build();
        tagMapper.insert(tag2);

        ScriptTag scriptTag1 = ScriptTag.builder()
                .scriptId(script.getId())
                .tagId(tag1.getId())
                .createTime(LocalDateTime.now())
                .build();
        scriptTagMapper.insert(scriptTag1);

        ScriptTag scriptTag2 = ScriptTag.builder()
                .scriptId(script.getId())
                .tagId(tag2.getId())
                .createTime(LocalDateTime.now())
                .build();
        scriptTagMapper.insert(scriptTag2);

        List<Integer> tagIds = scriptTagMapper.selectTagIdsByScriptId(script.getId());

        Assertions.assertEquals(2, tagIds.size());
        Assertions.assertTrue(tagIds.contains(tag1.getId()));
        Assertions.assertTrue(tagIds.contains(tag2.getId()));
    }

    @Test
    void testSelectScriptIdsByTagId() {
        Script script1 = Script.builder()
                .name("测试脚本1-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test1-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptMapper.insert(script1);

        Script script2 = Script.builder()
                .name("测试脚本2-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test2-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptMapper.insert(script2);

        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .build();
        tagMapper.insert(tag);

        ScriptTag scriptTag1 = ScriptTag.builder()
                .scriptId(script1.getId())
                .tagId(tag.getId())
                .createTime(LocalDateTime.now())
                .build();
        scriptTagMapper.insert(scriptTag1);

        ScriptTag scriptTag2 = ScriptTag.builder()
                .scriptId(script2.getId())
                .tagId(tag.getId())
                .createTime(LocalDateTime.now())
                .build();
        scriptTagMapper.insert(scriptTag2);

        List<String> scriptIds = scriptTagMapper.selectScriptIdsByTagId(tag.getId());

        Assertions.assertEquals(2, scriptIds.size());
        Assertions.assertTrue(scriptIds.contains(script1.getId()));
        Assertions.assertTrue(scriptIds.contains(script2.getId()));
    }

    @Test
    void testDelete() {
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();
        scriptMapper.insert(script);

        Tag tag = Tag.builder()
                .name("测试标签-" + UUID.randomUUID().toString().substring(0, 8))
                .description("测试标签描述")
                .build();
        tagMapper.insert(tag);

        ScriptTag scriptTag = ScriptTag.builder()
                .scriptId(script.getId())
                .tagId(tag.getId())
                .createTime(LocalDateTime.now())
                .build();
        scriptTagMapper.insert(scriptTag);

        LambdaQueryWrapper<ScriptTag> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ScriptTag::getScriptId, script.getId())
                .eq(ScriptTag::getTagId, tag.getId());
        int result = scriptTagMapper.delete(queryWrapper);

        Assertions.assertEquals(1, result);
    }
}
